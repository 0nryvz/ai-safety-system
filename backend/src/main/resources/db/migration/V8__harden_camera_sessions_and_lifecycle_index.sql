-- =====================================================================================
-- V8 - Camera session audit/version hardening and lifecycle reporting index
--
-- Owner: Backend 1
--
-- camera_sessions is mutable: session status can change from ACTIVE to CLOSED/TIMED_OUT.
-- Add audit/version columns to align the table with the shared database standard.
--
-- violations.lifecycle_status is a general API filter, so add an index covering
-- all lifecycle values in addition to the existing partial indexes.
-- =====================================================================================

ALTER TABLE camera_sessions
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE TRIGGER camera_sessions_set_updated_at
    BEFORE UPDATE ON camera_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX violations_lifecycle_status_started_at_idx
    ON violations (lifecycle_status, started_at DESC);