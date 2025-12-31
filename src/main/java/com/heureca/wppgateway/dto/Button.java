package com.heureca.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;

public class Button {

    @NotBlank(message = "Button ID is required")
    private String buttonId;

    @NotBlank(message = "Button text is required")
    private String buttonText;

    // Getters e Setters
    public String getButtonId() {
        return buttonId;
    }

    public void setButtonId(String buttonId) {
        this.buttonId = buttonId;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void setButtonText(String buttonText) {
        this.buttonText = buttonText;
    }
}