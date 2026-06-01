package com.nexhub.backend.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class MercadoPagoWebhookVerifier {
    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    public void validate(String dataId, String requestId, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago webhook secret is not configured");
        }
        if (dataId == null || dataId.isBlank() || requestId == null || requestId.isBlank()
                || signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago webhook signature data is incomplete");
        }

        String timestamp = headerPart(signatureHeader, "ts");
        String receivedSignature = headerPart(signatureHeader, "v1");
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        String calculatedSignature = sign(manifest);

        if (!MessageDigest.isEqual(
                calculatedSignature.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("Mercado Pago webhook signature is invalid");
        }
    }

    private String headerPart(String signatureHeader, String name) {
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2 && name.equals(keyValue[0].trim()) && !keyValue[1].isBlank()) {
                return keyValue[1].trim();
            }
        }
        throw new IllegalArgumentException("Mercado Pago webhook signature is incomplete");
    }

    private String sign(String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC SHA-256 is not available", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("Mercado Pago webhook secret is invalid", e);
        }
    }
}
