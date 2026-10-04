package com.bookingapp.infrastructure.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.model.Payment;
import com.bookingapp.domain.model.enums.PaymentStatus;
import com.bookingapp.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentReconciliationSchedulerTest {
    @Test
    void failureShouldNotBlockLaterAttemptsAndCursorShouldWrap() {
        PaymentService service = mock(PaymentService.class);
        Payment first = new Payment(1L, PaymentStatus.PENDING, 1L,
                null, "sess_first", BigDecimal.TEN);
        Payment second = new Payment(2L, PaymentStatus.PENDING, 2L,
                null, "sess_second", BigDecimal.TEN);
        when(service.reconciliationCandidates(100, 0)).thenReturn(List.of(first));
        when(service.reconciliationCandidates(100, 1)).thenReturn(List.of(second));
        when(service.reconciliationCandidates(100, 2)).thenReturn(List.of());
        doThrow(new IllegalStateException("Provider unavailable"))
                .when(service).reconcileStaleAttempt(first);
        PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(service);
        scheduler.reconcilePayments();
        scheduler.reconcilePayments();
        verify(service).reconcileStaleAttempt(second);
        scheduler.reconcilePayments();
        verify(service, org.mockito.Mockito.times(2)).reconcileStaleAttempt(first);
    }
}
