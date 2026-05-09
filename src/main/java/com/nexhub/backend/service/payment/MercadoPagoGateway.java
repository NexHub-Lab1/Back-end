package com.nexhub.backend.service.payment;

import com.nexhub.backend.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class MercadoPagoGateway implements PaymentGateway {
    @Value("${mercadopago.mock-checkout-base-url:http://localhost:5173/payments/mock}")
    private String mockCheckoutBaseUrl;

    @Override
    public ProviderPaymentIntent createTaskPaymentIntent(Payment payment) {
        String providerPaymentId = "mp_mock_" + UUID.randomUUID();
        String checkoutUrl = mockCheckoutBaseUrl
                + "?externalReference="
                + URLEncoder.encode(payment.getExternalReference(), StandardCharsets.UTF_8);

        return new ProviderPaymentIntent(providerPaymentId, checkoutUrl);
    }
}
