package com.heureca.wppgateway.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "api_client")
@Data
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", unique = true, nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "daily_limit")
    private Long dailyLimit; // null = unlimited

    @Column(name = "daily_usage")
    private Long dailyUsage = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientSource source;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_reset")
    private LocalDate lastReset = LocalDate.now();
}
