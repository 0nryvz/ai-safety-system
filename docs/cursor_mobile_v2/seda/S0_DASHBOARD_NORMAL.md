# S-0 Mobile Dashboard

**MODEL: NORMAL**

## Prerequisite

Onur O-0 contract seed integration branchinde olmalı.

## Goal

Gerçek backend dashboard verisini mobil-native kart/kompakt grafik/list olarak göster.

## READ FIRST

```text
mobile/lib/app.dart
mobile/lib/core/network/**
mobile/lib/core/models/**
mobile/lib/core/theme/strix_brand.dart
```

Onur'un core contractını tüket; kendi clientını oluşturma.

## WRITE SCOPE

```text
mobile/lib/features/dashboard/**
mobile/test/features/dashboard/**
```

## Endpoints

```text
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/recent-violations
GET /api/v1/dashboard/trend?from&to&bucket=DAY
GET /api/v1/dashboard/distribution?...groupBy=...
```

## UI

- 2-4 KPI card
- recent violations list
- compact trend
- compact distribution
- pull-to-refresh
- loading
- empty
- error
- offline

Desktop table kopyalama.

## Contract rules

- trend bucket: canonical `DAY`
- groupBy yalnız backend controllerın desteklediği değerler
- UTC -> local display
- recent card => violation detail navigation interface

## DO NOT

- core/network edit etme
- endpoint uydurma
- auth state oluşturma
- WebSocket implement etme

## Tests

- summary success
- empty trend
- empty distribution
- error
- offline render
- recent card navigation

## Acceptance

```text
flutter analyze
flutter test
```
