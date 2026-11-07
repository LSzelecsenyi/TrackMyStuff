-- Checksums for the deletion-marker HMAC keys. The Google subject is not stored.
-- A key id whose secret no longer matches this row fails closed.

CREATE TABLE marker_key_verifier (
    key_id INTEGER PRIMARY KEY,
    verifier VARCHAR(64) NOT NULL
);
