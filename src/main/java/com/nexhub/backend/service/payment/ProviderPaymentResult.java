package com.nexhub.backend.service.payment;

import java.math.BigDecimal;

public record ProviderPaymentResult(
        String providerPaymentId,
        String externalReference,
        String status,
        String statusDetail,
        BigDecimal amount,
        String currency
) {
}
