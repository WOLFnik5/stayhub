package com.bookingapp.domain.service;

import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.exception.ForbiddenOperationException;
import com.bookingapp.domain.exception.PaymentStateException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.repository.PaymentRepository;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.domain.service.dto.CurrentUser;
import com.bookingapp.domain.service.dto.PaymentCancelResult;
import com.bookingapp.domain.service.dto.PaymentFilterQuery;
import com.bookingapp.domain.service.dto.PaymentSessionResult;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.infrastructure.stripe.StripePaymentProvider;
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

        Payment paymentToSave = pendingPayment.attachSession(providerSession.sessionId(),
                providerSession.sessionUrl());
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

        Payment savedPayment = paymentRepository.save(payment.markPaid());
        kafkaEventPublisher.publishPaymentSucceeded(savedPayment);
        return savedPayment;
    }

    @Transactional
    public PaymentCancelResult handlePaymentCancel(String sessionId) {
        Payment payment = getPaymentBySessionId(sessionId);

        if (stripePaymentProvider.isPaymentSessionActive(sessionId)) {
            return new PaymentCancelResult(
                    payment.getId(),
                    payment.getSessionId(),
                    payment.getSessionUrl(),
                    payment.getStatus(),
                    true,
                    "Payment was canceled on the provider page, "
                            + "but the session is still active and can be completed later."
            );
        }

        Payment expiredPayment =
                payment.getStatus() == PaymentStatus.EXPIRED ? payment : payment.expire();
        Payment savedPayment = paymentRepository.save(expiredPayment);

        return new PaymentCancelResult(
                savedPayment.getId(),
                savedPayment.getSessionId(),
                savedPayment.getSessionUrl(),
                savedPayment.getStatus(),
                false,
                "Payment session is no longer active. "
                        + "A new checkout session will be required."
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
            return Payment.createPending(bookingId, totalAmount);
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
}
