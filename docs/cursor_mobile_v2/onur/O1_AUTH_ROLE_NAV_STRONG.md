# O-1 Auth / Refresh / Logout / Role-aware Navigation

**MODEL: STRONG**

## Goal

Operasyon ekranları için gerçek backend JWT session'ını merkezi hale getir.

## READ FIRST

```text
00_SHARED_CONTEXT.md
01_CONTRACT_SNAPSHOT.md
mobile/lib/app.dart
mobile/lib/core/network/**
mobile/lib/features/auth/**
mobile/lib/features/session/operator_login_page.dart
mobile/lib/features/session/camera_selection_page.dart
mobile/test/core/network/**
```

## WRITE SCOPE

```text
mobile/lib/app.dart
mobile/lib/core/network/**
mobile/lib/features/auth/**
mobile/lib/core/models/**
mobile/test/core/**
mobile/test/features/auth/**
```

## Required behavior

1. Login:
   - `POST /api/v1/auth/login`
   - accessToken + refreshToken + tokenType

2. Current user:
   - `GET /api/v1/users/me`
   - roles + departmentIds dahil

3. Authenticated requests:
   - `Authorization: Bearer <accessToken>`

4. Refresh:
   - `POST /api/v1/auth/refresh`
   - aynı anda çoklu 401 gelirse refresh storm üretme
   - mümkünse single-flight refresh davranışı

5. Logout:
   - `POST /api/v1/auth/logout` refreshToken ile
   - local session temizle
   - realtime daha sonra aynı session lifecycle'a bağlanabilecek

6. 401:
   - refresh mümkünse dene
   - refresh fail ise session temizle/login
   - sonsuz retry yok

7. 403:
   - logout YAPMA
   - merkezi forbidden state/error

8. Role navigation:
   - ADMIN: Users + camera management actions
   - OHS_SPECIALIST / SHIFT_SUPERVISOR: backend yetkisi esas
   - UI visibility güvenlik yerine geçmez

## Token storage

MVP için memory-only kabul.
Bu görevde secure_storage ekleme.

## DO NOT

- Gateway `CAMERA_KEY` akışını user JWT ile değiştirme.
- Backend security koduna dokunma.
- Permission kurallarını clientta backendden daha güçlü varsayma.
- Seda ekranlarını implement etme.

## Tests

Minimum:
- valid login
- invalid credentials
- `/users/me` parse
- Bearer injection
- 401 -> refresh success
- 401 -> refresh fail -> logout
- 403 -> session korunuyor
- ADMIN nav görünür
- non-admin Users görünmez
- logout session temizler

## Acceptance

```text
flutter analyze
flutter test
```

## Escalation

Refresh/concurrent request yarışında test nondeterministic olursa güçlü modelde kal.
Mechanical test fixture/JSON işleri normal modele devredilebilir.
