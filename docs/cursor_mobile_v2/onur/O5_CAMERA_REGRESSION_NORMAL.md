# O-5 Existing Camera/Gateway Regression Gate

**MODEL: NORMAL**

Bu bir yeni feature görevi değildir.

Hata cross-service/session race ise güçlü modele geç.

## Goal

PRD v2 operasyon ekranlarının mevcut kamera/Gateway hattını bozmadığını kanıtla.

## READ FIRST

```text
mobile/lib/features/camera/**
mobile/lib/features/session/**
mobile/lib/features/streaming/**
mobile/lib/core/config/app_config.dart
mobile/lib/core/network/api_client.dart
mobile/android/app/src/main/AndroidManifest.xml
mobile/android/app/src/main/res/xml/network_security_config.xml
mobile/test/features/session/**
mobile/test/features/streaming/**
```

## WRITE SCOPE

Normalde YOK.

Yalnız gerçek regression bulunursa:
```text
mobile/**
```

Regression kanıtlanmadan refactor etme.

## Verify

### Build
```text
flutter analyze
flutter test
flutter build apk --release
```

### Real Android

- APK install
- HTTP/Tailscale Gateway health erişimi
- backend login
- real camera selection
- Gateway session open
- heartbeat
- JPEG frame upload
- manual close

### Session identity
1. manual Start => A
2. network loss => reconnect => A
3. second reconnect => A
4. manual Stop -> Start => B
5. B != A
6. manual Stop sonrası reconnect timer yok

### Stability
15 dakika stream:
- crash yok
- preview kullanılabilir
- stale backlog yok

### App kill/network loss
Gateway metrics:
- active session cleanup
- queue cleanup
- ring buffer cleanup
- worker cleanup
- ghost session yok

## DO NOT

- başarılı eski streaming kodunu optimize etme
- token architecture açma
- Gateway feature yazma

## Output

```text
RELEASE APK:
REAL DEVICE:
SESSION IDENTITY:
15 MIN:
APP KILL CLEANUP:
REGRESSION FOUND:
READY FOR O6:
```
