-- Distinct local workout dates used only to decide Pro Discovery eligibility.
-- workout_date is the phone's stored workoutDate (YYYY-MM-DD), not a completion timestamp.
-- The server decides "today" in UTC. A date one day past UTC today may be stored so a
-- zone ahead of UTC is not rejected; it does not count as recent until that UTC date arrives.
-- historical is true when the date was first added by a union import. That flag is not
-- proof the workout happened. server_first_observed_at is when this row was first stored.
-- These rows are not a workout log and are not an analytics stream.

CREATE TABLE pro_discovery_activity_day (
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    workout_date DATE NOT NULL,
    historical BOOLEAN NOT NULL,
    server_first_observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, workout_date)
);

-- One snapshot revision per account. REPLACE applies only when base_revision matches.
-- A stale REPLACE cannot put a removed date back. UNION adds dates and does not delete.
-- Workouts are device-local, so two devices do not merge into one true history.
CREATE TABLE pro_discovery_activity_sync (
    user_id UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pro_discovery_activity_sync_revision CHECK (revision >= 0)
);
