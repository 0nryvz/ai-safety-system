package com.isg.backend.violation.domain.detection;

/**
 * Sol üst koordinat ve boyut bilgisiyle temsil edilen,
 * 0..1 aralığında normalize edilmiş bounding box.
 */
public record BoundingBox(
        double x,
        double y,
        double width,
        double height
) {

    private static final double TOLERANCE = 0.0001;

    public BoundingBox {
        validateRange(x,"x");
        validateRange(y, "y");
        validateRange(width, "width");
        validateRange(height, "height");

        if (x + width > 1.0 + TOLERANCE) {
            throw new IllegalArgumentException(
                    "Bounding box x + width must not exceed 1.0"
            );
        }

        if (y + height > 1.0 + TOLERANCE) {
            throw new IllegalArgumentException(
                    "Bounding box y + height must not exceed 1.0"
            );
        }
    }

    public double centerX() {
        return x + width / 2.0;
    }

    public double centerY() {
        return y + height / 2.0;
    }

    public double footX() {
        return centerX();
    }

    public double footY() {
        return y + height;
    }

    public double area() {
        return width * height;
    }

    private static void validateRange(double value, String fieldName) {
        if (value < 0.0 || value > 1.0 + TOLERANCE) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0.0 and 1.0"
            );
        }
    }
}