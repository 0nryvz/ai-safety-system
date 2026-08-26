# Current Repo Contract Snapshot

Bu dosya güncel repo snapshotındaki controller/DTO/enumlardan çıkarılmış kısa referanstır.
Cursor mümkün olduğunca bu sözleşmeyi kullansın; değişiklik görürse ilgili backend dosyasını okuyarak doğrulasın.

---

## Auth

### POST `/api/v1/auth/login`

Request:
```json
{
  "email": "user@example.com",
  "password": "..."
}
```

Response:
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer"
}
```

### POST `/api/v1/auth/refresh`

Body:
```json
{"refreshToken":"..."}
```

Response: AuthResponse.

### POST `/api/v1/auth/logout`

Body:
```json
{"refreshToken":"..."}
```

---

## Current user

### GET `/api/v1/users/me`

Current known `UserResponse` alanları:

```text
id: UUID
email: String
fullName: String
active: bool
departmentId: UUID?
departmentName: String?
roles: Set<String>
departmentIds: Set<UUID>
createdAt
```

### GET `/api/v1/users/me/departments`

Kullanıcının erişebildiği department listesi.

---

## Users

### GET `/api/v1/users`
### POST `/api/v1/users`
### GET `/api/v1/users/{id}`
### PATCH `/api/v1/users/{id}`
### DELETE `/api/v1/users/{id}`

Create:
```text
email
password (min 6)
fullName
departmentIds
roleNames (boş olamaz)
```

Update:
```text
fullName?
departmentIds?
roleNames?
active?
```

Roller:
```text
ADMIN
OHS_SPECIALIST
SHIFT_SUPERVISOR
```

---

## Cameras

### GET `/api/v1/cameras`
### POST `/api/v1/cameras`
### GET `/api/v1/cameras/{id}`
### PUT `/api/v1/cameras/{id}`

`CameraResponse`:

```text
id: UUID
name
code
departmentId: UUID
departmentName
active: bool
status: ONLINE | WEAK | OFFLINE
lastSeenAt
activeSessionId
```

DİKKAT:
- Alan adı `status`.
- `connectionStatus` diye yeni field uydurma.

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

Mobilde restricted-zone endpointlerini kullanma.

---

## Dashboard

### GET `/api/v1/dashboard/summary`

Known response:
```text
todayViolationCount
last7DaysViolationCount
mostFrequentViolationType
activeCameraCount
offlineCameraCount
activeViolationCount
```

### GET `/api/v1/dashboard/trend?from=YYYY-MM-DD&to=YYYY-MM-DD&bucket=DAY`

Şu an canonical bucket:
```text
DAY
```

Başka bucket uydurma.

### GET `/api/v1/dashboard/distribution?...`

Canonical groupBy setini controller kodundan doğrula.
Planlanan/known seçenekler:
```text
TYPE
CAMERA
DEPARTMENT
```

### GET `/api/v1/dashboard/recent-violations`

Known item:
```text
violationId
detectedAt
startedAt
violationType
cameraId
departmentId
departmentName
cameraName
cameraCode
lifecycleStatus
reviewStatus
recordingStatus
recordingReadyAt
confidence
modelVersion
```

---

## Violations

### GET `/api/v1/violations`

Filterler:
```text
from: Instant?
to: Instant?
type: ViolationType?
cameraId: UUID?
departmentId: UUID?
lifecycleStatus?
reviewStatus?
recordingStatus?
page
size
sort
```

Response:
```text
PageResponse
```

### GET `/api/v1/violations/{id}`

### PATCH `/api/v1/violations/{id}/review`

Body:
```json
{
  "reviewStatus": "REVIEWED | CONFIRMED | FALSE_ALARM",
  "version": 0
}
```

Conflict durumunda eski state'i sessizce ezme.

### Current lifecycle enum

```text
ACTIVE
PREPARING
COMPLETED
ERROR
```

Eski `RECORDING_PREPARING` değerini körlemesine kullanma.

### Current review enum

```text
UNREVIEWED
REVIEWED
CONFIRMED
FALSE_ALARM
```

### Current ViolationType snapshot

Güncel backend enum snapshotında:
```text
MISSING_WELDING_MASK
MISSING_GLOVES
MISSING_WELDING_APRON
RESTRICTED_ZONE
UNPROTECTED_PERSON
```

PRD v2 ceket ihlal ailesini ister, ancak backend canonical enum merge edilmeden mobil:
```text
MISSING_WELDING_JACKET
```
gibi yeni bir type UYDURMAMALI.

Backend 3 fix merge olduğunda bu dosya/enum tekrar doğrulanmalı.

---

## Media

### GET `/api/v1/violations/{violationId}/clip-url`
### GET `/api/v1/violations/{violationId}/cover-url`

Response semantic:
```text
url
expiresAt
```

- objectKey kullanma.
- MinIO SDK kullanma.
- URL expire olursa yeniden iste.

---

## Realtime

Endpoint:
```text
/ws
```

Protocol:
```text
STOMP over WebSocket
```

CONNECT native header:
```text
Authorization: Bearer <JWT>
```

Subscribe:
```text
/user/queue/alerts
```

Known AlertMessage:
```text
violationId
type
cameraName
departmentName
startedAt
confidence
lifecycleStatus
recordingStatus
clipReady
coverImageReady
```

Known ViolationUpdateMessage:
```text
violationId
lifecycleStatus
recordingStatus
clipReady
updatedAt
errorCode?
```

DİKKAT:
Current DTO snapshotta zorunlu `eventId` alanı görünmüyor.
Dedupe için:
1. eventId/version gerçekten geliyorsa kullan.
2. Yoksa violationId + message shape/status + timestamp gibi deterministic fallback oluştur.
3. Backendde olmayan alanı required parse field yapma.
