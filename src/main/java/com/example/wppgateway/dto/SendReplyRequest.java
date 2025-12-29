package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class SendReplyRequest {

    @NotBlank(message = "Session is required")
    private String session;

    @NotBlank(message = "Phone is required")
    private String phone;

    private boolean isGroup = false;

    @NotBlank(message = "Message is required")
    private String message;

    @NotNull(message = "Buttons cannot be null")
    @Size(min = 1, max = 3, message = "1-3 buttons required")
    private List<ReplyButton> buttons;

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

    public List<ReplyButton> getButtons() {
        return buttons;
    }

    public void setButtons(List<ReplyButton> buttons) {
        this.buttons = buttons;
    }
}