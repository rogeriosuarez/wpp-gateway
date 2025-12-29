package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendFileRequest extends SendMediaRequest {
    @NotBlank(message = "Base64 file is required")
    private String base64;

    @NotBlank(message = "Filename is required")
    private String filename;

}
