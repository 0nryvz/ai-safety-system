from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health_returns_service_status() -> None:
    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "UP"
    assert body["service"] == "camera-ingestion-gateway"
    assert body["version"] == "0.1.0"
    assert body["environment"] == "local"
    assert "timestamp" in body