package com.nexhub.backend.dto.payment;

public record TaskPaymentRequest(
        Long taskId,
        Long payerId
) {
}
