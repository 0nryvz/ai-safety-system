package com.isg.backend.violation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DetectionItem(

        @NotBlank
        String label,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        BigDecimal confidence,

        @NotNull
        @Valid
        BoundingBox bbox

) {
}