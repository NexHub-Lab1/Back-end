package com.nexhub.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void sendNotificationEmail(String to, String subject, String body) {
        sendNotificationEmail(to, subject, body, "INFO", null);
    }

    @Async
    public void sendNotificationEmail(String to, String subject, String body, String type, String targetPath) {
        String cleanTo = clean(to);
        if (cleanTo.isBlank()) {
            log.warn("Email notification skipped because recipient is blank");
            return;
        }

        String cleanFromEmail = clean(fromEmail);
        if (cleanFromEmail.isBlank() || clean(smtpPassword).isBlank()) {
            log.warn("Email notification to {} skipped: SMTP_USERNAME and SMTP_PASSWORD must be configured", cleanTo);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(cleanFromEmail);
            helper.setTo(cleanTo);
            helper.setSubject(clean(subject));
            helper.setText(buildHtmlContent(body, type, targetPath), true);

            mailSender.send(message);
            log.info("Email sent successfully via SMTP to {}", cleanTo);
        } catch (MessagingException e) {
            log.error("MessagingException while sending email via SMTP to {}: {}", cleanTo, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", cleanTo, e.getMessage());
        }
    }

    private String buildHtmlContent(String body, String type, String targetPath) {
        String cleanFrontendUrl = clean(frontendUrl);
        if (cleanFrontendUrl.isBlank()) {
            cleanFrontendUrl = "http://localhost:5173";
        }

        String resolvedType = type != null ? type.toUpperCase() : "INFO";
        String badgeBg;
        String badgeColor;
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

        String actionUrl = cleanFrontendUrl;
        if (targetPath != null && !targetPath.isBlank()) {
            String path = targetPath.startsWith("/") ? targetPath : "/" + targetPath;
            actionUrl = cleanFrontendUrl + path;
        }
        String profileUrl = cleanFrontendUrl + "/profile";

        return "<!DOCTYPE html>"
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
                + "                    " + escapeHtml(resolvedType)
                + "                  </td>"
                + "                </tr>"
                + "              </table>"
                + "              <p style=\"font-size: 16px; line-height: 1.6; color: #334155; margin: 0 0 24px 0; font-weight: 500;\">"
                + "                " + escapeHtml(body)
                + "              </p>"
                + "              <table border='0' cellpadding='0' cellspacing='0' style='margin-top: 32px;'>"
                + "                <tr>"
                + "                  <td>"
                + "                    <a href='" + escapeHtml(actionUrl) + "' target='_blank' style=\"background-color: #2563eb; color: #ffffff; font-size: 14px; font-weight: 600; text-decoration: none; padding: 12px 24px; border-radius: 12px; display: inline-block; box-shadow: 0 4px 6px rgba(37,99,235,0.2);\">"
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
                + "                You received this notification because email alerts are enabled on your account. You can manage your notification preferences anytime in your <a href='" + escapeHtml(profileUrl) + "' style='color: #2563eb; text-decoration: none; font-weight: 600;'>NexHub Profile Settings</a>."
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
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^['\"]|['\"]$", "").trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
