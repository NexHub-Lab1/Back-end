package com.nexhub.backend.repository;

import com.nexhub.backend.model.RewardDistribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardDistributionRepository extends JpaRepository<RewardDistribution, Long> {
    boolean existsBySubmission_Id(Long submissionId);

    List<RewardDistribution> findBySubmission_IdOrderByCreatedAtAsc(Long submissionId);
}
