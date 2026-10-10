-- V9 created token_hash as CHAR(64). PostgreSQL reports that as bpchar,
-- while Hibernate maps the String id to varchar(64).
-- rtrim drops the blank padding CHAR stores.

ALTER TABLE play_subscription
    ALTER COLUMN token_hash TYPE VARCHAR(64) USING rtrim(token_hash);
