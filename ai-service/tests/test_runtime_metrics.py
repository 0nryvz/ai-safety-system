from fastapi.testclient import TestClient

from app.main import app
from app.services.model_runner import InferenceResult


HEADERS = {
    "X-Camera-Id": "cam-metrics",
    "X-Session-Id": "session-metrics",
    "X-Frame-Timestamp": "2026-08-22T10:00:00Z",
    "X-Frame-Event-Id": (
        "33333333-3333-3333-3333-333333333333"
    ),
    "Content-Type": "image/jpeg",
}


def test_metrics_endpoint_initial_values():
    with TestClient(app) as client:
        response = client.get(
            "/internal/v1/metrics"
        )

        assert response.status_code == 200

        body = response.json()

        assert body["processedTotal"] == 0
        assert body["inferenceErrorTotal"] == 0
        assert body["invalidJpegTotal"] == 0
        assert body["backendDispatchSuccessTotal"] == 0
        assert body["backendDispatchErrorTotal"] == 0
        assert body["backendDispatchLatencyMs"]["count"] == 0
        assert body["throughput"]["windowSeconds"] == 60


def test_metrics_record_successful_processing_and_dispatch():
    with TestClient(app) as client:
        model_runner = app.state.model_runner
        backend_client = app.state.backend_client

        original_loaded = model_runner._loaded
        original_predict = model_runner.predict
        original_send = backend_client.send

        model_runner._loaded = True
        model_runner.predict = lambda jpeg_bytes: (
            InferenceResult(
                detections=[],
                inference_ms=5.0,
            )
        )

        async def fake_send(payload):
            return None

        backend_client.send = fake_send

        try:
            response = client.post(
                "/internal/v1/inference/frames",
                content=b"fake-jpeg",
                headers=HEADERS,
            )

            assert response.status_code == 202

            metrics_response = client.get(
                "/internal/v1/metrics"
            )

            assert metrics_response.status_code == 200

            body = metrics_response.json()

            assert body["processedTotal"] == 1
            assert (
                body["backendDispatchSuccessTotal"]
                == 1
            )
            assert body["backendDispatchErrorTotal"] == 0
            assert (
                body["backendDispatchLatencyMs"]["count"]
                == 1
            )
            assert (
                body["throughput"]["processedPerSecond"]
                >= 0
            )

        finally:
            model_runner._loaded = original_loaded
            model_runner.predict = original_predict
            backend_client.send = original_send


def test_metrics_record_invalid_jpeg_error():
    with TestClient(app) as client:
        model_runner = app.state.model_runner

        original_loaded = model_runner._loaded
        original_model = model_runner._model

        try:
            model_runner._loaded = True
            model_runner._model = object()

            response = client.post(
                "/internal/v1/inference/frames",
                content=b"invalid-jpeg",
                headers=HEADERS,
            )

            assert response.status_code == 400

            body = client.get(
                "/internal/v1/metrics"
            ).json()

            assert body["processedTotal"] == 0
            assert body["inferenceErrorTotal"] == 1
            assert body["invalidJpegTotal"] == 1
            assert body["backendDispatchSuccessTotal"] == 0

        finally:
            model_runner._loaded = original_loaded
            model_runner._model = original_model


def test_metrics_record_backend_dispatch_error():
    from app.services.backend_client import BackendClientError

    with TestClient(app) as client:
        model_runner = app.state.model_runner
        backend_client = app.state.backend_client

        original_loaded = model_runner._loaded
        original_predict = model_runner.predict
        original_send = backend_client.send

        model_runner._loaded = True
        model_runner.predict = lambda jpeg_bytes: (
            InferenceResult(
                detections=[],
                inference_ms=5.0,
            )
        )

        async def fake_send(payload):
            raise BackendClientError(
                "Backend unavailable",
                status_code=None,
            )

        backend_client.send = fake_send

        try:
            response = client.post(
                "/internal/v1/inference/frames",
                content=b"fake-jpeg",
                headers=HEADERS,
            )

            assert response.status_code == 502

            body = client.get(
                "/internal/v1/metrics"
            ).json()

            assert body["processedTotal"] == 1
            assert body["backendDispatchSuccessTotal"] == 0
            assert body["backendDispatchErrorTotal"] == 1
            assert (
                body["backendDispatchLatencyMs"]["count"]
                == 1
            )

        finally:
            model_runner._loaded = original_loaded
            model_runner.predict = original_predict
            backend_client.send = original_send