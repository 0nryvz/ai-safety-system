"""
Adım 4 - BackendDetectionClient testleri.

Gerçek Spring Boot backend olmadan, httpx.AsyncClient yerine geçen sahte bir
client ile retry/contract davranışını doğrular:
  - 2xx -> direkt döner, retry yok
  - 4xx (contract/auth) -> retry fırtınasına çevrilmez, hemen fırlatır
  - 5xx -> bounded retry (max 3 deneme), sonunda başarısızsa fırlatır
  - eventId payload'da her zaman aynı kalır
"""
from __future__ import annotations

from datetime import datetime, timezone

import httpx
import pytest

from app.core.config import Settings
from app.schemas.detection import DetectionRequest
from app.services.backend_client import (
    BackendClientError,
    BackendDetectionClient,
)


def make_payload(
    event_id: str = "11111111-1111-1111-1111-111111111111",
) -> DetectionRequest:
    return DetectionRequest(
        eventId=event_id,
        cameraId="cam-1",
        sessionId="sess-1",
        frameTimestamp=datetime.now(timezone.utc),
        modelVersion="v0.1.0",
        inferenceMs=42,
        detections=[],
    )


class FakeAsyncClient:
    """httpx.AsyncClient.post ile aynı imzayı taklit eden sahte client."""

    def __init__(
        self,
        responses: list[httpx.Response | Exception],
    ):
        self._responses = list(responses)
        self.calls: list[dict] = []

    async def post(
        self,
        url,
        json=None,
        headers=None,
    ):
        self.calls.append(
            {
                "url": url,
                "json": json,
                "headers": headers,
            }
        )

        item = self._responses.pop(0)

        if isinstance(item, Exception):
            raise item

        return item

    async def aclose(self):
        pass


def _settings() -> Settings:
    return Settings(
        AI_MODEL_PATH=None,
        BACKEND_BASE_URL="http://fake-backend:8080",
        INTERNAL_API_KEY="test-key",
    )


@pytest.mark.asyncio
async def test_success_response_no_retry():
    fake = FakeAsyncClient(
        [
            httpx.Response(
                200,
                json={"status": "ok"},
            )
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    response = await client.send(make_payload())

    assert response.status_code == 200
    assert len(fake.calls) == 1


@pytest.mark.asyncio
async def test_4xx_does_not_retry():
    fake = FakeAsyncClient(
        [
            httpx.Response(
                400,
                json={"error": "bad contract"},
            )
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    with pytest.raises(BackendClientError) as exc_info:
        await client.send(make_payload())

    assert exc_info.value.status_code == 400
    assert len(fake.calls) == 1


@pytest.mark.asyncio
async def test_401_auth_error_does_not_retry():
    fake = FakeAsyncClient(
        [
            httpx.Response(
                401,
                json={"error": "unauthorized"},
            )
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    with pytest.raises(BackendClientError) as exc_info:
        await client.send(make_payload())

    assert exc_info.value.status_code == 401
    assert len(fake.calls) == 1


@pytest.mark.asyncio
async def test_5xx_retries_then_succeeds():
    fake = FakeAsyncClient(
        [
            httpx.Response(
                503,
                json={"error": "temporarily unavailable"},
            ),
            httpx.Response(
                503,
                json={"error": "temporarily unavailable"},
            ),
            httpx.Response(
                200,
                json={"status": "ok"},
            ),
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    response = await client.send(make_payload())

    assert response.status_code == 200
    assert len(fake.calls) == 3


@pytest.mark.asyncio
async def test_5xx_exhausts_retries_and_raises():
    fake = FakeAsyncClient(
        [
            httpx.Response(500, json={}),
            httpx.Response(500, json={}),
            httpx.Response(500, json={}),
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    with pytest.raises(BackendClientError):
        await client.send(make_payload())

    assert len(fake.calls) == 3


@pytest.mark.asyncio
async def test_event_id_passthrough_across_retries():
    event_id = "22222222-2222-2222-2222-222222222222"

    fake = FakeAsyncClient(
        [
            httpx.Response(503, json={}),
            httpx.Response(
                200,
                json={"status": "ok"},
            ),
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    await client.send(
        make_payload(event_id=event_id)
    )

    for call in fake.calls:
        assert call["json"]["eventId"] == event_id


@pytest.mark.asyncio
async def test_internal_api_key_header_sent():
    fake = FakeAsyncClient(
        [
            httpx.Response(
                200,
                json={"status": "ok"},
            )
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    await client.send(make_payload())

    assert (
        fake.calls[0]["headers"]["X-Internal-Api-Key"]
        == "test-key"
    )


@pytest.mark.asyncio
async def test_timeout_retries_three_times_and_raises_backend_client_error():
    request = httpx.Request(
        "POST",
        "http://fake-backend:8080/internal/v1/detections",
    )

    fake = FakeAsyncClient(
        [
            httpx.ReadTimeout(
                "timeout",
                request=request,
            ),
            httpx.ReadTimeout(
                "timeout",
                request=request,
            ),
            httpx.ReadTimeout(
                "timeout",
                request=request,
            ),
        ]
    )

    client = BackendDetectionClient(
        _settings(),
        client=fake,
    )

    with pytest.raises(BackendClientError) as exc_info:
        await client.send(make_payload())

    assert exc_info.value.status_code is None
    assert len(fake.calls) == 3