package com.bookingapp.infrastructure.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.service.BookingClosureService;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingClosureRecoverySchedulerTest {
    @Test
    void failedClosureShouldNotStarveLaterBookingsAndCursorShouldWrap() {
        BookingClosureService service = mock(BookingClosureService.class);
        Booking first = new Booking();
        first.setId(1L);
        first.setStatus(BookingStatus.CANCELING);
        Booking second = new Booking();
        second.setId(2L);
        second.setStatus(BookingStatus.EXPIRING);
        when(service.pendingClosures(0)).thenReturn(List.of(first));
        when(service.pendingClosures(1)).thenReturn(List.of(second));
        when(service.pendingClosures(2)).thenReturn(List.of());
        doThrow(new IllegalStateException("Provider unavailable")).when(service).resume(first);
        BookingClosureRecoveryScheduler scheduler = new BookingClosureRecoveryScheduler(service);
        scheduler.resumeClosures();
        scheduler.resumeClosures();
        verify(service).resume(second);
        scheduler.resumeClosures();
        verify(service, times(2)).resume(first);
    }
}
