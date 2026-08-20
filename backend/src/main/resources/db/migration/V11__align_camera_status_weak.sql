ALTER TABLE cameras
DROP CONSTRAINT IF EXISTS cameras_status_chk;

UPDATE cameras
SET status = 'WEAK'
WHERE status = 'DEGRADED';

ALTER TABLE cameras
    ADD CONSTRAINT cameras_status_chk
        CHECK (
            status IN (
                       'ONLINE',
                       'WEAK',
                       'OFFLINE'
                )
            );