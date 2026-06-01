package com.nexhub.backend.service.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoWebhookVerifierTest {
    @Test
    void acceptsWebhookSignedWithConfiguredSecret() {
        MercadoPagoWebhookVerifier verifier = verifierWithSecret("test-secret");
        String manifest = "id:1234;request-id:req-1;ts:1704908010;";
        String signature = "ts=1704908010,v1=" + sign(manifest, "test-secret");

        verifier.validate("1234", "req-1", signature);
    }

    @Test
    void rejectsWebhookWithInvalidSignature() {
        MercadoPagoWebhookVerifier verifier = verifierWithSecret("test-secret");

        assertThatThrownBy(() -> verifier.validate("1234", "req-1", "ts=1704908010,v1=invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mercado Pago webhook signature is invalid");
    }

    private static MercadoPagoWebhookVerifier verifierWithSecret(String secret) {
        MercadoPagoWebhookVerifier verifier = new MercadoPagoWebhookVerifier();
        try {
            var field = MercadoPagoWebhookVerifier.class.getDeclaredField("webhookSecret");
            field.setAccessible(true);
            field.set(verifier, secret);
            return verifier;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String sign(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
