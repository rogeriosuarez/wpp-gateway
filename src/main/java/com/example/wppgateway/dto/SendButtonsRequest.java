package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class SendButtonsRequest {

    @NotBlank(message = "Session is required")
    private String session;

    @NotBlank(message = "Phone is required")
    private String phone;

    private boolean isGroup = false;

    @NotBlank(message = "Message text is required")
    private String message;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Buttons cannot be null")
    @Size(min = 1, max = 3, message = "1-3 buttons required") // WhatsApp max 3 buttons
    private List<Button> buttons;

    // Getters e Setters
    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Button> getButtons() {
        return buttons;
    }

    public void setButtons(List<Button> buttons) {
        this.buttons = buttons;
    }
}