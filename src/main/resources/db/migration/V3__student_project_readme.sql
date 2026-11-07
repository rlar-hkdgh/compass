CREATE TABLE student_project_readme (
    project_id BIGINT NOT NULL PRIMARY KEY,
    repository_url VARCHAR(2000) NOT NULL,
    readme_path TEXT,
    readme_sha VARCHAR(128),
    content TEXT,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_attempt_status VARCHAR(32) NOT NULL,
    last_error_code VARCHAR(64),
    CONSTRAINT fk_student_project_readme_project FOREIGN KEY (project_id)
        REFERENCES student_project(id) ON DELETE RESTRICT,
    CONSTRAINT chk_readme_attempt_status CHECK (CASE last_attempt_status WHEN 'SUCCESS' THEN TRUE WHEN 'NOT_FOUND' THEN TRUE WHEN 'RATE_LIMITED' THEN TRUE WHEN 'FAILED' THEN TRUE ELSE FALSE END),
    CONSTRAINT chk_readme_success_snapshot CHECK (
        (last_success_at IS NULL AND readme_path IS NULL AND readme_sha IS NULL AND content IS NULL)
        OR (last_success_at IS NOT NULL AND readme_path IS NOT NULL AND readme_sha IS NOT NULL AND content IS NOT NULL)
    ),
    CONSTRAINT chk_readme_success_result CHECK (
        last_attempt_status <> 'SUCCESS' OR (last_success_at IS NOT NULL AND last_error_code IS NULL)
    )
);
