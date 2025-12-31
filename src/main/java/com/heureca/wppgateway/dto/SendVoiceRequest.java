package com.heureca.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendVoiceRequest extends SendMediaRequest {
    @NotBlank(message = "Base64 audio is required")
    private String base64Ptt; // base64 do áudio

}