package com.nexhub.backend.repository;

import com.nexhub.backend.model.GithubWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubWebhookDeliveryRepository extends JpaRepository<GithubWebhookDelivery, String> {
}
