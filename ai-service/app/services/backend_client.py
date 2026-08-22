"""
Adım 4: AI Worker -> Spring Boot backend (/internal/v1/detections) gönderimi.

Hata davranışı (görev planı):
- Backend geçici 5xx verirse bounded retry uygulanır; aynı eventId korunur.
- Backend 4xx (contract/auth) hatası retry fırtınasına çevrilmez; anlamlı log üretilir.
"""
from __future__ import annotations

import logging

import httpx

from app.core.config import Settings
from app.schemas.detection import DetectionRequest

logger = logging.getLogger(__name__)


class BackendClientError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class BackendDetectionClient:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self._settings = settings
        self._client = client or httpx.AsyncClient(
            timeout=settings.backend_request_timeout_seconds
        )
        self._max_retries = 3

    async def send(self, payload: DetectionRequest) -> httpx.Response:
        """
        Aynı eventId ile bounded retry yapar. Sadece 5xx / bağlantı hatalarında
        retry eder; 4xx'te (contract/auth) hemen fırlatıp durur.
        """
        url = self._settings.backend_detections_url
        headers = {"X-Internal-Api-Key": self._settings.internal_api_key or ""}
        body = payload.model_dump(mode="json", by_alias=True)

        last_exc: Exception | None = None

        for attempt in range(1, self._max_retries + 1):
            try:
                response = await self._client.post(
                    url,
                    json=body,
                    headers=headers,
                )
            except httpx.RequestError as exc:
                last_exc = exc
                logger.warning(
                    "Backend'e bağlanılamadı (deneme %s/%s) eventId=%s: %s",
                    attempt,
                    self._max_retries,
                    payload.event_id,
                    exc,
                )
                continue

            if response.status_code < 300:
                return response

            if 400 <= response.status_code < 500:
                # Contract/auth hatası: retry fırtınasına çevrilmez, hemen durur.
                logger.error(
                    "Backend contract/auth hatası eventId=%s status=%s body=%s",
                    payload.event_id,
                    response.status_code,
                    response.text,
                )
                raise BackendClientError(
                    f"Backend {response.status_code} döndü",
                    response.status_code,
                )

            # 5xx: bounded retry
            logger.warning(
                "Backend geçici hata (deneme %s/%s) eventId=%s status=%s",
                attempt,
                self._max_retries,
                payload.event_id,
                response.status_code,
            )
            last_exc = BackendClientError(
                f"Backend {response.status_code} döndü",
                response.status_code,
            )

        if isinstance(last_exc, BackendClientError):
            raise last_exc

        if last_exc is not None:
            raise BackendClientError(
                "Backend'e ulaşılamadı",
                status_code=None,
            ) from last_exc

        raise BackendClientError(
            "Backend'e ulaşılamadı",
            status_code=None,
        )

    async def aclose(self) -> None:
        await self._client.aclose()