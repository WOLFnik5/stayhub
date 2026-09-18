package com.bookingapp.service;

import static com.bookingapp.service.validation.TextValidationUtils.requireNonBlank;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.PageResult;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.exception.ForbiddenOperationException;
import com.bookingapp.exception.PaymentStateException;
import com.bookingapp.infrastructure.config.StripeProperties;
import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.infrastructure.stripe.StripePaymentProvider;
import com.bookingapp.persistence.AccommodationRepositoryImpl;
import com.bookingapp.persistence.BookingRepositoryImpl;
import com.bookingapp.persistence.PaymentFilterQuery;
import com.bookingapp.persistence.PaymentRepositoryImpl;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.web.dto.PaymentCancelResult;
import com.bookingapp.web.dto.PaymentSessionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepositoryImpl paymentRepository;
    private final BookingRepositoryImpl bookingRepository;
    private final AccommodationRepositoryImpl accommodationRepository;
    private final UserRepositoryImpl userRepository;
    private final CurrentUserService currentUserService;
    private final StripePaymentProvider stripePaymentProvider;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final StripeProperties stripeProperties;

    public PaymentService(
            PaymentRepositoryImpl paymentRepository,
            BookingRepositoryImpl bookingRepository,
            AccommodationRepositoryImpl accommodationRepository,
            UserRepositoryImpl userRepository,
            CurrentUserService currentUserService,
            StripePaymentProvider stripePaymentProvider,
            KafkaEventPublisher kafkaEventPublisher,
            TransactionTemplate transactionTemplate,
            StripeProperties stripeProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.accommodationRepository = accommodationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.stripePaymentProvider = stripePaymentProvider;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.stripeProperties = stripeProperties;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentSessionResult createPaymentSession(Long bookingId) {
        // Commit the attempt before calling Stripe so retries reuse the same idempotency key.
        Payment attempt = transactionTemplate.execute(status -> prepareCheckout(bookingId));
        if (attempt.getSessionId() != null) {
            return resolveExistingCheckout(bookingId, attempt);
        }

        return createAndAttachCheckout(bookingId, attempt);
    }

    private Payment prepareCheckout(Long bookingId) {
        Booking booking = lockBooking(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        ensureBookingPayable(booking);
        if (paymentRepository.findAllByBookingId(bookingId).stream()
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.PAID)) {
            throw new PaymentStateException("Payment has already been completed");
        }
        Payment previous = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (previous != null && previous.getStatus() == PaymentStatus.PAID) {
            return previous;
        }
        if (previous != null && previous.getStatus() == PaymentStatus.PENDING) {
            return previous;
        }
        BigDecimal amount = previous == null
                ? calculateTotalAmount(booking, getAccommodation(booking.getAccommodationId()))
                : previous.getAmountToPay();
        Payment next = new Payment(null, PaymentStatus.PENDING, bookingId, null, null,
                validateAmount(amount));
        next.setCurrency(previous != null && previous.getCurrency() != null
                ? previous.getCurrency() : stripeProperties.getCurrency());
        return paymentRepository.save(next);
    }

    private PaymentSessionResult resolveExistingCheckout(Long bookingId, Payment payment) {
        String sessionId = payment.getSessionId();
        if (stripePaymentProvider.isPaymentSuccessful(sessionId)) {
            stripePaymentProvider.validatePayment(payment);
            return transactionTemplate.execute(status -> sessionResult(
                    markVerifiedPaymentPaid(bookingId, payment.getId(), sessionId)));
        }
        if (!stripePaymentProvider.isPaymentSessionExpired(sessionId)) {
            return sessionResult(payment);
        }

        Payment nextAttempt = transactionTemplate.execute(status ->
                replaceExpiredCheckout(bookingId, payment.getId(), sessionId));
        if (nextAttempt.getSessionId() != null) {
            return sessionResult(nextAttempt);
        }
        return createAndAttachCheckout(bookingId, nextAttempt);
    }

    private PaymentSessionResult createAndAttachCheckout(Long bookingId, Payment payment) {
        if (payment.getCreatedAt() == null
                || payment.getCreatedAt().isBefore(Instant.now().minus(23, ChronoUnit.HOURS))) {
            throw new PaymentStateException(
                    "Unresolved checkout attempt requires Stripe reconciliation before retrying");
        }
        Booking booking = getBooking(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        ensureBookingPayable(booking);
        PaymentSessionResult session = stripePaymentProvider.createPaymentSession(payment, booking,
                getAccommodation(booking.getAccommodationId()), getUser(booking.getUserId()));
        return transactionTemplate.execute(status -> sessionResult(
                attachSessionAfterRemoteCall(bookingId, payment.getId(), session)));
    }

    private Payment replaceExpiredCheckout(Long bookingId, Long paymentId, String sessionId) {
        Booking booking = lockBooking(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        ensureBookingPayable(booking);
        Payment payment = paymentRepository.refresh(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING
                || !sessionId.equals(payment.getSessionId())) {
            return payment;
        }
        paymentRepository.save(expirePayment(payment));
        paymentRepository.flush();
        Payment next = new Payment(null, PaymentStatus.PENDING, bookingId, null, null,
                validateAmount(payment.getAmountToPay()));
        next.setCurrency(payment.getCurrency());
        return paymentRepository.save(next);
    }

    private Payment attachSessionAfterRemoteCall(
            Long bookingId,
            Long paymentId,
            PaymentSessionResult session
    ) {
        Booking booking = lockBooking(bookingId);
        ensureCurrentUserCanAccessBooking(booking);
        ensureBookingPayable(booking);
        Payment payment = paymentRepository.refresh(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getSessionId() != null) {
            return payment;
        }
        return paymentRepository.save(
                attachSession(payment, session.sessionId(), session.sessionUrl()));
    }

    private Payment markVerifiedPaymentPaid(Long bookingId, Long paymentId, String sessionId) {
        lockBooking(bookingId);
        Payment payment = paymentRepository.refresh(paymentId);
        if (payment.getSessionId() != null && !payment.getSessionId().equals(sessionId)) {
            throw new PaymentStateException("Stripe session does not match this attempt");
        }
        payment.setSessionId(sessionId);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }
        Payment savedPayment = paymentRepository.save(markPaid(payment));
        kafkaEventPublisher.publishPaymentSucceeded(savedPayment);
        return savedPayment;
    }

    private Payment attachOrRecoverSession(Payment payment, Booking booking) {
        if (payment.getSessionId() != null) {
            return payment;
        }
        if (payment.getCreatedAt() == null
                || payment.getCreatedAt().isBefore(Instant.now().minus(23, ChronoUnit.HOURS))) {
            throw new PaymentStateException(
                    "Unresolved checkout attempt requires Stripe reconciliation before retrying");
        }
        PaymentSessionResult session = stripePaymentProvider.createPaymentSession(payment, booking,
                getAccommodation(booking.getAccommodationId()), getUser(booking.getUserId()));
        return paymentRepository.save(
                attachSession(payment, session.sessionId(), session.sessionUrl()));
    }

    private PaymentSessionResult sessionResult(Payment savedPayment) {
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

    public PageResult<Payment> getPaymentsPage(PaymentFilterQuery query, int page, int size) {
        validatePagination(page, size);
        CurrentUser currentUser = currentUserService.getCurrentUser();
        PaymentFilterQuery effectiveQuery = currentUser.role() == UserRole.ADMIN
                ? query == null ? new PaymentFilterQuery(null) : query
                : new PaymentFilterQuery(currentUser.id());
        return paymentRepository.findPageByFilter(effectiveQuery, page, size);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Payment handlePaymentSuccess(String sessionId) {
        return completeVerifiedPayment(getPaymentBySessionId(sessionId), sessionId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Payment handleWebhookSuccess(String sessionId, Long paymentId) {
        Payment payment = paymentRepository.findBySessionId(sessionId).orElseGet(() -> {
            if (paymentId == null) {
                throw new EntityNotFoundDomainException("Stripe payment is not recorded yet");
            }
            return paymentRepository.findById(paymentId).orElseThrow(() ->
                    new EntityNotFoundDomainException("Stripe payment attempt was not found"));
        });
        return completeVerifiedPayment(payment, sessionId);
    }

    private Payment completeVerifiedPayment(Payment payment, String sessionId) {
        if (payment.getSessionId() != null && !payment.getSessionId().equals(sessionId)) {
            throw new PaymentStateException("Stripe session does not match this attempt");
        }
        // Validation performs the remote read before the short state-transition transaction.
        payment.setSessionId(sessionId);
        if (!stripePaymentProvider.isPaymentSuccessful(sessionId)) {
            throw new PaymentStateException("Payment session '"
                    + sessionId
                    + "' is not confirmed as successful");
        }
        stripePaymentProvider.validatePayment(payment);
        return transactionTemplate.execute(status ->
                markVerifiedPaymentPaid(payment.getBookingId(), payment.getId(), sessionId));
    }

    private Payment completePayment(Payment payment, String sessionId) {
        lockBooking(payment.getBookingId());
        payment = paymentRepository.refresh(payment.getId());
        if (payment.getSessionId() != null && !payment.getSessionId().equals(sessionId)) {
            throw new PaymentStateException("Stripe session does not match this attempt");
        }
        // Only a verified webhook can reach an attempt whose session ID was not committed yet.
        payment.setSessionId(sessionId);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }
        if (!stripePaymentProvider.isPaymentSuccessful(sessionId)) {
            throw new PaymentStateException("Payment session '"
                    + sessionId
                    + "' is not confirmed as successful");
        }

        return confirmPayment(payment);
    }

    private Payment confirmPayment(Payment payment) {
        stripePaymentProvider.validatePayment(payment);
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
        Booking booking = lockBooking(payment.getBookingId());
        ensureCurrentUserCanAccessBooking(booking);
        payment = paymentRepository.refresh(payment.getId());
        if (bookingId != null && !bookingId.equals(payment.getBookingId())) {
            throw new BusinessValidationException("Payment session does not match booking id");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return new PaymentCancelResult(payment.getId(), payment.getSessionId(),
                    payment.getSessionUrl(), payment.getStatus(), false, "Payment is complete.");
        }
        payment = attachOrRecoverSession(payment, booking);
        String resolvedSessionId = payment.getSessionId();
        if (stripePaymentProvider.isPaymentSuccessful(resolvedSessionId)) {
            Payment paid = confirmPayment(payment);
            return new PaymentCancelResult(paid.getId(), paid.getSessionId(), paid.getSessionUrl(),
                    paid.getStatus(), false, "Payment is complete.");
        }

        if (isBookingPayable(booking)
                && stripePaymentProvider.isPaymentSessionActive(resolvedSessionId)) {
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

        if (!stripePaymentProvider.isPaymentSessionExpired(resolvedSessionId)) {
            return new PaymentCancelResult(payment.getId(), payment.getSessionId(),
                    null, payment.getStatus(), false, "Payment is processing or unavailable.");
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

    private Payment attachSession(Payment payment, String sessionId, String sessionUrl) {
        payment.setSessionId(requireNonBlank(sessionId, "Payment session id must not be blank"));
        payment.setSessionUrl(requireNonBlank(sessionUrl, "Payment session URL must not be blank"));
        return payment;
    }

    private Payment markPaid(Payment payment) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
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

    private Booking lockBooking(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException("Booking with id '"
                        + bookingId
                        + "' was not found"));
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException("Booking with id '"
                        + bookingId
                        + "' was not found"));
    }

    private boolean isBookingPayable(Booking booking) {
        return booking.getStatus() != BookingStatus.CANCELED
                && booking.getStatus() != BookingStatus.EXPIRED
                && booking.getCheckOutDate().isAfter(LocalDate.now());
    }

    private void ensureBookingPayable(Booking booking) {
        if (!isBookingPayable(booking)) {
            throw new PaymentStateException("Canceled or expired booking cannot be paid");
        }
    }

    @Transactional
    public void closeCheckoutForBooking(Booking booking, boolean cancellation) {
        lockBooking(booking.getId());
        for (Payment snapshot : paymentRepository.findAllByBookingId(booking.getId())) {
            Payment payment = paymentRepository.refresh(snapshot.getId());
            if (payment.getStatus() == PaymentStatus.PAID) {
                if (cancellation) {
                    throw new PaymentStateException(
                            "Paid booking requires a refund before cancellation");
                }
                continue;
            }
            if (payment.getStatus() == PaymentStatus.EXPIRED) {
                continue;
            }
            payment = attachOrRecoverSession(payment, booking);
            if (!stripePaymentProvider.expireUnpaidSession(payment.getSessionId())) {
                if (cancellation) {
                    throw new PaymentStateException(
                            "Payment completed; refund before cancellation");
                }
                confirmPayment(payment);
            } else {
                paymentRepository.save(expirePayment(payment));
            }
        }
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

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BusinessValidationException("Page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new BusinessValidationException("Page size must be between 1 and 100");
        }
    }
}
