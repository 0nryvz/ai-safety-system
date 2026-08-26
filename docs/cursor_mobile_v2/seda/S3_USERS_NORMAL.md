# S-3 Users — ADMIN Management

**MODEL: NORMAL**

## Goal

ADMIN user management.

## READ FIRST

```text
mobile/lib/features/auth/**
mobile/lib/core/network/**
mobile/lib/core/models/**
```

## WRITE SCOPE

```text
mobile/lib/features/users/**
mobile/test/features/users/**
```

## Endpoints

```text
GET /api/v1/users
POST /api/v1/users
GET /api/v1/users/{id}
PATCH /api/v1/users/{id}
DELETE /api/v1/users/{id}
GET /api/v1/users/me/departments
```

## Models

User:
```text
id
email
fullName
active
departmentId?
departmentName?
roles
departmentIds
createdAt
```

Create:
```text
email
password
fullName
departmentIds
roleNames
```

Update:
```text
fullName?
departmentIds?
roleNames?
active?
```

Roles:
```text
ADMIN
OHS_SPECIALIST
SHIFT_SUPERVISOR
```

DELETE backendde deactivate semantic; UI'da "Pasifleştir" olarak sunulabilir.

## UI

- ADMIN only navigation
- list
- create
- edit
- role selection
- department selection
- active/passive
- validation errors field-level

## DO NOT

- non-admin için client security guarantee iddia etme
- yeni departments endpoint uydurma
- auth core edit etme

## Tests

- admin visibility
- non-admin hidden
- user list parse
- create validation
- update
- deactivate semantics
- 403

## Acceptance

```text
flutter analyze
flutter test
```
