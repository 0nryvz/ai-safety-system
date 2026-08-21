# S-2 Violations — History, Filter, Detail, Review

**MODEL: STRONG**

Bu Seda tarafındaki en karmaşık feature.

## Goal

İhlal history/filter/detail/review ekranlarını backend contractına birebir bağla.

## READ FIRST

```text
mobile/lib/core/network/**
mobile/lib/core/models/**
mobile/lib/shared/media/**     # Onur clip player hazırsa
mobile/lib/features/dashboard/**

backend violation enums/controllers/DTOs READ-ONLY gerekirse
```

## WRITE SCOPE

```text
mobile/lib/features/violations/**
mobile/test/features/violations/**
```

Onur shared ClipPlayer'a yazma.

## List endpoint

```text
GET /api/v1/violations
```

Filters:
```text
from
to
type
cameraId
departmentId
lifecycleStatus
reviewStatus
recordingStatus
page
size
sort
```

Filter UI:
- bottom sheet
- applied filter summary
- clear filters
- UTC request conversion

## Status separation

Kesin ayrı alanlar:
```text
lifecycleStatus
reviewStatus
recordingStatus
```

Current backend lifecycle:
```text
ACTIVE
PREPARING
COMPLETED
ERROR
```

## Violation type drift

Backend canonical enum source-of-truth.

Current snapshot jacket içermeyebilir.
PRD jacket istiyor diye mobilde backend merge olmadan enum uydurma.

## Detail

```text
GET /api/v1/violations/{id}
```

Görüntüle:
- camera
- department
- confidence
- modelVersion
- timestamps
- lifecycle
- review
- recording
- clip/cover readiness

## Review

```text
PATCH /api/v1/violations/{id}/review
```

Body:
```json
{
  "reviewStatus": "REVIEWED|CONFIRMED|FALSE_ALARM",
  "version": 0
}
```

Conflict:
- local stale state'i overwrite etme
- server refresh
- user'a state changed mesajı
- explicit retry UX

## Media

Clip ready:
- Onur shared player kullan

Not ready:
- preparation placeholder

Error:
- distinct error state

## DO NOT

- objectKey
- MinIO client
- new violation type
- core auth/client edit
- backend fix

## Tests

- page parse
- all filters encode
- UTC conversion
- lifecycle/review/recording separate render
- detail
- review success
- conflict -> refresh
- NOT_READY
- ERROR
- READY clip host widget

## Acceptance

```text
flutter analyze
flutter test
```
