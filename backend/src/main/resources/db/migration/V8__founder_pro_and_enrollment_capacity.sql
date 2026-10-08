-- Founder Pro expires. Existing FOUNDER_LIFETIME rows are left as they are:
-- expires_at stays null, and that source remains allowed. New approvals write FOUNDER_PRO.

ALTER TABLE entitlement_grant
    ADD COLUMN expires_at TIMESTAMPTZ;

ALTER TABLE entitlement_grant
    DROP CONSTRAINT ck_entitlement_grant_source;

ALTER TABLE entitlement_grant
    ADD CONSTRAINT ck_entitlement_grant_source
        CHECK (source IN ('FOUNDER_LIFETIME', 'FOUNDER_PRO'));

ALTER TABLE entitlement_grant
    ADD CONSTRAINT ck_entitlement_grant_expiry CHECK (
        (source = 'FOUNDER_LIFETIME' AND expires_at IS NULL)
        OR (source = 'FOUNDER_PRO' AND expires_at IS NOT NULL AND expires_at > granted_at)
    );

-- One locked counter for Founding Tester enrollment. It is not the Early Adopter cohort.
-- enrolled_count starts at the applications that already exist so those people stay enrolled
-- without consuming a second slot. If that count is already above 2,000, capacity rises to
-- match it so the migration can succeed and no further slot is free.

CREATE TABLE founder_program_capacity (
    id SMALLINT PRIMARY KEY,
    enrolled_count INTEGER NOT NULL,
    capacity INTEGER NOT NULL,
    enrollment_open BOOLEAN NOT NULL,
    CONSTRAINT ck_founder_program_capacity_singleton CHECK (id = 1),
    CONSTRAINT ck_founder_program_capacity_bounds CHECK (
        enrolled_count >= 0
        AND capacity > 0
        AND enrolled_count <= capacity
    )
);

INSERT INTO founder_program_capacity (id, enrolled_count, capacity, enrollment_open)
SELECT 1,
       counted.total,
       GREATEST(2000, counted.total),
       true
FROM (SELECT COUNT(*)::integer AS total FROM founder_application) counted;
