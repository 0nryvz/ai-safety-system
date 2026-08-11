# Internal Recording Commands Contract

Bu doküman, Spring backend tarafından çağrılan Gateway internal recording komut endpointlerinin sözleşmesini tanımlar.

## Base path

- `/internal/v1/recordings`

## Start command

- **Method/Path:** `POST /internal/v1/recordings/commands/start`
- **Başarılı ACK:** `202 Accepted`

Request JSON:

```json
{
  "command_id": "8c69416d-6eb1-4a29-8ca2-581db8cd9b6f",
  "violation_id": "f28703fd-a609-42d1-a4de-2f3100963f61",
  "camera_id": "67547ea9-654b-4ff4-9dfd-adf9ab95ec5d",
  "session_id": "55889f88-fd7c-4e2e-8de9-c1dc601ff025",
  "started_at": "2026-01-01T10:00:00Z",
  "pre_buffer_seconds": 5,
  "post_buffer_seconds": 5,
  "max_clip_seconds": 30
}
```

Davranış:

- İlk geçerli START, violation için active state oluşturur ve `idempotent=false` ile ACK döner.
- Aynı `command_id` ile duplicate START idempotent kabul edilir ve `idempotent=true` döner.
- Aynı violation için farklı `command_id` ile ikinci active START `409 RECORDING_START_CONFLICT` döner.
- Session bulunamazsa `404 SESSION_NOT_FOUND` döner.
- Camera/session ownership uyuşmazsa `409 SESSION_CONFLICT` döner.

## Stop command

- **Method/Path:** `POST /internal/v1/recordings/commands/stop`
- **Başarılı ACK:** `202 Accepted`

Request JSON:

```json
{
  "command_id": "4f5de0eb-d4fc-4eb1-b9ce-4945945320fc",
  "violation_id": "f28703fd-a609-42d1-a4de-2f3100963f61",
  "ended_at": "2026-01-01T10:00:12Z"
}
```

Davranış:

- Daha önce START alınmış violation için ilk geçerli STOP kabul edilir ve `idempotent=false` döner.
- Aynı `command_id` ile duplicate STOP idempotent kabul edilir ve `idempotent=true` döner.
- Violation için START yoksa `404 RECORDING_NOT_FOUND_FOR_VIOLATION` döner.
- Çelişkili STOP isteği `409 RECORDING_STOP_CONFLICT` döner.

## ACK response

`202` durumunda body:

```json
{
  "command_id": "8c69416d-6eb1-4a29-8ca2-581db8cd9b6f",
  "violation_id": "f28703fd-a609-42d1-a4de-2f3100963f61",
  "idempotent": false
}
```

## Auth notu

Bu endpointler için repo içinde net bir service-to-service auth standardı bu aşamada tanımlı/bağlı değildir. Bu nedenle yeni header/token uydurulmamış, auth entegrasyonu sonraki aşama için TODO olarak bırakılmıştır.