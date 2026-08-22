ALTER TABLE recordings
    ADD COLUMN clip_group_id uuid;

CREATE INDEX recordings_clip_group_idx
    ON recordings (clip_group_id)
    WHERE clip_group_id IS NOT NULL;
