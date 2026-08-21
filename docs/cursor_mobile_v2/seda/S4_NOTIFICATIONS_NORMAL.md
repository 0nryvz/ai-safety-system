# S-4 Notifications Center UI

**MODEL: NORMAL**

Realtime protocol/store Onur'un alanıdır.
Bu görev yalnız presentation.

## Goal

Onur realtime notification store'unu kullanıcıya anlaşılır şekilde göster.

## READ FIRST

```text
mobile/lib/core/realtime/**
mobile/lib/features/notifications/data/**
mobile/lib/features/violations/**
mobile/lib/core/theme/strix_brand.dart
```

## WRITE SCOPE

```text
mobile/lib/features/notifications/presentation/**
mobile/test/features/notifications/presentation/**
```

## UI card

Göster:
```text
violation type
cameraName
departmentName
startedAt
confidence
lifecycleStatus
recordingStatus
clipReady
```

## Behavior

- same violation update => same card
- UI duplicate yaratma
- tap => violation detail
- dismiss => LOCAL ONLY
- backend delete/review çağrısı YOK
- offline/reconnecting visible
- old notifications remain when socket offline
- loading/empty/error/offline distinct

## DO NOT

- STOMP client edit etme
- socket lifecycle yazma
- server dismissal endpoint uydurma
- FCM/APNs

## Tests

- render alert
- update same card
- local dismiss
- tap detail
- offline retains data
- reconnect badge

## Acceptance

```text
flutter analyze
flutter test
```
