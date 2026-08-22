"""AI Worker runtime sayaçları ve throughput ölçümü."""

from __future__ import annotations

import time
from collections import deque
from threading import Lock


class RuntimeMetrics:
    """AI Worker process-local runtime metric deposu."""

    THROUGHPUT_WINDOW_SECONDS = 60.0

    def __init__(self) -> None:
        self._lock = Lock()
        self._started_monotonic = time.monotonic()

        self._processed_total = 0
        self._inference_error_total = 0
        self._invalid_jpeg_total = 0

        self._backend_dispatch_success_total = 0
        self._backend_dispatch_error_total = 0

        self._backend_latency_count = 0
        self._backend_latency_sum_ms = 0.0
        self._backend_latency_max_ms = 0.0

        self._processed_timestamps: deque[float] = deque()

    def record_processed(self) -> None:
        now = time.monotonic()

        with self._lock:
            self._processed_total += 1
            self._processed_timestamps.append(now)
            self._remove_old_timestamps(now)

    def record_inference_error(
        self,
        *,
        invalid_jpeg: bool = False,
    ) -> None:
        with self._lock:
            self._inference_error_total += 1

            if invalid_jpeg:
                self._invalid_jpeg_total += 1

    def record_backend_dispatch(
        self,
        *,
        success: bool,
        latency_ms: float,
    ) -> None:
        with self._lock:
            if success:
                self._backend_dispatch_success_total += 1
            else:
                self._backend_dispatch_error_total += 1

            self._backend_latency_count += 1
            self._backend_latency_sum_ms += latency_ms
            self._backend_latency_max_ms = max(
                self._backend_latency_max_ms,
                latency_ms,
            )

    def snapshot(self) -> dict:
        now = time.monotonic()

        with self._lock:
            self._remove_old_timestamps(now)

            uptime_seconds = max(
                now - self._started_monotonic,
                0.0,
            )

            throughput_duration = min(
                uptime_seconds,
                self.THROUGHPUT_WINDOW_SECONDS,
            )

            if throughput_duration > 0:
                processed_per_second = (
                    len(self._processed_timestamps)
                    / throughput_duration
                )
            else:
                processed_per_second = 0.0

            if self._backend_latency_count > 0:
                average_latency_ms = (
                    self._backend_latency_sum_ms
                    / self._backend_latency_count
                )
            else:
                average_latency_ms = 0.0

            return {
                "uptimeSeconds": round(uptime_seconds, 3),
                "processedTotal": self._processed_total,
                "inferenceErrorTotal": self._inference_error_total,
                "invalidJpegTotal": self._invalid_jpeg_total,
                "backendDispatchSuccessTotal": (
                    self._backend_dispatch_success_total
                ),
                "backendDispatchErrorTotal": (
                    self._backend_dispatch_error_total
                ),
                "backendDispatchLatencyMs": {
                    "count": self._backend_latency_count,
                    "average": round(average_latency_ms, 3),
                    "max": round(
                        self._backend_latency_max_ms,
                        3,
                    ),
                },
                "throughput": {
                    "windowSeconds": 60,
                    "processedPerSecond": round(
                        processed_per_second,
                        3,
                    ),
                },
            }

    def _remove_old_timestamps(
        self,
        now: float,
    ) -> None:
        cutoff = (
            now
            - self.THROUGHPUT_WINDOW_SECONDS
        )

        while (
            self._processed_timestamps
            and self._processed_timestamps[0] < cutoff
        ):
            self._processed_timestamps.popleft()