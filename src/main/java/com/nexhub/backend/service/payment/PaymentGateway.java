package com.nexhub.backend.service.payment;

import com.nexhub.backend.model.Payment;

import java.util.Optional;

public interface PaymentGateway {
    ProviderPaymentIntent createFundingIntent(Payment payment);

    ProviderPaymentResult getPayment(String providerPaymentId);

    Optional<ProviderPaymentResult> findPaymentByExternalReference(String externalReference);
}
