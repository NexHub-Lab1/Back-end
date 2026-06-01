package com.nexhub.backend.repository;

import com.nexhub.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByTask_IdOrderByCreatedAtDesc(Long taskId);

    boolean existsByTask_Id(Long taskId);

    boolean existsByTask_IdAndStatusIn(Long taskId, Collection<String> statuses);

    Optional<Payment> findFirstByTask_IdAndStatusOrderByCreatedAtDesc(Long taskId, String status);

    Optional<Payment> findByExternalReference(String externalReference);
}
