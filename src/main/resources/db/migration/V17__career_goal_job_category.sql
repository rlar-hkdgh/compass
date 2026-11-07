ALTER TABLE student_career_goal
    ADD COLUMN job_category_code VARCHAR(32) REFERENCES job_category(code);

ALTER TABLE student_career_goal
    ALTER COLUMN job_field DROP NOT NULL;

ALTER TABLE student_career_goal
    ADD CONSTRAINT chk_career_goal_job_selection CHECK (job_field IS NOT NULL OR job_category_code IS NOT NULL);

CREATE INDEX idx_career_goal_job_category ON student_career_goal(job_category_code);

-- Preserve the original classification and only backfill direct matches.
UPDATE student_career_goal SET job_category_code = '84' WHERE job_field = 'BACKEND';
UPDATE student_career_goal SET job_category_code = '92' WHERE job_field = 'FRONTEND';

-- A goal that still needs a job selection must be revisited during onboarding.
DELETE FROM student_onboarding_step
WHERE step_code = 'PREFERENCES'
  AND student_id IN (SELECT student_id FROM student_career_goal WHERE job_category_code IS NULL);
