-- destination service: initial schema (story 4.1)
-- Only the `destination` table is created here; `destination_vote` and
-- `destination_comment` ship with stories 4.2 and 4.4 in later migrations.

CREATE TABLE destination (
    id                UUID PRIMARY KEY,
    trip_id           UUID NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    image_key         VARCHAR(500),
    estimated_budget  DECIMAL(19, 4),
    currency          VARCHAR(3),
    external_url      VARCHAR(512),
    proposed_by       UUID NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_destination_trip_id_created_at_desc
    ON destination (trip_id, created_at DESC);
