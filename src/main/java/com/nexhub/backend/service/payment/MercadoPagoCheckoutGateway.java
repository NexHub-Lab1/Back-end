package com.nexhub.backend.service.payment;

import com.nexhub.backend.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MercadoPagoCheckoutGateway implements PaymentGateway {
    private static final String API_BASE_URL = "https://api.mercadopago.com";

    private final RestClient restClient = RestClient.create(API_BASE_URL);

    @Value("${mercadopago.access-token:}")
    private String accessToken;

    @Value("${mercadopago.webhook-url:}")
    private String webhookUrl;

    @Value("${mercadopago.use-sandbox:true}")
    private boolean useSandbox;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public ProviderPaymentIntent createFundingIntent(Payment payment) {
        validateAccessTokenConfigured();
        validateWebhookConfigured();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "task-" + payment.getTask().getId());
        item.put("title", "NexHub task reward: " + payment.getTask().getTitle());
        item.put("description", "Funding held for task completion in NexHub");
        item.put("currency_id", payment.getCurrency());
        item.put("quantity", 1);
        item.put("unit_price", payment.getAmount());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", payment.getExternalReference());
        body.put("back_urls", buildBackUrls(payment));
        body.put("auto_return", "approved");

        body.put("notification_url", webhookUrl.trim());

        Map<String, Object> response = postPreference(body);
        String preferenceId = requiredString(response, "id");
        String checkoutUrl = checkoutUrl(response);

        return new ProviderPaymentIntent(preferenceId, checkoutUrl);
    }

    @Override
    public ProviderPaymentResult getPayment(String providerPaymentId) {
        validateAccessTokenConfigured();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago payment id is required");
        }

        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/{paymentId}", providerPaymentId.trim())
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                throw new IllegalArgumentException("Mercado Pago returned an empty payment response");
            }

            return new ProviderPaymentResult(
                    String.valueOf(response.get("id")),
                    requiredString(response, "external_reference"),
                    requiredString(response, "status"),
                    optionalString(response, "status_detail"),
                    requiredAmount(response, "transaction_amount"),
                    requiredString(response, "currency_id")
            );
        } catch (RestClientResponseException e) {
            throw new IllegalArgumentException("Unable to retrieve payment from Mercado Pago: " + mercadoPagoError(e), e);
        }
    }

    private Map<String, Object> postPreference(Map<String, Object> body) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                throw new IllegalArgumentException("Mercado Pago returned an empty checkout response");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new IllegalArgumentException("Unable to create Mercado Pago checkout preference: " + mercadoPagoError(e), e);
        }
    }

    private String mercadoPagoError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "HTTP " + e.getStatusCode().value();
        }
        return body;
    }

    private Map<String, String> buildBackUrls(Payment payment) {
        String detailUrl = trimmedFrontendUrl() + "/task/" + payment.getTask().getId();
        return Map.of(
                "success", detailUrl + "?payment=approved",
                "failure", detailUrl + "?payment=failure",
                "pending", detailUrl + "?payment=pending"
        );
    }

    private String checkoutUrl(Map<String, Object> response) {
        String key = useSandbox ? "sandbox_init_point" : "init_point";
        String url = optionalString(response, key);
        if (url == null) {
            url = optionalString(response, "init_point");
        }
        if (url == null) {
            throw new IllegalArgumentException("Mercado Pago did not return a checkout URL");
        }
        return url;
    }

    private String trimmedFrontendUrl() {
        return frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    private void validateAccessTokenConfigured() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago access token is not configured");
        }
    }

    private void validateWebhookConfigured() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago webhook URL is not configured");
        }
    }

    private String requiredString(Map<String, Object> response, String key) {
        String value = optionalString(response, key);
        if (value == null) {
            throw new IllegalArgumentException("Mercado Pago response is missing " + key);
        }
        return value;
    }

    private String optionalString(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private BigDecimal requiredAmount(Map<String, Object> response, String key) {
        String value = requiredString(response, key);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mercado Pago response contains an invalid " + key, e);
        }
    }
}
