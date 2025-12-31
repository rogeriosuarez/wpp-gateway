package com.heureca.wppgateway.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

@Entity
@Table(name = "clients")
@Data
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String apiKey;
    private String name;
    private int dailyLimit = 500;
    private LocalDateTime createdAt = LocalDateTime.now();
}
