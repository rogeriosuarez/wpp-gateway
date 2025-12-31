package com.heureca.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendImageRequest extends SendMediaRequest {
    @NotBlank(message = "Base64 image is required")
    private String base64;

    private String filename = "image.jpg";
}