# PRD v2.0 Mobile — Shared Context

## Source of truth sırası

1. Güncel repo/develop kodu: **gerçek endpoint, DTO ve enum için birinci kaynak**
2. PRD v2.0: **ürün kapsamı için birinci kaynak**
3. `ISG_PRDv2_Mobil_ONUR_SEDA_Hizlandirilmis_Gorev_Plani.pdf`: iş bölümü ve uygulama sırası
4. Eski mobil/Gateway planları: yalnız streaming regression referansı

Bir contract güncel kodla uyuşmuyorsa mobil tarafta yeni endpoint/enum uydurma.

---

## PRD v2.0 mobil hedefi

Mobil artık yalnız kamera kaynağı değildir. Mobil MVP şunları içerir:

- Login / Logout
- Role-aware AppShell / navigation
- Dashboard summary + trend + distribution + recent violations
- Kamera listesi ve ONLINE / WEAK / OFFLINE durumları
- ADMIN kamera create/update/active-passive
- İhlal history/filter/detail/review
- İhlal clip playback
- ADMIN kullanıcı yönetimi
- Bildirim merkezi
- STOMP realtime + reconnect sonrası REST recovery
- Mevcut local camera preview
- Mevcut Gateway frame streaming

---

## Kesinlikle mobilde yapılmayacaklar

- Restricted zone / polygon / reference-image editor
- Web/mobil operasyon ekranında sürekli canlı video
- Ring buffer
- Gateway AI sampling
- AI inference
- FFmpeg
- MinIO upload
- Backend business logic
- FCM/APNs
- Camera/session token mimarisini yeniden tasarlama

---

## Repo başlangıç noktası

Mevcut önemli mobile dosyaları:

```text
mobile/lib/app.dart
mobile/lib/main.dart

mobile/lib/core/config/app_config.dart
mobile/lib/core/device/camera_identity.dart
mobile/lib/core/error/gateway_failure.dart
mobile/lib/core/network/api_client.dart
mobile/lib/core/network/backend_client.dart
mobile/lib/core/theme/strix_brand.dart

mobile/lib/features/camera/**
mobile/lib/features/session/**
mobile/lib/features/streaming/**

mobile/lib/shared/widgets/**
mobile/test/**
```

Mevcut kamera/session/streaming feature'larını yeni operasyon scope'u yüzünden yeniden yazma.

---

## Kod sınırı

PRD v2 mobil feature geliştirmesi için normal yazma alanı:

```text
mobile/**
```

Mobil geliştirme sırasında:

```text
backend/**
camera-gateway/**
ai-service/**
web/**
```

altında kod DEĞİŞTİRME.

Contract sorunu bulunursa ilgili ekip sahibine raporla.

---

## Onur / Seda dosya sahipliği

### Onur ana sahip

```text
mobile/lib/app.dart
mobile/lib/core/network/**
mobile/lib/core/realtime/**
mobile/lib/features/auth/**
mobile/lib/core/models/**   (veya seçilecek ortak model alanı)
mobile/lib/shared/media/**
```

Ayrıca:
- role-aware shell
- auth/session
- STOMP/realtime
- clip/cover media access
- entegrasyon
- release/E2E

### Seda ana sahip

```text
mobile/lib/features/dashboard/**
mobile/lib/features/camera_management/**
mobile/lib/features/violations/**
mobile/lib/features/users/**
mobile/lib/features/notifications/presentation/**
```

Seda, Onur'un core dosyalarını gerekmedikçe değiştirmez.
Onur, Seda'nın ekranlarını görsel olarak refactor etmez.

---

## Mimari davranışlar

### Auth
- Kullanıcı JWT'si operasyon REST + realtime için.
- Gateway demo camera token ayrı kalır.
- 401 = session invalid/yeniden login.
- 403 = yetkisiz; logout yapma.

### Time
- Backend UTC.
- UI local timezone gösterir.
- Filter requestleri backend contractına göre UTC gönderilir.

### Violation statusları ayrı tutulur
- lifecycle
- review
- recording

Bunları tek status alanında birleştirme.

### Realtime
- WebSocket/STOMP `/ws`
- subscribe `/user/queue/alerts`
- JWT CONNECT header
- socket source-of-truth değildir
- reconnect sonrası REST recovery
- duplicate alert üretme

### Media
- objectKey istemciye açılmaz.
- `/clip-url` ve `/cover-url` ile presigned URL alınır.
- READY değilse hazırlama state'i.
- expired URL yeniden istenir.

### Existing streaming
- automatic reconnect aynı sessionId
- manual Stop -> Start yeni UUID
- manual Stop sonrası reconnect timer çalışmaz
- streaming yalnız Gateway'e gider
