package com.isg.backend.violation.infrastructure.camera;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.application.RestrictedZoneProvider;
import com.isg.backend.violation.domain.geometry.NormalizedPolygon;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestrictedZoneProviderAdapterTest {

    private final RestrictedZoneProvider provider =
            mock(
                    RestrictedZoneProvider.class
            );

    private final RestrictedZoneProviderAdapter adapter =
            new RestrictedZoneProviderAdapter(
                    provider
            );

    @Test
    void convertsBe2PolygonToNormalizedPolygon() {
        UUID cameraId =
                UUID.randomUUID();

        when(
                provider.getActivePolygonForCamera(
                        cameraId
                )
        ).thenReturn(
                List.of(
                        new PointDto(
                                0.10,
                                0.10
                        ),
                        new PointDto(
                                0.80,
                                0.10
                        ),
                        new PointDto(
                                0.80,
                                0.80
                        ),
                        new PointDto(
                                0.10,
                                0.80
                        )
                )
        );

        Optional<NormalizedPolygon> result =
                adapter.findZone(
                        cameraId
                );

        assertThat(result)
                .isPresent();

        assertThat(
                result.orElseThrow()
                        .vertices()
        ).hasSize(
                4
        );

        assertThat(
                result.orElseThrow()
                        .contains(
                                0.50,
                                0.50
                        )
        ).isTrue();
    }

    @Test
    void returnsEmptyWhenCameraHasNoActiveZone() {
        UUID cameraId =
                UUID.randomUUID();

        when(
                provider.getActivePolygonForCamera(
                        cameraId
                )
        ).thenReturn(
                List.of()
        );

        Optional<NormalizedPolygon> result =
                adapter.findZone(
                        cameraId
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void rejectsMalformedActivePolygon() {
        UUID cameraId =
                UUID.randomUUID();

        when(
                provider.getActivePolygonForCamera(
                        cameraId
                )
        ).thenReturn(
                List.of(
                        new PointDto(
                                0.10,
                                0.10
                        ),
                        new PointDto(
                                0.80,
                                0.10
                        )
                )
        );

        assertThatThrownBy(
                () ->
                        adapter.findZone(
                                cameraId
                        )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "at least three points"
                );
    }
}