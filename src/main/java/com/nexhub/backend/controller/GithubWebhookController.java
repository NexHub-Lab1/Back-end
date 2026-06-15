package com.nexhub.backend.controller;

import com.nexhub.backend.service.GithubWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github/webhooks")
@RequiredArgsConstructor
public class GithubWebhookController {
    private final GithubWebhookService githubWebhookService;

    @PostMapping
    public ResponseEntity<Void> receiveGithubWebhook(
            @RequestHeader(name = "X-GitHub-Event") String event,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(name = "X-Hub-Signature-256") String signatureHeader,
            @RequestBody String payload
    ) {
        try {
            githubWebhookService.processWebhook(event, deliveryId, signatureHeader, payload);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
