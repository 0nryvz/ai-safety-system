from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_session_frame_ingestion_worker_coordinator,
    get_session_frame_queue_manager,
    get_session_frame_ring_buffer_manager,
    get_session_manager,
)
from app.main import app
from app.services.ai_frame_client import NoOpAIFrameClient
from app.services.session_ai_frame_dispatch_worker import (
    SessionAIFrameDispatchWorkerCoordinator,
)
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.session_manager import SessionManager


def get_metrics_body(
        client: TestClient,
) -> dict[str, object]:
    response = client.get("/metrics")

    assert response.status_code == 200
    return response.json()


def wait_until_frame_buffered(
        client: TestClient,
        *,
        expected_min_bytes: int,
        attempts: int = 50,
) -> dict[str, object]:
    last_body: dict[str, object] | None = None

    for _ in range(attempts):
        body = get_metrics_body(client)
        last_body = body

        if (
                body["buffered_frames"] >= 1
                and body["buffered_bytes"] >= expected_min_bytes
                and body["ai_sampled_frames"] >= 1
        ):
            return body

    pytest.fail(
        "Worker frame taşımayı beklenen sürede tamamlamadı: "
        f"{last_body}"
    )


def test_metrics_returns_gateway_state() -> None:
    session_manager = SessionManager()
    queue_manager = SessionFrameQueueManager(
        max_frames=2,
    )
    ring_buffer_manager = SessionFrameRingBufferManager(
        buffer_seconds=10,
        max_frames=300,
        max_bytes=67_108_864,
    )
    ingestion_worker_coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_dispatch_worker_coordinator=(
            SessionAIFrameDispatchWorkerCoordinator(
                ai_frame_client=NoOpAIFrameClient(),
                ai_configured=False,
            )
        ),
    )

    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )
    app.dependency_overrides[
        get_session_frame_queue_manager
    ] = lambda: queue_manager
    app.dependency_overrides[
        get_session_frame_ring_buffer_manager
    ] = lambda: ring_buffer_manager
    app.dependency_overrides[
        get_session_frame_ingestion_worker_coordinator
    ] = lambda: ingestion_worker_coordinator

    jpeg_data = b"\xff\xd8frame\xff\xd9"

    try:
        with TestClient(app) as client:
            open_response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "dev-session-token",
                },
            )

            upload_response = client.post(
                "/api/v1/sessions/session-1/frames",
                headers={
                    "X-Camera-Id": "camera-1",
                    "X-Frame-Timestamp": (
                        datetime.now(timezone.utc).isoformat()
                    ),
                    "Content-Type": "image/jpeg",
                },
                content=jpeg_data,
            )

            metrics_body = wait_until_frame_buffered(
                client,
                expected_min_bytes=len(jpeg_data),
            )

            close_response = client.post(
                "/api/v1/sessions/session-1/close",
                json={
                    "cameraId": "camera-1",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201
    assert upload_response.status_code == 202
    assert close_response.status_code == 204

    assert metrics_body["active_sessions"] == 1
    assert metrics_body["active_frame_queues"] == 1
    assert metrics_body["queued_frames"] == 0
    assert metrics_body["active_ring_buffers"] == 1
    assert metrics_body["active_ingestion_workers"] == 1
    assert metrics_body["buffered_frames"] >= 1
    assert metrics_body["buffered_bytes"] >= len(jpeg_data)
    assert metrics_body["frame_queue_capacity_per_session"] >= 1
    assert metrics_body["ai_sampled_frames"] >= 1
    assert metrics_body["ai_dispatched_frames"] == 0
    assert metrics_body["ai_dropped_stale_frames"] >= 0
    assert metrics_body["ai_dispatch_failures"] == 0
    assert metrics_body["ai_dispatch_timeouts"] >= 0
    assert metrics_body["ai_dispatch_retries"] >= 0
    assert metrics_body["ai_dispatch_latency_avg_ms"] >= 0
    assert metrics_body["active_ai_dispatch_workers"] == 1
    assert metrics_body["ai_dispatch_configured"] is False
    assert metrics_body["ai_dispatch_available"] is False
    assert metrics_body["ai_dispatch_circuit_open"] is False
    assert "timestamp" in metrics_body