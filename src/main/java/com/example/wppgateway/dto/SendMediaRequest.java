package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendMediaRequest {
    @NotBlank(message = "Session is required")
    private String session;

    @NotBlank(message = "Phone is required")
    private String phone;

    private boolean isGroup = false;
    private String caption;

}