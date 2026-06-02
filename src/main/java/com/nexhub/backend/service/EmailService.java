package com.nexhub.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendNotificationEmail(String to, String subject, String body) {
        try {
            // Strip any surrounding single or double quotes injected by environment variable parsers
            String cleanApiKey = apiKey != null ? apiKey.replaceAll("^['\"]|['\"]$", "") : "";
            String cleanFromEmail = fromEmail != null ? fromEmail.replaceAll("^['\"]|['\"]$", "") : "onboarding@resend.dev";

            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(cleanApiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", cleanFromEmail);
            payload.put("to", to);
            payload.put("subject", subject);
            payload.put("html", "<strong>" + body + "</strong>");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, entity, String.class);
            log.info("Email sent successfully via Resend to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via Resend to {}: {}", to, e.getMessage());
        }
    }
}
