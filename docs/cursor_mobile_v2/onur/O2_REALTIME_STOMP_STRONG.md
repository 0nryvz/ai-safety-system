# O-2 Mobile Realtime STOMP + Dedupe + REST Recovery

**MODEL: STRONG**

Bu paket içindeki en reasoning-heavy görevlerden biridir.

## Goal

Backend `/ws` STOMP hattına tek bağlantı ile bağlan.
`/user/queue/alerts` eventlerini duplicate-safe store'a aktar.
Reconnect sonrası REST recovery başlat.

## READ FIRST

```text
mobile/lib/app.dart
mobile/lib/features/auth/**
mobile/lib/core/network/**
mobile/pubspec.yaml

backend/src/main/java/com/isg/backend/violation/config/WebSocketConfig.java
backend/src/main/java/com/isg/backend/violation/config/WebSocketJwtChannelInterceptor.java
backend/src/main/java/com/isg/backend/violation/application/notification/AlertMessage.java
backend/src/main/java/com/isg/backend/violation/application/notification/ViolationUpdateMessage.java
```

Backend dosyaları READ-ONLY.

## WRITE SCOPE

```text
mobile/lib/core/realtime/**
mobile/lib/features/notifications/data/**
mobile/lib/core/models/**
mobile/test/core/realtime/**
mobile/test/features/notifications/**
mobile/pubspec.yaml      # yalnız gerekli STOMP dependency için
```

## Protocol

```text
WebSocket endpoint: /ws
STOMP CONNECT header:
Authorization: Bearer <accessToken>

SUBSCRIBE:
/user/queue/alerts
```

Raw web_socket_channel tek başına yeterli değil.
Uygunsa `stomp_dart_client` eklenebilir.
Yeni dependency eklemeden minimum adapter daha güvenliyse gerekçelendir.

## Required state

Connection:
```text
CONNECTING
CONNECTED
RECONNECTING
OFFLINE
```

Tek login session için tek socket.

Logout:
- socket kapanır
- reconnect timer/task iptal olur

## Event parsing

Current known `AlertMessage` ve `ViolationUpdateMessage` contractını kullan.

Unknown/malformed event:
- app crash etmez
- güvenli parse failure
- mümkünse diagnostic

## Dedupe

Backend snapshotta zorunlu eventId görünmüyor.

Priority:
1. Gerçek payloadta eventId/version varsa onu kullan.
2. Yoksa deterministic fallback kullan.

Fallback örneği:
```text
violationId + messageKind + lifecycleStatus + recordingStatus + clipReady + effectiveTimestamp
```

Aynı violation için update:
- yeni duplicate card üretmez
- mevcut entity state'ini update eder

## REST recovery

Socket source-of-truth değildir.

Reconnect sonrası minimum recovery hook/callback:
- active/recent violation state
- dashboard/relevant state
- gerekirse camera status

Bu görevde tüm feature REST ekranını yazma.
Core yalnız recovery trigger/interface sağlamalı.

## Backoff

- bounded
- tek timer
- logout/manual disconnect sonrası tekrar açılmaz
- aynı anda iki socket oluşmaz

## DO NOT

- FCM/APNs
- browser CORS workaround
- backend websocket config değişikliği
- required eventId uydurmak
- socket eventlerini kalıcı DB source-of-truth gibi kullanmak

## Tests

- CONNECT uses Bearer header
- subscribe correct destination
- single active connection
- malformed event ignored safely
- duplicate alert => one item
- update => same violation item changes
- disconnect => reconnect state
- reconnect => recovery callback
- logout => no reconnect
- token/session change => old socket closed

## Acceptance

```text
flutter analyze
flutter test
```

Gerçek backend smoke:
```text
login
STOMP connect
subscribe
gerçek MESSAGE
```

Backend notification hattı hazır değilse fake STOMP testleriyle kodu tamamla; final smoke'u dependency ready olunca yap.
