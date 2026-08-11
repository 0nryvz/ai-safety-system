-- V6 — Add revoked flag to refresh_tokens
-- Auth modülü için refresh token iptal durumunun tutulması

ALTER TABLE refresh_tokens
    ADD COLUMN revoked BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN refresh_tokens.revoked IS
    'Refresh token iptal durumu. Logout veya güvenlik nedeniyle geçersiz kılınan tokenlar true olur.';