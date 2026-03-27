package com.bookingapp.web.payment;

import com.bookingapp.web.ControllerTestSecurityConfig;
import com.bookingapp.web.controller.PaymentController;
import com.bookingapp.web.mapper.PaymentWebMapper;
import com.bookingapp.domain.service.dto.PaymentCancelResult;
import com.bookingapp.domain.service.dto.PaymentSessionResult;
import com.bookingapp.domain.service.PaymentService;
import com.bookingapp.web.exception.GlobalExceptionHandler;
import com.bookingapp.domain.enums.PaymentStatus;
import com.bookingapp.domain.model.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.bookingapp\\.infrastructure\\.security\\..*"
        )
)
@Import({ControllerTestSecurityConfig.class, GlobalExceptionHandler.class, PaymentWebMapper.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getPaymentsShouldReturnUnauthorizedWhenAnonymous() throws Exception {
        mockMvc.perform(get("/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handlePaymentSuccessShouldBeAccessibleWithoutAuthentication() throws Exception {
        when(paymentService.handlePaymentSuccess("sess_public")).thenReturn(
                new Payment(101L, PaymentStatus.PAID, 12L, "https://checkout.example/sess_public", "sess_public", BigDecimal.valueOf(320))
        );

        mockMvc.perform(get("/payments/success")
                        .param("session_id", "sess_public"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Payment completed successfully."))
                .andExpect(jsonPath("$.payment.sessionId").value("sess_public"))
                .andExpect(jsonPath("$.payment.status").value("PAID"));
    }

    @Test
    void createPaymentShouldReturnPaymentSessionJson() throws Exception {
        when(paymentService.createPaymentSession(anyLong())).thenReturn(
                new PaymentSessionResult(
                        "sess_123",
                        "https://checkout.example/sess_123",
                        100L,
                        PaymentStatus.PENDING.name(),
                        11L,
                        BigDecimal.valueOf(450)
                )
        );

        mockMvc.perform(post("/payments")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": 11
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingId").value(11))
                .andExpect(jsonPath("$.sessionId").value("sess_123"))
                .andExpect(jsonPath("$.sessionUrl").value("https://checkout.example/sess_123"))
                .andExpect(jsonPath("$.amountToPay").value(450));
    }

    @Test
    void getPaymentsShouldReturnListJson() throws Exception {
        when(paymentService.getPayments(any())).thenReturn(List.of(
                new Payment(100L, PaymentStatus.PENDING, 11L, "https://checkout.example/sess_123", "sess_123", BigDecimal.valueOf(450))
        ));

        mockMvc.perform(get("/payments").with(user("customer@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].bookingId").value(11))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].sessionId").value("sess_123"));
    }

    @Test
    void createPaymentShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/payments"));
    }

    @Test
    void handlePaymentSuccessShouldReturnUserFriendlyResponse() throws Exception {
        when(paymentService.handlePaymentSuccess("sess_123")).thenReturn(
                new Payment(100L, PaymentStatus.PAID, 11L, "https://checkout.example/sess_123", "sess_123", BigDecimal.valueOf(450))
        );

        mockMvc.perform(get("/payments/success")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .param("session_id", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Payment completed successfully."))
                .andExpect(jsonPath("$.payment.id").value(100))
                .andExpect(jsonPath("$.payment.status").value("PAID"));
    }

    @Test
    void handlePaymentCancelShouldReturnCancelResponse() throws Exception {
        when(paymentService.handlePaymentCancel("sess_123")).thenReturn(
                new PaymentCancelResult(
                        100L,
                        "sess_123",
                        "https://checkout.example/sess_123",
                        PaymentStatus.PENDING,
                        true,
                        "Payment was canceled on the provider page, but the session is still active and can be completed later."
                )
        );

        mockMvc.perform(get("/payments/cancel")
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .param("session_id", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId").value(100))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.sessionId").value("sess_123"))
                .andExpect(jsonPath("$.canBeCompletedLater").value(true));
    }
}
