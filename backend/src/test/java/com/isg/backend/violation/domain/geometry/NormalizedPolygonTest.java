package com.isg.backend.violation.domain.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizedPolygonTest {

    private final NormalizedPolygon square = new NormalizedPolygon(List.of(
            new NormalizedPoint(0.2, 0.2),
            new NormalizedPoint(0.8, 0.2),
            new NormalizedPoint(0.8, 0.8),
            new NormalizedPoint(0.2, 0.8)
    ));

    @Test
    void containsReturnsTrueWhenPointIsInside() {
        assertThat(square.contains(0.5, 0.5)).isTrue();
    }

    @Test
    void containsReturnsFalseWhenPointIsOutside() {
        assertThat(square.contains(0.1, 0.5)).isFalse();
        assertThat(square.contains(0.9, 0.5)).isFalse();
        assertThat(square.contains(0.5, 0.1)).isFalse();
    }

    @Test
    void containsReturnsTrueWhenPointIsOnEdge() {
        assertThat(square.contains(0.2, 0.5)).isTrue();
        assertThat(square.contains(0.5, 0.8)).isTrue();
    }

    @Test
    void containsReturnsTrueWhenPointIsOnVertex() {
        assertThat(square.contains(0.2, 0.2)).isTrue();
    }

    @Test
    void constructorRejectsPolygonWithLessThanThreeVertices() {
        assertThatThrownBy(() -> new NormalizedPolygon(List.of(
                new NormalizedPoint(0.1, 0.1),
                new NormalizedPoint(0.5, 0.5)
        )))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsWorksForConcavePolygon() {
        NormalizedPolygon concavePolygon = new NormalizedPolygon(List.of(
                new NormalizedPoint(0.1, 0.1),
                new NormalizedPoint(0.5, 0.1),
                new NormalizedPoint(0.5, 0.5),
                new NormalizedPoint(0.9, 0.5),
                new NormalizedPoint(0.9, 0.9),
                new NormalizedPoint(0.1, 0.9)
        ));

        assertThat(concavePolygon.contains(0.3, 0.3)).isTrue();
        assertThat(concavePolygon.contains(0.7, 0.3)).isFalse();
        assertThat(concavePolygon.contains(0.7, 0.7)).isTrue();
    }
}