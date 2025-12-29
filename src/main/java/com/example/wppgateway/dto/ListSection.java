package com.example.wppgateway.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListSection {

    @NotBlank(message = "Section title is required")
    private String title;

    @NotNull(message = "Rows cannot be null")
    @Size(min = 1, message = "At least one row is required")
    private List<ListRow> rows;

}