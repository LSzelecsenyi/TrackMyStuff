-- Strict users, provider identities, and opaque auth sessions.
-- Absolute instants are TIMESTAMPTZ. Foreign keys do not cascade.

CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE external_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_external_identity_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_external_identity_provider_subject
        UNIQUE (provider, provider_subject)
);

CREATE INDEX ix_external_identity_user_id ON external_identity (user_id);

CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT fk_auth_session_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_auth_session_token_hash
        UNIQUE (token_hash),
    CONSTRAINT ck_auth_session_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX ix_auth_session_user_id ON auth_session (user_id);
