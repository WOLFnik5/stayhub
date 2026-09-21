package com.bookingapp.exception;

public class PaymentProviderUnavailableException extends PaymentProviderException {

    public PaymentProviderUnavailableException(
            String category,
            Integer providerStatusCode,
            String providerRequestId
    ) {
        super("Payment provider is temporarily unavailable", category, providerStatusCode,
                providerRequestId);
    }
}
