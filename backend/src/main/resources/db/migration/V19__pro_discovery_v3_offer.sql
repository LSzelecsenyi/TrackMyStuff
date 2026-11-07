-- Pro Discovery V3 decides offers from the account registration time and the
-- last time an offer was actually shown. Workout dates are not an input.
-- V17 and V18 stored that obsolete activity. Those tables are removed here.
-- The one-time trial row, Founder, Welcome Back, Play, and deletion markers stay.

DROP TABLE IF EXISTS pro_discovery_device_day;
DROP TABLE IF EXISTS pro_discovery_device;
DROP TABLE IF EXISTS pro_discovery_activity_permission;
DROP TABLE IF EXISTS pro_discovery_activity_day;
DROP TABLE IF EXISTS pro_discovery_activity_sync;

-- presented_at is written only after the offer was displayed.
-- A status query does not fill it. The next offer is two calendar months later.
CREATE TABLE pro_discovery_offer (
    user_id UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    presented_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);
