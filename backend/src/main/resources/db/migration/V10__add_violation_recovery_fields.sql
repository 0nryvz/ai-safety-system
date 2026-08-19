-- =====================================================================================
-- V10 - Add violation recovery fields
--
-- Owner: Backend 1
--
-- Persist the subject identity and originating camera session required by
-- Backend 3 restart/state recovery. Both fields remain nullable so existing
-- violation records remain valid.
-- =====================================================================================

ALTER TABLE violations
    ADD COLUMN subject_key varchar(255),
    ADD COLUMN source_session_id uuid;

ALTER TABLE violations
    ADD CONSTRAINT violations_source_session_fk
        FOREIGN KEY (source_session_id)
        REFERENCES camera_sessions (id)
        ON DELETE SET NULL;

CREATE INDEX violations_source_session_idx
    ON violations (source_session_id);
