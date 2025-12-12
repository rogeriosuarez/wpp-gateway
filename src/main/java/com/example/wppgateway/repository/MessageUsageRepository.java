package com.example.wppgateway.repository;

import com.example.wppgateway.model.MessageUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface MessageUsageRepository extends JpaRepository<MessageUsage, Long> {
    Optional<MessageUsage> findByClientApiKeyAndDate(String clientApiKey, LocalDate date);
}
