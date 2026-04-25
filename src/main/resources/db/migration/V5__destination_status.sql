-- Add status fields to destination so an organizer can mark one row as the chosen choice.
-- Partial unique index enforces "at most one CHOSEN per trip" at the DB level.
ALTER TABLE destination
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'CHOSEN'));

ALTER TABLE destination
    ADD COLUMN chosen_at TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE destination
    ADD COLUMN chosen_by UUID NULL;

CREATE UNIQUE INDEX uq_destination_one_chosen_per_trip
    ON destination(trip_id)
    WHERE status = 'CHOSEN';
