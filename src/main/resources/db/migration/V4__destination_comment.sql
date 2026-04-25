CREATE TABLE destination_comment (
    id             UUID PRIMARY KEY,
    destination_id UUID NOT NULL REFERENCES destination(id) ON DELETE CASCADE,
    trip_id        UUID NOT NULL,
    device_id      UUID NOT NULL,
    content        TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_destination_comment_destination_created
    ON destination_comment (destination_id, created_at ASC, id ASC);
