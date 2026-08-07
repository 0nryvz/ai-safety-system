package com.isg.backend.violation.domain.geometry;

import java.util.List;

public final class NormalizedPolygon {

    private static final double TOLERANCE = 1e-9;

    private final List<NormalizedPoint> vertices;

    public NormalizedPolygon(List<NormalizedPoint> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException(
                    "Polygon must contain at least three vertices"
            );
        }

        this.vertices = List.copyOf(vertices);
    }

    public boolean contains(double x, double y) {
        validateCoordinate(x, "x");
        validateCoordinate(y, "y");

        if (isOnBoundary(x, y)) {
            return true;
        }

        boolean inside = false;
        int vertexCount = vertices.size();

        for (int i = 0, j = vertexCount - 1; i < vertexCount; j = i++) {
            NormalizedPoint current = vertices.get(i);
            NormalizedPoint previous = vertices.get(j);

            boolean crossesHorizontalRay =
                    (current.y() > y) != (previous.y() > y);

            if (!crossesHorizontalRay) {
                continue;
            }

            double intersectionX =
                    (previous.x() - current.x())
                            * (y - current.y())
                            / (previous.y() - current.y())
                            + current.x();

            if (x < intersectionX) {
                inside = !inside;
            }
        }

        return inside;
    }

    public List<NormalizedPoint> vertices() {
        return vertices;
    }

    private boolean isOnBoundary(double x, double y) {
        int vertexCount = vertices.size();

        for (int i = 0, j = vertexCount - 1; i < vertexCount; j = i++) {
            NormalizedPoint current = vertices.get(i);
            NormalizedPoint previous = vertices.get(j);

            if (x < Math.min(previous.x(), current.x()) - TOLERANCE
                    || x > Math.max(previous.x(), current.x()) + TOLERANCE
                    || y < Math.min(previous.y(), current.y()) - TOLERANCE
                    || y > Math.max(previous.y(), current.y()) + TOLERANCE) {
                continue;
            }

            double crossProduct =
                    (current.x() - previous.x()) * (y - previous.y())
                            - (current.y() - previous.y()) * (x - previous.x());

            if (Math.abs(crossProduct) <= TOLERANCE) {
                return true;
            }
        }

        return false;
    }

    private static void validateCoordinate(double value, String fieldName) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0.0 and 1.0"
            );
        }
    }
}