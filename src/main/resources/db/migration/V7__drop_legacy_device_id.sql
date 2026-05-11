-- Phase 3: drop legacy device_id columns now that all callers consume trip_member_id.
-- Dropping device_id columns also removes the legacy unique constraint
-- (destination_id, device_id) and any indexes that referenced device_id.
-- See docs/PROGRESS_device_id_to_member_id.md.

ALTER TABLE destination
    DROP COLUMN proposed_by,
    DROP COLUMN chosen_by;

ALTER TABLE destination
    ALTER COLUMN proposed_by_trip_member_id SET NOT NULL;

ALTER TABLE destination_vote
    DROP COLUMN device_id;

ALTER TABLE destination_vote
    ALTER COLUMN trip_member_id SET NOT NULL;

ALTER TABLE destination_vote
    ADD CONSTRAINT uk_destination_vote_destination_member UNIQUE (destination_id, trip_member_id);

ALTER TABLE destination_comment
    DROP COLUMN device_id;

ALTER TABLE destination_comment
    ALTER COLUMN trip_member_id SET NOT NULL;
