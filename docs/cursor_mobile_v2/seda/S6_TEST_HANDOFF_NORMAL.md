# S-6 Feature Tests + Handoff

**MODEL: NORMAL**

## Goal

Seda branchini integration için temiz teslim et.

## Test scope

```text
mobile/test/features/dashboard/**
mobile/test/features/camera_management/**
mobile/test/features/violations/**
mobile/test/features/users/**
mobile/test/features/notifications/**
```

## Minimum scenarios

### Dashboard
- success
- empty
- error

### Cameras
- list
- statuses
- admin visibility
- 403

### Violations
- filters
- detail
- status separation
- review version
- conflict

### Users
- admin visibility
- create validation
- update

### Notifications
- render
- update same item
- dismiss local
- detail navigation

## Commands

```text
flutter analyze
flutter test
```

Gerekirse feature bazında:
```text
flutter test test/features/<feature>
```

## Handoff note format

Cursor şu raporu üretsin:

```text
FEATURES COMPLETED:
FILES CHANGED:
ENDPOINTS USED:
CORE FILES TOUCHED:
TESTS:
KNOWN CONTRACT DRIFT:
KNOWN UI DEBT:
READY TO MERGE: yes/no
```

`CORE FILES TOUCHED` boş olmalı veya Onur ile önceden anlaşılmış olmalı.
