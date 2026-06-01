package com.nexhub.backend.service.payment;

import com.nexhub.backend.model.Payment;

public interface PaymentGateway {
    ProviderPaymentIntent createFundingIntent(Payment payment);

    ProviderPaymentResult getPayment(String providerPaymentId);
}
