package com.nexhub.backend.dto.payment;

import com.nexhub.backend.model.User;

import java.math.BigDecimal;

public record BalanceResponse(
        Long userId,
        String username,
        BigDecimal availableBalance,
        BigDecimal escrowBalance
) {
    public static BalanceResponse fromUser(User user) {
        return new BalanceResponse(
                user.getId(),
                user.getUsername(),
                user.getAvailableBalance(),
                user.getEscrowBalance()
        );
    }
}
