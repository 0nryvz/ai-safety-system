from fastapi.testclient import TestClient

from app.main import app

HEADERS = {
    "X-Camera-Id": "cam-1",
    "X-Session-Id": "sess-1",
    "X-Frame-Timestamp": "2026-08-18T10:00:00Z",
    "X-Frame-Event-Id": "11111111-1111-1111-1111-111111111111",
    "Content-Type": "image/jpeg",
}


def test_missing_headers_returns_422():
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/inference/frames",
            content=b"\xff\xd8\xff\xe0fakejpeg",
            headers={"Content-Type": "image/jpeg"},
        )
        assert response.status_code == 422


def test_wrong_content_type_returns_415():
    with TestClient(app) as client:
        headers = {**HEADERS, "Content-Type": "application/octet-stream"}
        response = client.post(
            "/internal/v1/inference/frames", content=b"not-a-jpeg", headers=headers
        )
        assert response.status_code == 415


def test_empty_body_returns_400():
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/inference/frames", content=b"", headers=HEADERS
        )
        assert response.status_code == 400


def test_model_not_loaded_returns_503():
    # Adım 0/2 tamamlanana kadar model her zaman yüklenmemiş durumda,
    # bu yüzden endpoint şu an için 503 dönmeli (kontrollü hata).
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/inference/frames",
            content=b"\xff\xd8\xff\xe0fakejpeg",
            headers=HEADERS,
        )
        assert response.status_code == 503


def test_event_id_passthrough_when_model_loaded():
    # Model yüklenmiş gibi simüle edip (predict() mock'lanır - gerçek .pt
    # dosyası olmadan da endpoint kontratı test edilebilir) eventId'nin
    # aynen döndüğünü ve backend'e gönderim yapıldığını doğrula.
    from app.services.model_runner import Detection, InferenceResult

    fake_result = InferenceResult(
        detections=[
            Detection(
                label="Person",  # config/class_mapping.json -> "person"
                confidence=0.91,
                bbox_x=0.1,
                bbox_y=0.1,
                bbox_width=0.3,
                bbox_height=0.5,
            )
        ],
        inference_ms=12.3,
    )

    with TestClient(app) as client:
        model_runner = app.state.model_runner
        backend_client = app.state.backend_client
        model_runner._loaded = True  # test-only stub
        original_predict = model_runner.predict
        original_send = backend_client.send
        model_runner.predict = lambda jpeg_bytes: fake_result  # type: ignore[method-assign]

        async def fake_send(payload):
            return None

        backend_client.send = fake_send  # type: ignore[method-assign]
        try:
            response = client.post(
                "/internal/v1/inference/frames",
                content=b"\xff\xd8\xff\xe0fakejpeg",
                headers=HEADERS,
            )
            assert response.status_code == 202
            assert response.json()["eventId"] == HEADERS["X-Frame-Event-Id"]
        finally:
            model_runner._loaded = False
            model_runner.predict = original_predict
            backend_client.send = original_send


def test_unsupported_label_returns_400():
    # Model class_mapping.json'da olmayan bir label üretirse 400 dönmeli.
    from app.services.model_runner import Detection, InferenceResult

    fake_result = InferenceResult(
        detections=[
            Detection(
                label="visor",  # backend'in KABUL ETMEDİĞİ / mapping'de olmayan bir class
                confidence=0.8,
                bbox_x=0.1,
                bbox_y=0.1,
                bbox_width=0.2,
                bbox_height=0.2,
            )
        ],
        inference_ms=5.0,
    )

    with TestClient(app) as client:
        model_runner = app.state.model_runner
        model_runner._loaded = True
        original_predict = model_runner.predict
        model_runner.predict = lambda jpeg_bytes: fake_result  # type: ignore[method-assign]
        try:
            response = client.post(
                "/internal/v1/inference/frames",
                content=b"\xff\xd8\xff\xe0fakejpeg",
                headers=HEADERS,
            )
            assert response.status_code == 400
        finally:
            model_runner._loaded = False
            model_runner.predict = original_predict
