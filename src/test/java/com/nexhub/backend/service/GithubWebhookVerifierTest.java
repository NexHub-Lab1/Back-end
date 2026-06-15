package com.nexhub.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubWebhookVerifierTest {
    private GithubWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GithubWebhookVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", "test-secret");
    }

    @Test
    void acceptsValidGithubSignature() {
        String payload = "{\"zen\":\"Keep it logically awesome.\"}";
        String signature = "sha256=" + verifier.sign(payload);

        verifier.validate(payload, signature);
    }

    @Test
    void rejectsInvalidGithubSignature() {
        assertThatThrownBy(() -> verifier.validate("{\"zen\":\"Keep it logically awesome.\"}", "sha256=bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GitHub webhook signature is invalid");
    }
}
