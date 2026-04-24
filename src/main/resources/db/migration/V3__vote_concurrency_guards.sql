-- Story 4.2 code-review hardening (D2+D3).
-- Defense-in-depth: enforce "unique rank per (trip, device)" at the DB layer on top of
-- the service-level pg_advisory_xact_lock. The partial index excludes rows where rank is
-- NULL so it does not conflict with APPROVAL / SIMPLE (rank IS NULL by design).
CREATE UNIQUE INDEX IF NOT EXISTS uq_destination_vote_trip_device_rank
    ON destination_vote (trip_id, device_id, rank)
    WHERE rank IS NOT NULL;
