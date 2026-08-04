# Camera Gateway Frame Upload

Bu doküman, mobil uygulamanın Camera Ingestion Gateway ile kuracağı
session ve JPEG frame aktarım sözleşmesini açıklar.

## Open session

POST `/api/v1/sessions/open`

Request body:

```json
{
  "cameraId": "camera-1",
  "sessionId": "session-1",
  "sessionToken": "camera-session-token"
}
```

Yeni session oluşturulduğunda `201 Created`, aynı kamera ve session
kısa süreli reconnect yaptığında `200 OK` döner.

## Send heartbeat

POST `/api/v1/sessions/{sessionId}/heartbeat`

Request body:

```json
{
  "cameraId": "camera-1"
}
```

Heartbeat, aktif session'ın bağlantı zamanını günceller.

## Upload JPEG frame

POST `/api/v1/sessions/{sessionId}/frames`

Headers:

- `Content-Type: image/jpeg`
- `X-Camera-Id: camera-1`
- `X-Frame-Timestamp: 2026-08-04T01:00:00Z`

Body:

- Raw JPEG bytes gönderilir.
- `multipart/form-data` kullanılmaz.
- Frame timestamp timezone içermelidir.
- Maksimum frame boyutu Gateway config üzerinden belirlenir.
- Frame aktif session'a ait bounded queue içine alınır.
- Queue doluysa en eski frame düşürülür.

Example:

```bash
curl -X POST \
  "http://localhost:8000/api/v1/sessions/session-1/frames" \
  -H "Content-Type: image/jpeg" \
  -H "X-Camera-Id: camera-1" \
  -H "X-Frame-Timestamp: 2026-08-04T01:00:00Z" \
  --data-binary "@frame.jpg"
```

Successful response:

```json
{
  "accepted": true,
  "cameraId": "camera-1",
  "sessionId": "session-1",
  "capturedAt": "2026-08-04T01:00:00Z",
  "sizeBytes": 182451,
  "queueDepth": 1,
  "queueCapacity": 30,
  "frameCount": 1,
  "droppedFrameCount": 0
}
```

## Close session

POST `/api/v1/sessions/{sessionId}/close`

Request body:

```json
{
  "cameraId": "camera-1"
}
```

Başarılı kapanışta `204 No Content` döner. Tekrarlanan close isteği
idempotent davranır.

## Important constraints

- Mobil görüntüyü yalnızca Gateway'e gönderir.
- Web paneline sürekli canlı yayın gönderilmez.
- Normal kamera akışı diske kaydedilmez.
- Frame içeriği loglanmaz.
- Queue sınırlıdır ve bellekte sınırsız büyümez.
- Session kapandığında bekleyen queue frameleri bellekten temizlenir.
- `sessionToken` örneği temsili değerdir; gerçek token Backend 2
  tarafından üretilip doğrulanacaktır.