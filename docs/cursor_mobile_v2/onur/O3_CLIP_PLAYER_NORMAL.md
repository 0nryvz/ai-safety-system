# O-3 Presigned Clip/Cover Client + Shared Player

**MODEL: NORMAL**

Güçlü modele yalnız video lifecycle veya auth/expiry yarışında non-local sorun çıkarsa geç.

## Goal

Seda'nın violation detail ekranında kullanabileceği tek ortak media bileşeni sağla.

## READ FIRST

```text
mobile/lib/core/network/**
mobile/lib/features/auth/**
mobile/lib/core/theme/strix_brand.dart
mobile/pubspec.yaml

backend/.../ViolationMediaController.java   # READ-ONLY
```

## WRITE SCOPE

```text
mobile/lib/shared/media/**
mobile/lib/core/network/**
mobile/test/shared/media/**
mobile/pubspec.yaml
```

## Endpoints

```text
GET /api/v1/violations/{id}/clip-url
GET /api/v1/violations/{id}/cover-url
```

Response:
```text
url
expiresAt
```

## Required behavior

- READY clip => standard Flutter player
- NOT_READY / 409 => "Klip hazırlanıyor"
- ERROR => error state
- 401 => auth session behavior
- 403 => forbidden
- network => retry action
- URL expire => aynı violationId ile yeni presigned URL iste
- objectKey model/UI/log içinde yok

Public API mümkün olduğunca küçük:
```text
ViolationClipPlayer(violationId, recordingStatus/clipReady...)
```

Seda detail ekranı MinIO veya presigned URL lifecycle bilmek zorunda kalmasın.

## DO NOT

- MinIO SDK
- direct bucket access
- URL'yi permanent storage'a yazma
- objectKey expose etme
- Seda violation page'ini edit etme

## Tests

- READY player loading path
- 409 placeholder
- 403 error
- expired => refetch
- network => retry
- dispose safe

## Acceptance

```text
flutter analyze
flutter test
```
