-- Pro Discovery activity collection is off until the account turns it on.
-- generation increases when collection is turned off and when it is turned on again.
-- An in-flight upload from an older generation cannot recreate deleted dates.
-- installation_id is a sync namespace for one app installation. It is not a credential.
-- A device replaces only its own dates. Eligibility is the distinct union of those sets.

CREATE TABLE pro_discovery_activity_permission (
    user_id UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL,
    scope_version INTEGER NOT NULL,
    generation BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pro_discovery_activity_permission_scope CHECK (scope_version >= 1),
    CONSTRAINT ck_pro_discovery_activity_permission_generation CHECK (generation >= 1)
);

CREATE TABLE pro_discovery_device (
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    installation_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, installation_id),
    CONSTRAINT ck_pro_discovery_device_revision CHECK (revision >= 0),
    CONSTRAINT ck_pro_discovery_device_generation CHECK (generation >= 1)
);

CREATE TABLE pro_discovery_device_day (
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    installation_id UUID NOT NULL,
    workout_date DATE NOT NULL,
    historical BOOLEAN NOT NULL,
    server_first_observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, installation_id, workout_date)
);

-- A device snapshot that has not been refreshed for 18 months can be removed
-- only while another snapshot for the same account was refreshed inside that window.
-- The sole remaining snapshot is kept. 18 months is a product limit, not a statute.
COMMENT ON TABLE pro_discovery_device IS
    'One activity snapshot per account and installation. Stale extra snapshots may be removed after 18 months.';
