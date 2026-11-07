-- Existing descriptions cannot be safely split into an overview automatically.
-- Stop before any schema change when existing projects require manual backfill.
CREATE TABLE v7_project_data_guard (affected_rows BIGINT NOT NULL CHECK (affected_rows=0));
INSERT INTO v7_project_data_guard SELECT count(*) FROM student_project;
DROP TABLE v7_project_data_guard;

ALTER TABLE student_project ADD COLUMN overview TEXT NOT NULL;
ALTER TABLE student_project ADD COLUMN outcome TEXT;
ALTER TABLE student_project ADD CONSTRAINT chk_project_overview_nonblank CHECK(length(trim(overview))>0);
ALTER TABLE student_project ADD CONSTRAINT chk_project_description_nonblank CHECK(length(trim(description))>0);

CREATE TABLE student_project_tech_stack (
    project_id BIGINT NOT NULL,
    tech_stack_code VARCHAR(32) NOT NULL,
    PRIMARY KEY(project_id,tech_stack_code),
    FOREIGN KEY(project_id) REFERENCES student_project(id) ON DELETE RESTRICT,
    FOREIGN KEY(tech_stack_code) REFERENCES tech_stack(code) ON DELETE RESTRICT
);
CREATE INDEX idx_student_project_tech_stack_code ON student_project_tech_stack(tech_stack_code);
