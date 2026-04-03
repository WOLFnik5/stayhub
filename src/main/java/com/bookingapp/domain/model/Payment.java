package com.bookingapp.domain.model;

import com.bookingapp.domain.model.enums.PaymentStatus;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private Long id;
    private PaymentStatus status;
    private Long bookingId;
    private String sessionUrl;
    private String sessionId;
    private BigDecimal amountToPay;
}
