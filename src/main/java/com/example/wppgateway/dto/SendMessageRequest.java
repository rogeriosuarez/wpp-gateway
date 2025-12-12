package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank
    private String session;
    @NotBlank
    private String to;
    @NotBlank
    private String message;
}
