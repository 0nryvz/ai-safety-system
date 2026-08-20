import json
from collections.abc import Callable

import httpx
import pytest

from app.infrastructure.http_recording_callback_client import (
    HttpRecordingCallbackClient,
)
from app.services.recording_callback_client import (
    RecordingCallbackError,
    RecordingCallbackPayload,
)


def _build_client_with_transport(
        handler: Callable[[httpx.Request], httpx.Response],
) -> HttpRecordingCallbackClient:
    transport = httpx.MockTransport(handler)
    async_client = httpx.AsyncClient(
        transport=transport,
    )

    return HttpRecordingCallbackClient(
        backend_base_url="http://backend.internal:8080",
        internal_api_key="very-secret-internal-key",
        http_client=async_client,
    )


@pytest.mark.asyncio
async def test_send_callback_ready_payload_and_header_and_endpoint() -> None:
    captured_request: httpx.Request | None = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_request
        captured_request = request
        return httpx.Response(
            status_code=202,
            json={"ok": True},
        )

    client = _build_client_with_transport(handler)

    await client.send_callback(
        RecordingCallbackPayload(
            recording_id="recording-1",
            violation_id="violation-1",
            status="READY",
            object_key="violations/2026/08/violation-1/recording-1.mp4",
            duration_ms=30_000,
            size_bytes=1_048_576,
            checksum="sha256:abcd",
            retry_count=2,
            cover_image_key=(
                "violations/2026/08/"
                "violation-1/cover.jpg"
            ),
        )
    )

    assert captured_request is not None
    assert (
        str(captured_request.url)
        == "http://backend.internal:8080/internal/v1/recordings/callback"
    )
    assert (
        captured_request.headers["X-Internal-Api-Key"]
        == "very-secret-internal-key"
    )
    assert json.loads(captured_request.content.decode("utf-8")) == {
        "recordingId": "recording-1",
        "violationId": "violation-1",
        "status": "READY",
        "objectKey": "violations/2026/08/violation-1/recording-1.mp4",
        "durationMs": 30_000,
        "sizeBytes": 1_048_576,
        "checksum": "sha256:abcd",
        "retryCount": 2,
        "coverImageKey": (
            "violations/2026/08/"
            "violation-1/cover.jpg"
        ),
    }


@pytest.mark.asyncio
async def test_send_callback_error_payload() -> None:
    captured_request: httpx.Request | None = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_request
        captured_request = request
        return httpx.Response(status_code=204)

    client = _build_client_with_transport(handler)

    await client.send_callback(
        RecordingCallbackPayload(
            recording_id="recording-2",
            violation_id="violation-2",
            status="ERROR",
            retry_count=3,
            error_code="UPLOAD_FAILED",
        )
    )

    assert captured_request is not None
    assert json.loads(captured_request.content.decode("utf-8")) == {
        "recordingId": "recording-2",
        "violationId": "violation-2",
        "status": "ERROR",
        "retryCount": 3,
        "errorCode": "UPLOAD_FAILED",
    }


@pytest.mark.asyncio
async def test_send_callback_maps_4xx_to_application_error() -> None:
    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        return httpx.Response(status_code=409)

    client = _build_client_with_transport(handler)

    with pytest.raises(
            RecordingCallbackError,
            match="status=409",
    ) as exc_info:
        await client.send_callback(
            RecordingCallbackPayload(
                recording_id="recording-3",
                violation_id="violation-3",
                status="ERROR",
                retry_count=0,
                error_code="CONFLICT",
            )
        )

    assert exc_info.value.retryable is False


@pytest.mark.asyncio
async def test_send_callback_maps_5xx_to_application_error() -> None:
    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        return httpx.Response(status_code=500)

    client = _build_client_with_transport(handler)

    with pytest.raises(
            RecordingCallbackError,
            match="status=500",
    ) as exc_info:
        await client.send_callback(
            RecordingCallbackPayload(
                recording_id="recording-4",
                violation_id="violation-4",
                status="READY",
                object_key="violations/2026/08/violation-4/recording-4.mp4",
                duration_ms=1,
                size_bytes=1,
                checksum="sha256:abc",
                retry_count=1,
            )
        )

    assert exc_info.value.retryable is True


@pytest.mark.asyncio
async def test_send_callback_maps_timeout_to_application_error() -> None:
    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        raise httpx.ConnectTimeout("timeout", request=request)

    client = _build_client_with_transport(handler)

    with pytest.raises(
            RecordingCallbackError,
            match="timed out",
    ) as exc_info:
        await client.send_callback(
            RecordingCallbackPayload(
                recording_id="recording-5",
                violation_id="violation-5",
                status="ERROR",
                retry_count=1,
                error_code="TIMEOUT",
            )
        )

    assert exc_info.value.retryable is True


@pytest.mark.asyncio
async def test_send_callback_maps_network_error_to_application_error() -> None:
    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        raise httpx.ConnectError("network", request=request)

    client = _build_client_with_transport(handler)

    with pytest.raises(
            RecordingCallbackError,
            match="request failed",
    ) as exc_info:
        await client.send_callback(
            RecordingCallbackPayload(
                recording_id="recording-6",
                violation_id="violation-6",
                status="ERROR",
                retry_count=0,
                error_code="NETWORK",
            )
        )

    assert exc_info.value.retryable is True


@pytest.mark.asyncio
async def test_send_callback_error_message_does_not_include_api_key() -> None:
    secret_api_key = "ultra-secret-key"

    transport = httpx.MockTransport(
        lambda request: httpx.Response(status_code=500)
    )
    async_client = httpx.AsyncClient(
        transport=transport,
    )
    client = HttpRecordingCallbackClient(
        backend_base_url="http://backend.internal:8080",
        internal_api_key=secret_api_key,
        http_client=async_client,
    )

    with pytest.raises(RecordingCallbackError) as exc_info:
        await client.send_callback(
            RecordingCallbackPayload(
                recording_id="recording-7",
                violation_id="violation-7",
                status="ERROR",
                retry_count=0,
                error_code="SERVER_ERROR",
            )
        )

    assert secret_api_key not in str(exc_info.value)
