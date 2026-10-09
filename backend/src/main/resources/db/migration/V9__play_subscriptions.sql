-- Verified Google Play subscriptions. Purchase tokens are stored so missed
-- notifications can be re-queried. They are not written to application logs.

CREATE TABLE play_subscription (
    token_hash CHAR(64) PRIMARY KEY,
    purchase_token TEXT NOT NULL,
    package_name VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    base_plan_id VARCHAR(255),
    subscription_state VARCHAR(64) NOT NULL,
    expiry_time TIMESTAMPTZ,
    auto_renewing BOOLEAN NOT NULL,
    entitled BOOLEAN NOT NULL,
    linked_user_id UUID,
    latest_order_id VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_play_subscription_user
        FOREIGN KEY (linked_user_id) REFERENCES app_user (id)
);

CREATE TABLE play_rtdn_message (
    message_id VARCHAR(255) PRIMARY KEY,
    event_time TIMESTAMPTZ,
    notification_type INTEGER,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_play_subscription_expiry ON play_subscription (expiry_time);
