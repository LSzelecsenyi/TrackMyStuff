-- Founder enrollment_open becomes a lifecycle. true was accepting new
-- participants (ACTIVE). false was a reversible pause (INACTIVE). Existing
-- counters, applications, and entitlements are not changed. RETIRED is new
-- and is not inferred from a full cohort.

ALTER TABLE founder_program_capacity
    ADD COLUMN lifecycle VARCHAR(16);

UPDATE founder_program_capacity
SET lifecycle = CASE WHEN enrollment_open THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE founder_program_capacity
    ALTER COLUMN lifecycle SET NOT NULL;

ALTER TABLE founder_program_capacity
    ADD CONSTRAINT ck_founder_program_capacity_lifecycle
        CHECK (lifecycle IN ('ACTIVE', 'INACTIVE', 'RETIRED'));

ALTER TABLE founder_program_capacity
    DROP COLUMN enrollment_open;

-- Early Adopter had no closure flag. Existing cohorts stay ACTIVE.
-- A full cohort remains ACTIVE until an admin changes the lifecycle.

ALTER TABLE early_adopter_cohort
    ADD COLUMN lifecycle VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE early_adopter_cohort
    ADD CONSTRAINT ck_early_adopter_cohort_lifecycle
        CHECK (lifecycle IN ('ACTIVE', 'INACTIVE', 'RETIRED'));

ALTER TABLE early_adopter_cohort
    ALTER COLUMN lifecycle DROP DEFAULT;
