-- One Founder application per Strict user. Qualifying workouts are immutable events.
-- Deadline is an absolute instant. Distinct days use the client-attested local date.

CREATE TABLE founder_application (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    feedback_text VARCHAR(8000),
    feedback_submitted_at TIMESTAMPTZ,
    tester_report_submitted_at TIMESTAMPTZ,
    pending_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_founder_application_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_founder_application_user
        UNIQUE (user_id),
    CONSTRAINT ck_founder_application_deadline
        CHECK (deadline_at > enrolled_at)
);

CREATE TABLE founder_workout_event (
    id UUID PRIMARY KEY,
    founder_application_id UUID NOT NULL,
    client_workout_id UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    workout_local_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_founder_workout_event_application
        FOREIGN KEY (founder_application_id) REFERENCES founder_application (id),
    CONSTRAINT uq_founder_workout_event_application_client
        UNIQUE (founder_application_id, client_workout_id)
);

CREATE TABLE founder_review_snapshot (
    id UUID PRIMARY KEY,
    founder_application_id UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    app_version VARCHAR(32) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    qualifying_workout_count INTEGER NOT NULL,
    distinct_workout_day_count INTEGER NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    feedback_text VARCHAR(8000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_founder_review_snapshot_application
        FOREIGN KEY (founder_application_id) REFERENCES founder_application (id),
    CONSTRAINT uq_founder_review_snapshot_application
        UNIQUE (founder_application_id)
);
