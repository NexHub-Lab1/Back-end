package com.nexhub.backend.dto.payment;

import com.nexhub.backend.model.Payment;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.User;

import java.math.BigDecimal;
import java.sql.Date;

public record PaymentResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long payerId,
        String payerUsername,
        BigDecimal amount,
        String currency,
        String provider,
        String providerPaymentId,
        String externalReference,
        String checkoutUrl,
        String status,
        String failureReason,
        Date createdAt,
        Date updatedAt,
        Date approvedAt,
        Date failedAt,
        Date releasedAt,
        Date refundedAt
) {
    public static PaymentResponse fromPayment(Payment payment) {
        Task task = payment.getTask();
        User payer = payment.getPayer();

        return new PaymentResponse(
                payment.getId(),
                task != null ? task.getId() : null,
                task != null ? task.getTitle() : null,
                payer != null ? payer.getId() : null,
                payer != null ? payer.getUsername() : null,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getProviderPaymentId(),
                payment.getExternalReference(),
                payment.getCheckoutUrl(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getApprovedAt(),
                payment.getFailedAt(),
                payment.getReleasedAt(),
                payment.getRefundedAt()
        );
    }
}
