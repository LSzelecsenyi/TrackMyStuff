-- Account statuses that are not Founder Lifetime.
-- Founder stays on entitlement_grant (source FOUNDER_LIFETIME).
-- Early Adopter and Developer must not grant Pro.

CREATE TABLE account_status_grant (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_account_status_grant_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_account_status_grant_user_status UNIQUE (user_id, status),
    CONSTRAINT ck_account_status_grant_status
        CHECK (status IN ('EARLY_ADOPTER', 'DEVELOPER'))
);

CREATE TABLE early_adopter_cohort (
    id SMALLINT PRIMARY KEY,
    assigned_count INTEGER NOT NULL,
    capacity INTEGER NOT NULL,
    backfill_completed BOOLEAN NOT NULL,
    CONSTRAINT ck_early_adopter_cohort_singleton CHECK (id = 1),
    CONSTRAINT ck_early_adopter_cohort_bounds CHECK (
        assigned_count >= 0
        AND capacity > 0
        AND assigned_count <= capacity
    )
);

-- One durable counter. It is incremented when a slot is assigned and is never
-- recomputed from the remaining grant rows. backfill_completed makes the
-- existing-user pass run once; later registrations only take a slot when
-- assigned_count is still below capacity.
INSERT INTO early_adopter_cohort (id, assigned_count, capacity, backfill_completed)
VALUES (1, 0, 1000, false);
