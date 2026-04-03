package com.bookingapp.service;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentFilterQuery;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.exception.ForbiddenOperationException;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.infrastructure.stripe.StripePaymentProvider;
import com.bookingapp.web.dto.PaymentCancelResult;
import com.bookingapp.web.dto.PaymentSessionResult;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AccommodationRepository accommodationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final StripePaymentProvider stripePaymentProvider;
    private final KafkaEventPublisher kafkaEventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            AccommodationRepository accommodationRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            StripePaymentProvider stripePaymentProvider,
            KafkaEventPublisher kafkaEventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.accommodationRepository = accommodationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.stripePaymentProvider = stripePaymentProvider;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    @Transactional
    public PaymentSessionResult createPaymentSession(Long bookingId) {
        Booking booking = getBooking(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        Accommodation accommodation = getAccommodation(booking.getAccommodationId());
        User bookingOwner = getUser(booking.getUserId());

        BigDecimal totalAmount = calculateTotalAmount(booking, accommodation);
        Payment pendingPayment = preparePaymentForCheckout(booking.getId(), totalAmount);
        PaymentSessionResult providerSession = stripePaymentProvider.createPaymentSession(
                pendingPayment,
                booking,
                accommodation,
                bookingOwner
        );

        Payment paymentToSave = attachSession(
                pendingPayment,
                providerSession.sessionId(),
                providerSession.sessionUrl()
        );
        Payment savedPayment = paymentRepository.save(paymentToSave);

        return new PaymentSessionResult(
                savedPayment.getSessionId(),
                savedPayment.getSessionUrl(),
                savedPayment.getId(),
                savedPayment.getStatus().name(),
                savedPayment.getBookingId(),
                savedPayment.getAmountToPay()
        );
    }

    public List<Payment> getPayments(PaymentFilterQuery query) {
        CurrentUser currentUser = currentUserService.getCurrentUser();

        if (currentUser.role() == UserRole.ADMIN) {
            PaymentFilterQuery effectiveQuery = query == null ? new PaymentFilterQuery(
                    null) : query;
            return paymentRepository.findAllByFilter(effectiveQuery);
        }

        return paymentRepository.findAllByFilter(new PaymentFilterQuery(currentUser.id()));
    }

    @Transactional
    public Payment handlePaymentSuccess(String sessionId) {
        Payment payment = getPaymentBySessionId(sessionId);

        if (!stripePaymentProvider.isPaymentSuccessful(sessionId)) {
            throw new PaymentStateException("Payment session '"
                    + sessionId
                    + "' is not confirmed as successful");
        }

        Payment savedPayment = paymentRepository.save(markPaid(payment));
        kafkaEventPublisher.publishPaymentSucceeded(savedPayment);
        return savedPayment;
    }

    @Transactional
    public PaymentCancelResult handlePaymentCancel(String sessionId) {
        return handlePaymentCancel(sessionId, null);
    }

    @Transactional
    public PaymentCancelResult handlePaymentCancel(String sessionId, Long bookingId) {
        Payment payment = resolvePaymentForCancel(sessionId, bookingId);
        String resolvedSessionId = payment.getSessionId();

        if (stripePaymentProvider.isPaymentSessionActive(resolvedSessionId)) {
            return new PaymentCancelResult(
                    payment.getId(),
                    payment.getSessionId(),
                    payment.getSessionUrl(),
                    payment.getStatus(),
                    true,
                    "Payment was canceled on the provider page. "
                            + "You can pay later using the same session for a limited time."
            );
        }

        Payment expiredPayment = expirePayment(payment);
        Payment savedPayment = paymentRepository.save(expiredPayment);

        return new PaymentCancelResult(
                savedPayment.getId(),
                savedPayment.getSessionId(),
                savedPayment.getSessionUrl(),
                savedPayment.getStatus(),
                false,
                "Payment session is no longer active. "
                        + "Create a new checkout session if you want to pay later."
        );
    }

    private BigDecimal calculateTotalAmount(Booking booking, Accommodation accommodation) {
        long bookedDays = ChronoUnit.DAYS.between(booking.getCheckInDate(),
                booking.getCheckOutDate());
        if (bookedDays <= 0) {
            throw new BusinessValidationException("Booking must contain at least one payable day");
        }
        return accommodation.getDailyRate().multiply(BigDecimal.valueOf(bookedDays));
    }

    private Payment preparePaymentForCheckout(Long bookingId, BigDecimal totalAmount) {
        Payment existingPayment = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (existingPayment == null) {
            return new Payment(
                    null,
                    PaymentStatus.PENDING,
                    bookingId,
                    null,
                    null,
                    validateAmount(totalAmount)
            );
        }

        if (existingPayment.getStatus() == PaymentStatus.PAID) {
            throw new PaymentStateException("Payment for booking id '"
                    + bookingId
                    + "' has already been completed");
        }

        return new Payment(
                existingPayment.getId(),
                PaymentStatus.PENDING,
                bookingId,
                null,
                null,
                totalAmount
        );
    }

    private Payment attachSession(Payment payment, String sessionId, String sessionUrl) {
        payment.setSessionId(requireNonBlank(sessionId, "Payment session id must not be blank"));
        payment.setSessionUrl(requireNonBlank(sessionUrl, "Payment session URL must not be blank"));
        return payment;
    }

    private Payment markPaid(Payment payment) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.EXPIRED) {
            throw new PaymentStateException("Expired payment cannot be marked as paid");
        }
        payment.setStatus(PaymentStatus.PAID);
        return payment;
    }

    private Payment expirePayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.EXPIRED) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new PaymentStateException("Paid payment cannot be expired");
        }
        payment.setStatus(PaymentStatus.EXPIRED);
        return payment;
    }

    private BigDecimal validateAmount(BigDecimal amountToPay) {
        if (amountToPay == null) {
            throw new BusinessValidationException("Payment amount must not be null");
        }
        if (amountToPay.signum() < 0) {
            throw new BusinessValidationException("Payment amount must not be negative");
        }
        return amountToPay;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }

    private void ensureCurrentUserCanAccessBooking(Booking booking) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (!currentUser.id().equals(booking.getUserId())) {
            throw new ForbiddenOperationException("Access denied for booking id '"
                    + booking.getId()
                    + "'");
        }
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException("Booking with id '"
                        + bookingId
                        + "' was not found"));
    }

    private Accommodation getAccommodation(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + accommodationId + "' was not found"));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '"
                        + userId
                        + "' was not found"));
    }

    private Payment getPaymentBySessionId(String sessionId) {
        return paymentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Payment with session id '" + sessionId + "' was not found"));
    }

    private Payment resolvePaymentForCancel(String sessionId, Long bookingId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return getPaymentBySessionId(sessionId);
        }

        if (bookingId == null) {
            throw new BusinessValidationException(
                    "Either session_id or booking_id must be provided"
            );
        }

        return paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Payment for booking id '" + bookingId + "' was not found"
                ));
    }
}
