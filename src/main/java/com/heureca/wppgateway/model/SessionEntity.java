package com.heureca.wppgateway.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Table(name = "sessions")
@Data
public class SessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", unique = true, nullable = false)
    private String sessionName;

    @Column(name = "client_api_key", nullable = false)
    private String clientApiKey;

    @Column(name = "phone", unique = true, nullable = false)
    private String phone;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "wpp_token", length = 2000)
    private String wppToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
