package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReplyButton {

    @NotBlank(message = "Button text is required")
    @Size(max = 20, message = "Max 20 characters")
    private String text;

    @NotBlank(message = "Button ID is required")
    private String id;

    // Getters e Setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}