from fastapi.testclient import TestClient

from app.main import app


def test_health_returns_200_and_model_status():
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"
        assert "model" in body
        assert "loaded" in body["model"]
