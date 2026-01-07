package com.heureca.wppgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(name = "CreateSessionRequest", description = "Payload used to create a new WhatsApp session")
public class CreateSessionRequest {

    @NotBlank(message = "Phone is required")
    @Schema(description = "Phone number that will be used to connect WhatsApp", example = "+55 21 99999-8888", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;
    // Número do aparelho que vai enviar

    @Schema(description = "Optional description for identification purposes", example = "WhatsApp Empresa - Vendas")
    private String description;
    // Opcional: "Celular João", "WhatsApp empresa"

    /**
     * Custom validation
     */
    @Schema(description = "Indicates whether the provided phone number is valid (internal validation)", accessMode = Schema.AccessMode.READ_ONLY, example = "true")
    public boolean isPhoneValid() {
        if (phone == null)
            return false;

        // Remove everything that is not a number
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        return cleanPhone.length() >= 10;
    }

    /**
     * Returns only numeric phone
     */
    @Schema(description = "Returns the phone number containing only digits", accessMode = Schema.AccessMode.READ_ONLY, example = "5521999998888")
    public String getCleanPhone() {
        if (phone == null)
            return null;

        return phone.replaceAll("[^0-9]", "");
    }
}
