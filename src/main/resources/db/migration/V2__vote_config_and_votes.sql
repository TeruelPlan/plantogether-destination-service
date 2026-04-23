CREATE TABLE destination_vote_config (
    trip_id    UUID        PRIMARY KEY,
    mode       VARCHAR(16) NOT NULL CHECK (mode IN ('SIMPLE', 'APPROVAL', 'RANKING')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE destination_vote (
    id             UUID        PRIMARY KEY,
    destination_id UUID        NOT NULL REFERENCES destination(id) ON DELETE CASCADE,
    trip_id        UUID        NOT NULL,
    device_id      UUID        NOT NULL,
    rank           INT         NULL CHECK (rank IS NULL OR rank >= 1),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (destination_id, device_id)
);

CREATE INDEX idx_destination_vote_trip_device ON destination_vote(trip_id, device_id);
CREATE INDEX idx_destination_vote_destination ON destination_vote(destination_id);
