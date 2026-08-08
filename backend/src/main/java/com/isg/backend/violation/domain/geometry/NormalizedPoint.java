package com.isg.backend.violation.domain.geometry;

public record NormalizedPoint(
        double x,
        double y
) {

    public NormalizedPoint {
        validateCoordinate(x, "x");
        validateCoordinate(y, "y");
    }

    private static void validateCoordinate(double value, String fieldName) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0.0 and 1.0"
            );
        }
    }
}