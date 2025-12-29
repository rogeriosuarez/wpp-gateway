package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank(message = "Session name is required")
    private String session;

    @NotBlank(message = "Recipient phone is required")
    private String to;

    @NotBlank(message = "Message is required")
    private String message;
}