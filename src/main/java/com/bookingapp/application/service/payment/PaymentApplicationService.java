package com.bookingapp.application.service.payment;

import com.bookingapp.application.model.CreatePaymentSessionCommand;
import com.bookingapp.application.model.CurrentUser;
import com.bookingapp.application.model.PaymentCancelResult;
import com.bookingapp.application.model.PaymentFilterQuery;
import com.bookingapp.application.model.PaymentSession;
import com.bookingapp.application.port.in.payment.CreatePaymentSessionUseCase;
import com.bookingapp.application.port.in.payment.GetPaymentsUseCase;
import com.bookingapp.application.port.in.payment.HandlePaymentCancelUseCase;
import com.bookingapp.application.port.in.payment.HandlePaymentSuccessUseCase;
import com.bookingapp.application.port.out.integration.EventPublisherPort;
import com.bookingapp.application.port.out.integration.NotificationPort;
import com.bookingapp.application.port.out.integration.PaymentProviderPort;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.application.port.out.persistence.BookingRepositoryPort;
import com.bookingapp.application.port.out.persistence.PaymentRepositoryPort;
import com.bookingapp.application.port.out.persistence.UserRepositoryPort;
import com.bookingapp.application.port.out.security.CurrentUserProviderPort;
import com.bookingapp.common.exception.ForbiddenOperationException;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.exception.PaymentStateException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PaymentApplicationService implements
        CreatePaymentSessionUseCase,
        GetPaymentsUseCase,
        HandlePaymentSuccessUseCase,
        HandlePaymentCancelUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final AccommodationRepositoryPort accommodationRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final CurrentUserProviderPort currentUserProviderPort;
    private final PaymentProviderPort paymentProviderPort;
    private final EventPublisherPort eventPublisherPort;
    private final NotificationPort notificationPort;

    public PaymentApplicationService(
            PaymentRepositoryPort paymentRepositoryPort,
            BookingRepositoryPort bookingRepositoryPort,
            AccommodationRepositoryPort accommodationRepositoryPort,
            UserRepositoryPort userRepositoryPort,
            CurrentUserProviderPort currentUserProviderPort,
            PaymentProviderPort paymentProviderPort,
            EventPublisherPort eventPublisherPort,
            NotificationPort notificationPort
    ) {
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.accommodationRepositoryPort = accommodationRepositoryPort;
        this.userRepositoryPort = userRepositoryPort;
        this.currentUserProviderPort = currentUserProviderPort;
        this.paymentProviderPort = paymentProviderPort;
        this.eventPublisherPort = eventPublisherPort;
        this.notificationPort = notificationPort;
    }

    @Override
    @Transactional
    public PaymentSession createPaymentSession(CreatePaymentSessionCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Create payment session command must not be null");
        }

        Booking booking = getBooking(command.bookingId());
        ensureCurrentUserCanAccessBooking(booking);
        Accommodation accommodation = getAccommodation(booking.getAccommodationId());
        User bookingOwner = getUser(booking.getUserId());

        BigDecimal totalAmount = calculateTotalAmount(booking, accommodation);
        Payment pendingPayment = Payment.createPending(booking.getId(), totalAmount);
        PaymentSession providerSession = paymentProviderPort.createPaymentSession(
                pendingPayment,
                booking,
                accommodation,
                bookingOwner
        );

        Payment paymentToSave = pendingPayment.attachSession(providerSession.sessionId(), providerSession.sessionUrl());
        Payment savedPayment = paymentRepositoryPort.save(paymentToSave);

        return new PaymentSession(
                savedPayment.getSessionId(),
                savedPayment.getSessionUrl(),
                savedPayment.getId(),
                savedPayment.getStatus().name()
        );
    }

    @Override
    public List<Payment> getPayments(PaymentFilterQuery query) {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();

        if (currentUser.role() == UserRole.ADMIN) {
            PaymentFilterQuery effectiveQuery = query == null ? new PaymentFilterQuery(null) : query;
            return paymentRepositoryPort.findAllByFilter(effectiveQuery);
        }

        return paymentRepositoryPort.findAllByFilter(new PaymentFilterQuery(currentUser.id()));
    }

    @Override
    @Transactional
    public Payment handlePaymentSuccess(String sessionId) {
        Payment payment = getPaymentBySessionId(sessionId);

        if (!paymentProviderPort.isPaymentSuccessful(sessionId)) {
            throw new PaymentStateException("Payment session '" + sessionId + "' is not confirmed as successful");
        }

        Payment savedPayment = paymentRepositoryPort.save(payment.markPaid());
        eventPublisherPort.publishPaymentSucceeded(savedPayment);
        notificationPort.notifyPaymentSuccessful(savedPayment);
        return savedPayment;
    }

    @Override
    @Transactional
    public PaymentCancelResult handlePaymentCancel(String sessionId) {
        Payment payment = getPaymentBySessionId(sessionId);

        if (paymentProviderPort.isPaymentSessionActive(sessionId)) {
            return new PaymentCancelResult(
                    payment.getId(),
                    payment.getSessionId(),
                    payment.getSessionUrl(),
                    payment.getStatus(),
                    true,
                    "Payment was canceled on the provider page, but the session is still active and can be completed later."
            );
        }

        Payment expiredPayment = payment.getStatus() == PaymentStatus.EXPIRED ? payment : payment.expire();
        Payment savedPayment = paymentRepositoryPort.save(expiredPayment);

        return new PaymentCancelResult(
                savedPayment.getId(),
                savedPayment.getSessionId(),
                savedPayment.getSessionUrl(),
                savedPayment.getStatus(),
                false,
                "Payment session is no longer active. A new checkout session will be required."
        );
    }

    private BigDecimal calculateTotalAmount(Booking booking, Accommodation accommodation) {
        long bookedDays = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        if (bookedDays <= 0) {
            throw new BusinessValidationException("Booking must contain at least one payable day");
        }
        return accommodation.getDailyRate().multiply(BigDecimal.valueOf(bookedDays));
    }

    private void ensureCurrentUserCanAccessBooking(Booking booking) {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }

        if (!currentUser.id().equals(booking.getUserId())) {
            throw new ForbiddenOperationException("Access denied for booking id '" + booking.getId() + "'");
        }
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepositoryPort.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundDomainException("Booking with id '" + bookingId + "' was not found"));
    }

    private Accommodation getAccommodation(Long accommodationId) {
        return accommodationRepositoryPort.findById(accommodationId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Accommodation with id '" + accommodationId + "' was not found"));
    }

    private User getUser(Long userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '" + userId + "' was not found"));
    }

    private Payment getPaymentBySessionId(String sessionId) {
        return paymentRepositoryPort.findBySessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundDomainException(
                        "Payment with session id '" + sessionId + "' was not found"));
    }
}
