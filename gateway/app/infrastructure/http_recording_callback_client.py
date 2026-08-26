import httpx

from app.services.recording_callback_client import (
    RecordingCallbackClient,
    RecordingCallbackError,
    RecordingCallbackPayload,
)


class HttpRecordingCallbackClient(RecordingCallbackClient):
    def __init__(
            self,
            *,
            backend_base_url: str,
            internal_api_key: str,
            timeout_seconds: float = 5.0,
            http_client: httpx.AsyncClient | None = None,
    ) -> None:
        self._internal_api_key = internal_api_key
        self._callback_url = (
            f"{backend_base_url.rstrip('/')}"
            "/internal/v1/recordings/callback"
        )

        if http_client is None:
            self._http_client = httpx.AsyncClient(
                timeout=timeout_seconds,
            )
            return

        self._http_client = http_client

    async def send_callback(
            self,
            payload: RecordingCallbackPayload,
    ) -> None:
        request_json = _build_callback_json(payload)

        try:
            response = await self._http_client.post(
                self._callback_url,
                json=request_json,
                headers={
                    "X-Internal-Api-Key": self._internal_api_key,
                },
            )
        except httpx.TimeoutException as ex:
            raise RecordingCallbackError(
                "Recording callback request timed out",
                retryable=True,
            ) from ex
        except httpx.RequestError as ex:
            raise RecordingCallbackError(
                "Recording callback request failed",
                retryable=True,
            ) from ex

        if 200 <= response.status_code < 300:
            return

        retryable = response.status_code >= 500

        raise RecordingCallbackError(
            "Recording callback failed "
            f"with status={response.status_code}",
            retryable=retryable,
        )


def _build_callback_json(
        payload: RecordingCallbackPayload,
) -> dict[str, object]:
    callback_json: dict[str, object] = {
        "recordingId": payload.recording_id,
        "violationId": payload.violation_id,
        "status": payload.status,
        "retryCount": payload.retry_count,
    }

    if payload.status == "READY":
        callback_json.update(
            {
                "objectKey": payload.object_key,
                "durationMs": payload.duration_ms,
                "sizeBytes": payload.size_bytes,
                "checksum": payload.checksum,
            }
        )

        if (
                payload.cover_image_key is not None
                and payload.cover_image_key.strip()
        ):
            callback_json["coverImageKey"] = (
                payload.cover_image_key
            )

        return callback_json

    if payload.status == "ERROR":
        callback_json["errorCode"] = payload.error_code
        return callback_json

    raise RecordingCallbackError(
        f"Unsupported recording callback status={payload.status}"
    )
