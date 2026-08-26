# O-0 Contract Seed + App Skeleton

**MODEL: STRONG**

## Goal

İki kişinin paralel çalışabileceği minimum ortak mobil çekirdeği kur:
- auth session boundary
- authenticated API boundary
- common error/model boundary
- role-aware AppShell placeholder
- mevcut CameraPage'i koru

Bu adım feature implementasyonu değildir; sınırları sabitleyen küçük seed commitidir.

## READ FIRST

```text
mobile/lib/app.dart
mobile/lib/main.dart
mobile/lib/core/config/app_config.dart
mobile/lib/core/network/backend_client.dart
mobile/lib/features/session/operator_login_page.dart
mobile/lib/features/session/camera_selection_page.dart
mobile/lib/features/camera/camera_page.dart
mobile/pubspec.yaml
mobile/test/core/network/backend_client_test.dart
```

Gerekirse yalnız auth/user DTO'larını doğrulamak için backend controller/DTO dosyalarını READ-ONLY incele.

## WRITE SCOPE

```text
mobile/lib/app.dart
mobile/lib/core/network/**
mobile/lib/core/models/**            # seçilirse
mobile/lib/features/auth/**          # skeleton/provider boundary
mobile/lib/shared/**                 # yalnız ortak nav/error skeleton gerekirse
mobile/test/**
```

`features/camera`, `features/session`, `features/streaming` içine gereksiz yazma.

## Required design

### AuthSession

En az:
```text
accessToken
refreshToken
currentUser
roles
departmentIds
authenticated
```

Implementation Riverpod ile yapılabilir; repo zaten flutter_riverpod içeriyor.

### API boundary

Mevcut `BackendClient` kırılmadan:
- ya authenticated generic client'a evrilmeli
- ya yanında tek merkezi authenticated client oluşturulmalı

Her feature kendi `http.Client`/baseUrl sistemini kurmamalı.

### Error mapping

Minimum:
```text
unauthenticated / 401
forbidden / 403
validation
network/unreachable
server
conflict
unknown
```

### AppShell placeholder

Navigation entries:
```text
Dashboard
Kameralar
İhlaller
Bildirimler
Kullanıcılar (ADMIN only)
Kamera Yayını
```

Feature'lar henüz yoksa placeholder page kabul.

Existing CameraPage silinmeyecek.

## DO NOT

- Yeni routing dependency eklemek zorunlu değil.
- Streaming controller refactor etme.
- Gateway tokenını user JWT ile birleştirme.
- Secure storage mimarisi açma.
- Backend endpoint uydurma.
- Seda feature klasörlerini implement etme.

## STOP CONDITIONS

Şunlardan biri varsa kod uydurmadan dur:
- current user contract güncel backendde farklı
- auth response alanları farklı
- mevcut login akışı seed ile çakışıyor ve davranış net değil

## Acceptance

```text
flutter analyze
flutter test
```

Ayrıca:
- auth yoksa login route
- auth varsa shell state tanımlı
- CameraPage erişilebilir
- Seda core dosyasına dokunmadan yeni feature page ekleyebiliyor

## Output at end

```text
CHANGED:
CONTRACT CREATED:
TESTS:
SEDA CAN START: yes/no
RISKS:
```
