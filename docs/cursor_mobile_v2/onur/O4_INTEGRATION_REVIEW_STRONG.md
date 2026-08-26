# O-4 Mobile Feature Integration + Contract Review

**MODEL: STRONG**

## Goal

Seda feature branchini core/auth/realtime/media katmanıyla birleştir.
Yeni feature yazmaktan çok contract drift ve cross-feature hata bul.

## READ FIRST

Önce git diff + changed files.

Sonra yalnız:
```text
mobile/lib/app.dart
mobile/lib/core/**
mobile/lib/features/auth/**
mobile/lib/features/dashboard/**
mobile/lib/features/camera_management/**
mobile/lib/features/violations/**
mobile/lib/features/users/**
mobile/lib/features/notifications/**
mobile/lib/shared/media/**
```

## WRITE SCOPE

Integration conflict gerektirdiği kadar:
```text
mobile/**
```

Ancak görsel refactor yapma.

## Review checklist

### Auth
- bütün REST calls merkezi Bearer client kullanıyor mu?
- feature kendi baseUrl/http clientını açmış mı?
- 401/403 doğru mu?

### Camera
- response alanı `status`
- values ONLINE/WEAK/OFFLINE
- restricted-zone mobilde yok

### Violations
- lifecycle/review/recording ayrı
- current lifecycle `PREPARING`
- backendde olmayan jacket enum hardcode edilmiş mi?
- filter UTC doğru mu?
- review `version` içeriyor mu?

### Dashboard
- trend bucket yalnız canonical backend değeri
- groupBy uydurma yok

### Realtime
- notification UI store tüketiyor
- duplicate card yok
- offline eski state'i silmiyor

### Media
- objectKey yok
- clip player shared component
- direct MinIO yok

### Navigation
- ADMIN Users
- CameraPage hâlâ erişilebilir
- live operation video yok
- restricted-zone yok

## Contract drift policy

Bir mismatch:
- mobil typo ise düzelt
- backend contract eksikse backend koduna dokunma
- PRD vs backend farkını ayrı blocker olarak raporla

## Tests

```text
flutter analyze
flutter test
```

Sonra feature smoke:
- login
- dashboard
- cameras
- violation detail
- users admin
- notifications
- camera streaming page open

## Output

```text
MOBILE BUGS FIXED:
BACKEND CONTRACT BLOCKERS:
PRD DRIFT:
TESTS:
READY FOR O5: yes/no
```
