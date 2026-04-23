-- destination service: initial schema

CREATE TABLE destination
(
    id               UUID PRIMARY KEY,
    trip_id          UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    image_key        VARCHAR(500),
    estimated_budget NUMERIC(19, 4),
    currency         VARCHAR(3),
    external_url     VARCHAR(512),
    proposed_by      UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_destination_trip ON destination (trip_id);
