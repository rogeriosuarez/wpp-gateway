package com.heureca.wppgateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heureca.wppgateway.model.SessionEntity;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {
    Optional<SessionEntity> findBySessionName(String sessionName);

    Optional<SessionEntity> findByPhone(String phone);

    Optional<SessionEntity> findByClientApiKeyAndSessionName(String clientApiKey, String sessionName);
}