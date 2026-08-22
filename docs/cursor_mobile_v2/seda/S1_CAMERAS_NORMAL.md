# S-1 Cameras — List, Status, ADMIN CRUD

**MODEL: NORMAL**

## Goal

Operasyon kamera yönetimini mobilde sun.

## READ FIRST

```text
mobile/lib/core/network/**
mobile/lib/features/camera/**      # streaming feature sadece ilişkiyi anlamak için
mobile/lib/features/session/**
```

## WRITE SCOPE

```text
mobile/lib/features/camera_management/**
mobile/test/features/camera_management/**
```

Existing `features/camera/**` streaming kodunu yeniden yazma.

## Contract

Response:
```text
id
name
code
departmentId
departmentName
active
status
lastSeenAt
activeSessionId
```

DİKKAT:
```text
status = ONLINE | WEAK | OFFLINE
```

`connectionStatus` bekleme.

Endpoints:
```text
GET /api/v1/cameras
POST /api/v1/cameras
GET /api/v1/cameras/{id}
PUT /api/v1/cameras/{id}
```

Create:
```text
name
code
departmentId
```

Update:
```text
name?
code?
departmentId?
active?
```

## UI

- list/card
- visible status badge
- active/passive
- last seen local time
- ADMIN create/edit/toggle
- non-admin no admin controls
- camera card -> existing CameraPage only through proper selected cameraId contract

## STRICTLY FORBIDDEN

- restricted zone
- reference image editor
- polygon
- new camera streaming implementation

## Tests

- parse status
- list
- ONLINE/WEAK/OFFLINE rendering
- admin action visible
- non-admin hidden
- 403
- create/update validation

## Acceptance

```text
flutter analyze
flutter test
```
