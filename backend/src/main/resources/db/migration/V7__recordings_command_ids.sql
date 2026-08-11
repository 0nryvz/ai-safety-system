ALTER TABLE recordings
    ADD COLUMN start_command_id uuid,
    ADD COLUMN stop_command_id uuid;
