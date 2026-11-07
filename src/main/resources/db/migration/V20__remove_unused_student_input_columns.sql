-- Reject unresolved legacy goals before removing any columns.
ALTER TABLE student_career_goal ALTER COLUMN job_category_code SET NOT NULL;

-- Preserve a single selection if it is absent from the V18 selection table.
INSERT INTO student_career_goal_company_size (student_id, company_size)
SELECT goal.student_id, goal.company_size
FROM student_career_goal goal
WHERE goal.company_size IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM student_career_goal_company_size selection
    WHERE selection.student_id = goal.student_id
  );

ALTER TABLE student_career_goal DROP CONSTRAINT chk_career_goal_job_selection;
ALTER TABLE student_career_goal DROP COLUMN job_field;
ALTER TABLE student_career_goal DROP COLUMN career_type;
ALTER TABLE student_career_goal DROP COLUMN target_role;
ALTER TABLE student_career_goal DROP COLUMN company_size;

ALTER TABLE student DROP COLUMN login_id;
ALTER TABLE student DROP COLUMN student_number;
