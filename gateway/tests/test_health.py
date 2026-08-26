from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_session_frame_ingestion_worker_coordinator,
)
from app.main import app
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerStats,
)


client = TestClient(app)


def test_health_returns_service_status() -> None:
    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "DEGRADED"
    assert body["ai_dispatch_status"] == "DEGRADED"
    assert body["ai_dispatch_configured"] is False
    assert body["ai_dispatch_circuit_open"] is False
    assert body["service"] == "camera-ingestion-gateway"
    assert body["version"] == "0.1.0"
    assert body["environment"] == "local"
    assert "timestamp" in body


def test_health_returns_degraded_when_ai_dispatch_unavailable() -> None:
    class DegradedIngestionCoordinator:
        async def stats(self) -> SessionFrameIngestionWorkerStats:
            return SessionFrameIngestionWorkerStats(
                ring_buffer_error_count=0,
                unexpected_error_count=0,
                sampled_frame_count=0,
                ai_dispatched_frame_count=0,
                ai_dropped_stale_frame_count=0,
                ai_dispatch_failure_count=1,
                ai_dispatch_timeout_count=1,
                ai_dispatch_retry_count=0,
                ai_dispatch_latency_avg_ms=0.0,
                active_ai_dispatch_worker_count=0,
                ai_dispatch_configured=True,
                ai_dispatch_available=False,
                ai_dispatch_circuit_open=True,
            )

    app.dependency_overrides[
        get_session_frame_ingestion_worker_coordinator
    ] = lambda: DegradedIngestionCoordinator()

    try:
        response = client.get("/health")
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["ai_dispatch_status"] == "DEGRADED"
    assert body["ai_dispatch_configured"] is True
    assert body["ai_dispatch_circuit_open"] is True