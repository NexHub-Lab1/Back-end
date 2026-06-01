package com.nexhub.backend.dto.payment;

import com.nexhub.backend.model.Payment;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.User;
import com.nexhub.backend.model.WalletTransaction;

import java.math.BigDecimal;
import java.sql.Date;

public record WalletTransactionResponse(
        Long id,
        Long userId,
        String username,
        Long paymentId,
        Long taskId,
        String taskTitle,
        String type,
        BigDecimal amount,
        String currency,
        BigDecimal availableBalanceAfter,
        BigDecimal escrowBalanceAfter,
        String description,
        Date createdAt
) {
    public static WalletTransactionResponse fromWalletTransaction(WalletTransaction transaction) {
        User user = transaction.getUser();
        Payment payment = transaction.getPayment();
        Task task = transaction.getTask();

        return new WalletTransactionResponse(
                transaction.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getUsername() : null,
                payment != null ? payment.getId() : null,
                task != null ? task.getId() : null,
                task != null ? task.getTitle() : null,
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getAvailableBalanceAfter(),
                transaction.getEscrowBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
