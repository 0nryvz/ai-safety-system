# AI Worker (`ai-service`)

Gateway'den gelen JPEG kareleri alan, YOLO modeli ile inference çalıştıran ve
normalize edilmiş detection sonuçlarını Spring Boot Backend'e gönderen FastAPI servisidir.

## Sistem Akışı

```text
Gateway
  |
  | POST /internal/v1/inference/frames
  | JPEG + camera/session/timestamp/eventId
  v
AI Worker
  |
  | YOLO inference
  | class mapping
  | bbox normalization
  v
Spring Boot Backend
  |
  | POST /internal/v1/detections
  v
Violation Engine
```

## Mevcut Durum

- [x] Adım 0 - Model artifact teslimi
- [x] Adım 1 - FastAPI servis iskeleti ve `/health`
- [x] Adım 2 - Gerçek YOLO inference adapter
- [x] Adım 3 - Gateway -> AI inference endpoint
- [x] Adım 4 - AI -> Backend detection client
- [x] 9-class mapping
- [x] BBox normalize/clamp edge-case düzeltmeleri
- [x] Backend 4xx/5xx hata propagation
- [x] Unit/integration testleri
- [x] Dockerfile
- [x] Root `docker-compose.yml` içine `ai-service` tanımı
- [ ] Gerçek ACTIVE camera/session ile AI -> Backend `202 Accepted` smoke testi
- [ ] Gerçek Gateway -> AI -> Backend E2E testi
- [ ] 3 FPS / uzun süreli performans doğrulaması
- [ ] Docker runtime doğrulaması

Gerçek E2E testleri için Backend tarafında kayıtlı kamera ve ACTIVE session gereklidir.

## Model

Model:

```text
models/best.pt
```

Model versiyonu:

```text
ppe-yolo26s-300ep-v1
```

Model özellikleri:

```text
Ultralytics YOLO26s
imgsz = 640
300 epoch
9 class
```

Class listesi:

```text
Person
gloves
non_gloves
non_welding_jacket
non_welding_mask
welding
welding_apron
welding_jacket
welding_mask
```

Backend mapping:

```text
Person              -> person
gloves              -> gloves
non_gloves          -> non_gloves
non_welding_jacket  -> non_welding_jacket
non_welding_mask    -> non_welding_mask
welding             -> welding
welding_apron       -> welding_apron
welding_jacket      -> welding_jacket
welding_mask        -> welding_mask
```

`non_*` classları drop edilmez. Backend violation kuralları tarafından kullanılmaktadır.

Model ağırlığı Git'e commit edilmez.

Docker çalıştırmada model:

```text
./ai-service/models/best.pt
```

host path'inden container içindeki:

```text
/models/best.pt
```

path'ine read-only volume olarak mount edilir.

## Environment

Örnek config:

```text
ai-service/.env.example
```

Temel değişkenler:

```env
AI_MODEL_PATH=./models/best.pt
AI_MODEL_VERSION=ppe-yolo26s-300ep-v1
AI_MODEL_DEVICE=cpu

CONFIDENCE_THRESHOLD=0.50
IOU_THRESHOLD=0.45

BACKEND_BASE_URL=http://localhost:8080
BACKEND_DETECTIONS_PATH=/internal/v1/detections
INTERNAL_API_KEY=change-me-local-internal-key

AI_CLASS_MAPPING_PATH=config/class_mapping.json
```

Local çalıştırmada Backend:

```text
http://localhost:8080
```

Docker container içinden host makinedeki Backend:

```text
http://host.docker.internal:8080
```

olarak kullanılır.

## Windows'ta Local Çalıştırma

```powershell
cd ai-service

.\.venv\Scripts\Activate.ps1

uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Health kontrolü:

```powershell
curl.exe http://127.0.0.1:8001/health
```

Model başarıyla yüklenmişse:

```json
{
  "status": "ok",
  "service": "ai-worker",
  "model": {
    "loaded": true,
    "version": "ppe-yolo26s-300ep-v1",
    "device": "cpu",
    "error": null
  }
}
```

## Testler

AI Worker testleri:

```powershell
cd ai-service
.\.venv\Scripts\Activate.ps1
pytest -v
```

Testlerde temel olarak şunlar doğrulanır:

- JPEG validation
- model loaded / not-loaded davranışı
- bbox normalization
- frame dışındaki geçersiz bbox davranışı
- 9-class mapping
- `non_*` classlarının korunması
- eventId passthrough
- Backend authentication header
- Backend 4xx davranışı
- Backend 5xx retry davranışı
- Backend failure propagation

## Docker

Root dizinden:

```powershell
docker compose config
```

AI Worker image build:

```powershell
docker compose build ai-service
```

Container başlatma:

```powershell
docker compose up -d ai-service
```

Durum:

```powershell
docker compose ps
```

Log:

```powershell
docker logs isg-ai-service
```

Health:

```powershell
curl.exe http://127.0.0.1:8001/health
```

Docker içindeki model path:

```text
/models/best.pt
```

AI Worker portu:

```text
8001
```

## Backend Hata Davranışı

Beklenen sözleşme:

```text
Backend 2xx
-> AI Worker 202

Backend 4xx
-> AI Worker aynı 4xx

Backend 5xx / connection failure
-> AI Worker bounded retry
-> retry başarısızsa AI Worker 502
```

Bu davranış Gateway retry sözleşmesiyle uyumludur.

## Camera / Session Notu

Backend detection endpoint'i kayıtlı bir kamera ve ACTIVE session bekler.

AI requestinde:

```text
cameraId = cameras.id
sessionId = camera_sessions.session_id
```

kullanılmalıdır.

`camera_sessions.id` gönderilmemelidir.

Geçerli camera/session çifti olmadan Backend:

```text
404 Camera or session not found
```

döndürebilir.

Geçerli ACTIVE session ile başarılı detection request'i için beklenen Backend cevabı:

```text
202 Accepted
```

## Kalan Entegrasyon Testleri

Gerçek kamera/session akışı hazır olduğunda:

```text
Gateway
-> AI Worker :8001
-> gerçek YOLO inference
-> Backend /internal/v1/detections
-> 202 Accepted
```

akışı doğrulanacaktır.

Ardından 3 FPS ve uzun süreli E2E/performance testi tamamlanacaktır.