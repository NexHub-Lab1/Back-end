package com.nexhub.backend.service.payment;

public record ProviderPaymentIntent(
        String providerPaymentId,
        String checkoutUrl
) {
}
