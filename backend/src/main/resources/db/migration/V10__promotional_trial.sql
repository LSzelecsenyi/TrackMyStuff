-- One promotional Pro Discovery trial per Strict account.
-- Client workout history is not stored here. Activation time is server time.

CREATE TABLE promotional_trial (
    user_id UUID NOT NULL REFERENCES app_user (id),
    promotion_type VARCHAR(64) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, promotion_type)
);
