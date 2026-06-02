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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void sendNotificationEmail(String to, String subject, String body) {
        sendNotificationEmail(to, subject, body, "INFO", null);
    }

    @Async
    public void sendNotificationEmail(String to, String subject, String body, String type, String targetPath) {
        try {
            // Strip any surrounding single or double quotes injected by environment variable parsers
            String cleanApiKey = apiKey != null ? apiKey.replaceAll("^['\"]|['\"]$", "") : "";
            String cleanFromEmail = fromEmail != null ? fromEmail.replaceAll("^['\"]|['\"]$", "") : "onboarding@resend.dev";
            String cleanFrontendUrl = frontendUrl != null ? frontendUrl.replaceAll("^['\"]|['\"]$", "") : "http://localhost:5173";

            // Resolve badge styling based on notification type
            String badgeBg;
            String badgeColor;
            String resolvedType = type != null ? type.toUpperCase() : "INFO";

            if ("SUCCESS".equals(resolvedType)) {
                badgeBg = "#d1fae5";
                badgeColor = "#065f46";
            } else if ("WARNING".equals(resolvedType)) {
                badgeBg = "#fef3c7";
                badgeColor = "#92400e";
            } else {
                badgeBg = "#dbeafe";
                badgeColor = "#1e40af";
            }

            // Resolve action target URL
            String actionUrl = cleanFrontendUrl;
            if (targetPath != null && !targetPath.isBlank()) {
                String path = targetPath.startsWith("/") ? targetPath : "/" + targetPath;
                actionUrl = cleanFrontendUrl + path;
            }
            String profileUrl = cleanFrontendUrl + "/profile";

            // Assemble beautiful premium HTML email template
            String htmlContent = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head>"
                    + "  <meta charset='utf-8'>"
                    + "  <meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                    + "  <title>NexHub Notification</title>"
                    + "</head>"
                    + "<body style=\"margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale;\">"
                    + "  <table border='0' cellpadding='0' cellspacing='0' width='100%' style='background-color: #f8fafc; padding: 40px 20px;'>"
                    + "    <tr>"
                    + "      <td align='center'>"
                    + "        <table border='0' cellpadding='0' cellspacing='0' width='100%' style='max-width: 580px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;'>"
                    + "          <tr>"
                    + "            <td style=\"background: linear-gradient(135deg, #2563eb, #1d4ed8); padding: 32px 40px; text-align: left;\">"
                    + "              <span style=\"font-size: 24px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;\">NexHub</span>"
                    + "            </td>"
                    + "          </tr>"
                    + "          <tr>"
                    + "            <td style='padding: 40px 40px 32px 40px;'>"
                    + "              <table border='0' cellpadding='0' cellspacing='0' style='margin-bottom: 24px;'>"
                    + "                <tr>"
                    + "                  <td style='background-color: " + badgeBg + "; color: " + badgeColor + "; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; padding: 6px 12px; border-radius: 9999px;'>"
                    + "                    " + resolvedType
                    + "                  </td>"
                    + "                </tr>"
                    + "              </table>"
                    + "              <p style=\"font-size: 16px; line-height: 1.6; color: #334155; margin: 0 0 24px 0; font-weight: 500;\">"
                    + "                " + body
                    + "              </p>"
                    + "              <table border='0' cellpadding='0' cellspacing='0' style='margin-top: 32px;'>"
                    + "                <tr>"
                    + "                  <td>"
                    + "                    <a href='" + actionUrl + "' target='_blank' style=\"background-color: #2563eb; color: #ffffff; font-size: 14px; font-weight: 600; text-decoration: none; padding: 12px 24px; border-radius: 12px; display: inline-block; box-shadow: 0 4px 6px rgba(37,99,235,0.2);\">"
                    + "                      View on NexHub"
                    + "                    </a>"
                    + "                  </td>"
                    + "                </tr>"
                    + "              </table>"
                    + "            </td>"
                    + "          </tr>"
                    + "          <tr>"
                    + "            <td style='padding: 0 40px;'>"
                    + "              <div style='border-top: 1px solid #f1f5f9;'></div>"
                    + "            </td>"
                    + "          </tr>"
                    + "          <tr>"
                    + "            <td style='padding: 32px 40px; background-color: #fafafa;'>"
                    + "              <p style=\"font-size: 12px; line-height: 1.5; color: #94a3b8; margin: 0;\">"
                    + "                You received this notification because email alerts are enabled on your account. You can manage your notification preferences anytime in your <a href='" + profileUrl + "' style='color: #2563eb; text-decoration: none; font-weight: 600;'>NexHub Profile Settings</a>."
                    + "              </p>"
                    + "            </td>"
                    + "          </tr>"
                    + "        </table>"
                    + "        <table border='0' cellpadding='0' cellspacing='0' width='100%' style='max-width: 580px; margin-top: 20px; text-align: center;'>"
                    + "          <tr>"
                    + "            <td>"
                    + "              <p style='font-size: 11px; color: #cbd5e1; margin: 0;'>"
                    + "                &copy; 2026 NexHub. All rights reserved."
                    + "              </p>"
                    + "            </td>"
                    + "          </tr>"
                    + "        </table>"
                    + "      </td>"
                    + "    </tr>"
                    + "  </table>"
                    + "</body>"
                    + "</html>";

            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(cleanApiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", cleanFromEmail);
            payload.put("to", to);
            payload.put("subject", subject);
            payload.put("html", htmlContent);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, entity, String.class);
            log.info("Email sent successfully via Resend to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via Resend to {}: {}", to, e.getMessage());
        }
    }
}
