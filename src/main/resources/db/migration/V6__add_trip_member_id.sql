-- Phase 1: introduce trip_member_id alongside device_id for cross-service identity migration.
-- Columns are nullable until backfill completes; legacy device_id columns remain in place
-- and are dual-written until Phase 3 (cleanup). See docs/PLAN_device_id_to_member_id.md.

ALTER TABLE destination
    ADD COLUMN proposed_by_trip_member_id UUID,
    ADD COLUMN chosen_by_trip_member_id UUID;

CREATE INDEX idx_destination_proposed_by_member ON destination (proposed_by_trip_member_id);

ALTER TABLE destination_vote
    ADD COLUMN trip_member_id UUID;

CREATE INDEX idx_destination_vote_member ON destination_vote (trip_id, trip_member_id);

ALTER TABLE destination_comment
    ADD COLUMN trip_member_id UUID;

CREATE INDEX idx_destination_comment_member ON destination_comment (trip_member_id);
