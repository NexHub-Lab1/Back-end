package com.nexhub.backend.service.payment;

import com.nexhub.backend.model.Payment;

public interface PaymentGateway {
    ProviderPaymentIntent createTaskPaymentIntent(Payment payment);
}
