# S-5 Mobile-native UX + Common States

**MODEL: NORMAL**

## Goal

Feature'ları demo-safe mobil-native deneyime getir.
Yeni architecture açma.

## WRITE SCOPE

Yalnız Seda feature presentation klasörleri:
```text
dashboard
camera_management
violations
users
notifications/presentation
```

Onur `app.dart/core/**` sınırını bozma.

## UX rules

- wide table yok
- Card / ListTile
- filter bottom sheet
- create/edit page veya modal sheet
- small-screen safe
- scroll safe
- text scale reasonable
- loading
- empty
- error
- forbidden
- offline

Navigation shell Onur'a ait; feature destination/widget bağlanması yapılabilir.

## Semantic status

ONLINE/CONNECTED ile COMPLETED gibi farklı domain statuslarını tek generic renge indirgeme.

En azından:
- camera connection status
- realtime connection status
- violation lifecycle
- review
- recording

anlamları ayrı kalsın.

## Forbidden

- restricted zone
- live operation video
- camera streaming rewrite

## Tests

- small screen no overflow
- loading/empty/error
- role-sensitive action visibility
- long text/scroll smoke

## Acceptance

```text
flutter analyze
flutter test
```
