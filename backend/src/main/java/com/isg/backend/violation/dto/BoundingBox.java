package com.isg.backend.violation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;

public record BoundingBox(

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        BigDecimal x,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        BigDecimal y,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1.0")
        BigDecimal width,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1.0")
        BigDecimal height
) {
        private static final BigDecimal ONE =
                BigDecimal.ONE;

        @AssertTrue(
                message = "bbox must remain inside the normalized frame"
        )
        public boolean isWithinFrame() {
                if (x == null
                        || y == null
                        || width == null
                        || height == null) {
                        return true;
                }

                return x.add(width).compareTo(ONE) <= 0
                        && y.add(height).compareTo(ONE) <= 0;
        }
}
