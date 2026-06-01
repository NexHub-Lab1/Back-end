package com.nexhub.backend.service.payment;

public record ProviderPaymentIntent(
        String providerPreferenceId,
        String checkoutUrl
) {
}
