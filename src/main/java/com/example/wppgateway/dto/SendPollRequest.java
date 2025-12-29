package com.example.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class SendPollRequest {

    @NotBlank(message = "Session is required")
    private String session;

    @NotBlank(message = "Phone is required")
    private String phone;

    private boolean isGroup = false;

    @NotBlank(message = "Poll name is required")
    private String name;

    @NotNull(message = "Choices cannot be null")
    @Size(min = 2, max = 12, message = "2-12 choices required") // WhatsApp max 12 options
    private List<String> choices;

    private Map<String, Object> options; // ex: {"selectableCount": 1}

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getChoices() {
        return choices;
    }

    public void setChoices(List<String> choices) {
        this.choices = choices;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}