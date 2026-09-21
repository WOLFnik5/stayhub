package com.bookingapp.exception;

public class PaymentProviderGatewayException extends PaymentProviderException {

    public PaymentProviderGatewayException(
            String category,
            Integer providerStatusCode,
            String providerRequestId
    ) {
        super("Payment provider request failed", category, providerStatusCode, providerRequestId);
    }
}
