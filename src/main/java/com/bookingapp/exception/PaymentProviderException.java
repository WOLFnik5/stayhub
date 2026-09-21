package com.bookingapp.exception;

public abstract class PaymentProviderException extends DomainException {
    private final String category;
    private final Integer providerStatusCode;
    private final String providerRequestId;

    protected PaymentProviderException(
            String message,
            String category,
            Integer providerStatusCode,
            String providerRequestId
    ) {
        super(message);
        this.category = category;
        this.providerStatusCode = providerStatusCode;
        this.providerRequestId = providerRequestId;
    }

    public String getCategory() {
        return category;
    }

    public Integer getProviderStatusCode() {
        return providerStatusCode;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }
}
