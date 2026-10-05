ALTER TABLE founder_review_snapshot
    ADD COLUMN client_submission_id UUID;

UPDATE founder_review_snapshot
SET client_submission_id = id
WHERE client_submission_id IS NULL;

ALTER TABLE founder_review_snapshot
    ALTER COLUMN client_submission_id SET NOT NULL;

ALTER TABLE founder_review_snapshot
    ADD CONSTRAINT uq_founder_review_snapshot_submission UNIQUE (client_submission_id);
