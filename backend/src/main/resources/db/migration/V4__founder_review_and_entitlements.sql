-- Admin identity is separate from app_user. Founder Lifetime is a grant, not a flag on the user.

CREATE TABLE admin_user (
    id UUID PRIMARY KEY,
    google_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_admin_user_google_subject UNIQUE (google_subject)
);

CREATE TABLE admin_session (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_admin_session_admin
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id),
    CONSTRAINT uq_admin_session_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_admin_session_expiry CHECK (expires_at > created_at)
);

CREATE TABLE entitlement_grant (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source VARCHAR(32) NOT NULL,
    founder_application_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_entitlement_grant_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_entitlement_grant_application
        FOREIGN KEY (founder_application_id) REFERENCES founder_application (id),
    CONSTRAINT uq_entitlement_grant_user_source UNIQUE (user_id, source),
    CONSTRAINT uq_entitlement_grant_application UNIQUE (founder_application_id),
    CONSTRAINT ck_entitlement_grant_source CHECK (source = 'FOUNDER_LIFETIME')
);

CREATE TABLE founder_review_decision (
    id UUID PRIMARY KEY,
    founder_application_id UUID NOT NULL,
    admin_user_id UUID NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(2000),
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_founder_review_decision_application
        FOREIGN KEY (founder_application_id) REFERENCES founder_application (id),
    CONSTRAINT fk_founder_review_decision_admin
        FOREIGN KEY (admin_user_id) REFERENCES admin_user (id),
    CONSTRAINT uq_founder_review_decision_application UNIQUE (founder_application_id),
    CONSTRAINT ck_founder_review_decision_shape CHECK (
        (decision = 'APPROVED' AND reason IS NULL)
        OR (decision = 'REJECTED' AND reason IS NOT NULL AND char_length(btrim(reason)) > 0)
    )
);
