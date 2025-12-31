package com.heureca.wppgateway.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Data;

@Entity
@Table(name = "message_usage")
@Data
public class MessageUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String clientApiKey;
    private LocalDate date;
    private int count;
}
