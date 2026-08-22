# O-6 Final Mobile -> Full System E2E Release Gate

**MODEL: STRONG**

Bu görevde güçlü model kod yazmaktan çok **teşhis ve release kararını** yönetmeli.

## Goal

Tek gerçek demo zincirini kanıtla:

```text
Android phone
-> Gateway
-> real AI Worker
-> Backend detection
-> temporal violation
-> Recorder
-> FFmpeg
-> MinIO
-> Backend READY
-> mobile alert/detail/clip
```

## Rule

İlk hata görüldüğünde rastgele kod değiştirme.

Önce zincirin hangi segmentinde kırıldığını belirle:
1. mobile
2. Gateway ingestion
3. Gateway -> AI
4. AI -> Backend
5. violation engine
6. recorder
7. MinIO/callback
8. notification
9. mobile REST/media

İlgili ekip dışı kodu mobil görevi içinde değiştirme.

## Canonical runtime

```text
Backend: 8080
Gateway: 8000
AI Worker: 8001
MinIO: 9000
PostgreSQL: 5432
```

## Mobile acceptance

- login
- current user
- dashboard
- cameras
- violations
- notifications
- ADMIN users
- role-aware navigation
- CameraPage streaming

## Event acceptance

- real violation oluşur
- mobil alert gelir
- recorder READY olur
- clipReady update aynı violationı günceller
- detail açılır
- presigned clip telefonda oynar

## Resilience acceptance

- socket loss => old state stays
- socket reconnect => REST recovery
- Gateway network loss => same stream sessionId reconnect
- manual stop => no reconnect
- app kill => Gateway cleanup

## Storage acceptance

- violation COMPLETED
- recording READY
- MP4 exists
- cover exists
- private object access yalnız presigned route

## Build/release

```text
flutter analyze
flutter test
flutter build apk --release
```

Backend/Gateway suites mevcut proje release planına göre çalıştırılır.

## Output

Cursor sadece şu formatla final rapor üretsin:

```text
PASS:
FAIL:
BLOCKER OWNER:
EVIDENCE:
RETEST COMMAND:
MVP MOBILE RELEASE: GO / NO-GO
```
