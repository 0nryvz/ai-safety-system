# Frontend Contracts

This document records the shared frontend contracts introduced in FE2 Step 1.

## Date and time contract

Backend date-time values are treated as UTC.

Frontend rules:

- Backend timestamps are parsed as UTC-compatible ISO date-time values.
- Date-time values are displayed in the user's local timezone.
- Form date values are converted to UTC ISO format before they are sent to the backend.
- Shared date conversion helpers live under `web/src/core/date`.
- `DateRange` only collects date values.
- `DateRange` does not define backend query parameter names.
- Backend-specific filter parameter names are added only after the relevant endpoint contract is confirmed.

Example:

**Backend value**

`2026-08-14T22:00:00Z`

**User timezone**

`UTC+03:00`

**Frontend display**

`2026-08-15 01:00`

**Frontend submission**

`Date -> toISOString() -> UTC ISO value`

## API error contract

Frontend REST requests use the shared Axios client:

`web/src/core/api/apiClient.ts`

Shared API errors use:

- `web/src/core/api/apiError.ts`
- `web/src/core/api/apiErrorMapper.ts`
- `web/src/core/api/apiErrorPolicy.ts`

Error behavior:

- `401` means the session or authentication state is invalid.
- `403` means the authenticated user does not have permission.
- `401` and `403` must not be handled as the same condition.
- Network failures are represented separately from HTTP response errors.
- Unsupported backend error fields must not be invented by the frontend.

## Correlation ID dependency

The current confirmed backend error contract contains:

- `timestamp`
- `status`
- `error`
- `message`
- `path`

A correlation ID, request ID, or trace ID is not currently available in the confirmed backend error response contract.

Frontend rules:

- The frontend must not generate a fake correlation ID.
- Correlation/request ID display remains a backend contract dependency.
- When the backend exposes a confirmed ID field or response header, the shared API error layer can preserve and display it.
- Until then, error UI must use only confirmed backend fields.

## Auth token provider contract

Shared authentication session types and token provider live under:

`web/src/features/auth`

`AuthTokenProvider` provides:

- current access token lookup
- current session lookup
- current session status
- login change notifications
- token refresh notifications
- logout notifications
- session expiry notifications

The FE1 realtime client can consume this interface without depending on LoginPage implementation details.

The following items belong to FE2 Step 2:

- full session persistence
- Bearer request interception
- refresh-token handling
- logout integration

## Role access contract

Frontend role visibility uses:

- `ADMIN`
- `OHS_SPECIALIST`
- `SHIFT_SUPERVISOR`

Frontend role checks are only for UI and route visibility.

Backend authorization remains authoritative for:

- protected operations
- department access
- returned data
- `403 Forbidden` responses
