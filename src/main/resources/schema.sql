ALTER TABLE IF EXISTS task_submissions
    ALTER COLUMN pull_request_url DROP NOT NULL;
