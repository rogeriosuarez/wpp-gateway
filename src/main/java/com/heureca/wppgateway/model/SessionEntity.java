package com.heureca.wppgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Data
@Schema(name = "Session")
public class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Internal database identifier", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Column(name = "session_name", unique = true, nullable = false)
    @Schema(description = "Unique session identifier used internally and with WPPConnect", example = "wpp_5521999998888", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionName;

    @Column(name = "client_api_key", nullable = false)
    @Schema(description = "Client API key owner of this session", example = "client_abc123", accessMode = Schema.AccessMode.READ_ONLY)
    private String clientApiKey;

    @Column(name = "phone", unique = true, nullable = false)
    @Schema(description = "Phone number associated with the WhatsApp session", example = "5521999998888", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @Column(name = "description")
    @Schema(description = "Optional human-readable description for this session", example = "WhatsApp Vendas - Loja Centro")
    private String description;

    @Column(name = "status")
    @Schema(description = "Current session status", example = "CONNECTED", accessMode = Schema.AccessMode.READ_ONLY)
    private String status;

    @Column(name = "wpp_token", length = 2000)
    @Schema(description = "Internal token used to authenticate with WPPConnect", accessMode = Schema.AccessMode.READ_ONLY)
    private String wppToken;

    @Column(name = "created_at")
    @Schema(description = "Session creation timestamp", example = "2025-12-31T14:32:10", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt = LocalDateTime.now();
}
