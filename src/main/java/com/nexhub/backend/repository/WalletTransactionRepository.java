package com.nexhub.backend.repository;

import com.nexhub.backend.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
