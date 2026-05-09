package com.nexhub.backend.dto.payment;

public record PaymentSimulationRequest(
        Long paymentId,
        String status,
        String failureReason
) {
}
