-- One Welcome Back grant per Strict account.
-- The qualifying workout id blocks replay. Local workout dates are not stored.
-- Cooldown is measured from activation and is not reset by reinstall.

CREATE TABLE welcome_back_grant (
    user_id UUID PRIMARY KEY REFERENCES app_user (id),
    qualifying_workout_id VARCHAR(80) NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    cooldown_until TIMESTAMPTZ NOT NULL
);
