ALTER TABLE founder_review_snapshot
    ADD COLUMN rules_profile VARCHAR(32),
    ADD COLUMN temporary_pro_workout_count INTEGER,
    ADD COLUMN required_workout_count INTEGER,
    ADD COLUMN required_distinct_day_count INTEGER,
    ADD COLUMN qualification_window_days INTEGER;

ALTER TABLE founder_review_snapshot
    ADD CONSTRAINT ck_founder_review_snapshot_rules CHECK (
        (temporary_pro_workout_count IS NULL OR temporary_pro_workout_count >= 1)
        AND (required_workout_count IS NULL OR required_workout_count >= 1)
        AND (required_distinct_day_count IS NULL OR required_distinct_day_count >= 1)
        AND (qualification_window_days IS NULL OR qualification_window_days >= 1)
    );

ALTER TABLE founder_workout_event
    ADD COLUMN display_name VARCHAR(80),
    ADD COLUMN duration_seconds INTEGER,
    ADD COLUMN exercise_count INTEGER,
    ADD COLUMN completed_set_count INTEGER,
    ADD COLUMN from_template BOOLEAN,
    ADD COLUMN used_external_load BOOLEAN;

ALTER TABLE founder_workout_event
    ADD CONSTRAINT ck_founder_workout_event_observations CHECK (
        (duration_seconds IS NULL OR duration_seconds >= 0)
        AND (exercise_count IS NULL OR exercise_count >= 0)
        AND (completed_set_count IS NULL OR completed_set_count >= 0)
    );
