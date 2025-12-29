package com.example.wppgateway.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendListRequest {

    @NotBlank(message = "Session is required")
    private String session;

    @NotBlank(message = "Phone is required")
    private String phone;

    private boolean isGroup = false;

    @NotBlank(message = "Button text is required")
    private String buttonText;

    private String description;

    @NotNull(message = "Sections cannot be null")
    @Size(min = 1, message = "At least one section is required")
    private List<ListSection> sections;

}