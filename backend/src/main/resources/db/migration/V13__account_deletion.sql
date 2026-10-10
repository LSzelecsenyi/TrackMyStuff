-- Account deletion.
--
-- account_deletion_marker stores a one-way hash of the Google subject and
-- which one-time promotions that subject already used. It does not store the
-- subject, email, name, or workout content. It exists so Founder, Pro
-- Discovery, Welcome Back, and Early Adopter cannot be claimed again by
-- creating a new Strict user with the same Google account. No statutory
-- retention period is asserted for this row.
--
-- play_subscription.claim_blocked stops a later account from attaching a
-- purchase token that belonged to a deleted account. The token itself stays
-- so Play notifications can still refresh that row. Deleting a Strict account
-- does not cancel the Google Play subscription.

CREATE TABLE account_deletion_marker (
    subject_hash VARCHAR(64) PRIMARY KEY,
    deleted_at TIMESTAMPTZ NOT NULL,
    founder_used BOOLEAN NOT NULL,
    pro_discovery_used BOOLEAN NOT NULL,
    welcome_back_used BOOLEAN NOT NULL,
    early_adopter_used BOOLEAN NOT NULL
);

ALTER TABLE play_subscription
    ADD COLUMN claim_blocked BOOLEAN NOT NULL DEFAULT FALSE;
