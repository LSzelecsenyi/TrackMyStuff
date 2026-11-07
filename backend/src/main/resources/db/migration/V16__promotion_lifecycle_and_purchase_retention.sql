-- Early Adopter eligibility is fixed at registration. Accounts that already
-- exist stay eligible for the one-time historical backfill. Accounts created
-- later are marked ineligible by the application when the program is not ACTIVE.

ALTER TABLE app_user
    ADD COLUMN early_adopter_ineligible BOOLEAN NOT NULL DEFAULT false;

-- Pro Discovery and Welcome Back were not stored separately. They could be
-- issued only while Founder enrollment was not ACTIVE. Copy that current
-- effective state once, then leave the programs independent.
-- Founder ACTIVE -> both promotions INACTIVE (they were not being issued).
-- Founder INACTIVE or RETIRED -> both promotions ACTIVE (they were being issued).
-- This does not turn a promotion on when Founder enrollment is open.

CREATE TABLE promotional_program (
    program VARCHAR(32) PRIMARY KEY,
    lifecycle VARCHAR(16) NOT NULL,
    CONSTRAINT ck_promotional_program_name
        CHECK (program IN ('PRO_DISCOVERY', 'WELCOME_BACK')),
    CONSTRAINT ck_promotional_program_lifecycle
        CHECK (lifecycle IN ('ACTIVE', 'INACTIVE', 'RETIRED'))
);

INSERT INTO promotional_program (program, lifecycle)
SELECT 'PRO_DISCOVERY',
       CASE WHEN lifecycle = 'ACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM founder_program_capacity
WHERE id = 1;

INSERT INTO promotional_program (program, lifecycle)
SELECT 'WELCOME_BACK',
       CASE WHEN lifecycle = 'ACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM founder_program_capacity
WHERE id = 1;

-- ended_at is the moment the subscription stopped being recoverable as an
-- entitlement. It is not the cancellation request time. retention_hold is an
-- operator flag. The application does not set it and does not invent a legal hold.

ALTER TABLE play_subscription
    ADD COLUMN ended_at TIMESTAMPTZ,
    ADD COLUMN retention_hold BOOLEAN NOT NULL DEFAULT false;
