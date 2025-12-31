package com.heureca.wppgateway.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "session_usage")
@Data
public class SessionUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_name", nullable = false)
    private String sessionName;

    @Column(name = "count", nullable = false)
    private int count = 0;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // Construtor padrão
    public SessionUsage() {
        this.date = LocalDate.now();
    }

    // Construtor com parâmetros
    public SessionUsage(String sessionName) {
        this();
        this.sessionName = sessionName;
    }

}