package com.example.wppgateway.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Table(name = "sessions")
@Data
public class SessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String sessionName;
    private String clientApiKey;
    @Column(length = 2000)
    private String wppToken;
    private String status;
    private LocalDateTime createdAt = LocalDateTime.now();
}
