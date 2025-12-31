package com.heureca.wppgateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListRow {

    @NotBlank(message = "Row ID is required")
    private String rowId;

    @NotBlank(message = "Row title is required")
    private String title;

    private String description;

}