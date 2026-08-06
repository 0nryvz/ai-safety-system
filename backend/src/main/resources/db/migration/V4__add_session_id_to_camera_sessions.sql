-- V4 — Gateway session identifier
--
-- Gateway tarafından üretilen session_id için business key alanı.
-- UUID id alanı primary key olarak kalır.

ALTER TABLE camera_sessions
    ADD COLUMN session_id VARCHAR(255) NOT NULL UNIQUE;