package com.nexhub.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class GithubWebhookVerifier {
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${github.webhook-secret:}")
    private String webhookSecret;

    public void validate(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("GitHub webhook secret is not configured");
        }
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException("GitHub webhook signature data is incomplete");
        }
        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            throw new IllegalArgumentException("GitHub webhook signature is invalid");
        }

        String calculatedSignature = SIGNATURE_PREFIX + sign(payload);
        if (!MessageDigest.isEqual(
                calculatedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("GitHub webhook signature is invalid");
        }
    }

    String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC SHA-256 is not available", e);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("GitHub webhook secret is invalid", e);
        }
    }
}
