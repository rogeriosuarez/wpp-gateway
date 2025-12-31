package com.heureca.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {
    @NotBlank(message = "Phone is required")
    private String phone; // Número do aparelho que vai enviar

    private String description; // Opcional: "Celular João", "WhatsApp empresa"

    // Validação personalizada
    public boolean isPhoneValid() {
        if (phone == null)
            return false;
        // Remove tudo que não é número e verifica se tem pelo menos 10 dígitos
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        return cleanPhone.length() >= 10;
    }

    // Método para obter phone limpo
    public String getCleanPhone() {
        if (phone == null)
            return null;
        return phone.replaceAll("[^0-9]", "");
    }
}