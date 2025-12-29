package com.example.wppgateway.repository;

import com.example.wppgateway.model.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {
    Optional<SessionEntity> findBySessionName(String sessionName);

    Optional<SessionEntity> findByPhone(String phone);

    Optional<SessionEntity> findByClientApiKeyAndSessionName(String clientApiKey, String sessionName);
}