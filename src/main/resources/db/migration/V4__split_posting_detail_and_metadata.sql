-- Preserve every original value; narrowing rejects identifiers longer than 64 characters.
ALTER TABLE posting_employment_type DROP CONSTRAINT posting_employment_type_source_code_source_posting_id_fkey;
ALTER TABLE posting_job_category DROP CONSTRAINT posting_job_category_source_code_source_posting_id_fkey;
ALTER TABLE posting_region DROP CONSTRAINT posting_region_source_code_source_posting_id_fkey;
ALTER TABLE posting_specialty DROP CONSTRAINT posting_specialty_new_source_code_source_posting_id_fkey;
ALTER TABLE posting_tech_stack DROP CONSTRAINT posting_tech_stack_new_source_code_source_posting_id_fkey;
ALTER TABLE job_posting ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_employment_type ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_job_category ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_region ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_specialty ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_tech_stack ALTER COLUMN source_posting_id SET DATA TYPE VARCHAR(64);
ALTER TABLE posting_employment_type ADD CONSTRAINT posting_employment_type_source_code_source_posting_id_fkey FOREIGN KEY (source_code,source_posting_id) REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE;
ALTER TABLE posting_job_category ADD CONSTRAINT posting_job_category_source_code_source_posting_id_fkey FOREIGN KEY (source_code,source_posting_id) REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE;
ALTER TABLE posting_region ADD CONSTRAINT posting_region_source_code_source_posting_id_fkey FOREIGN KEY (source_code,source_posting_id) REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE;
ALTER TABLE posting_specialty ADD CONSTRAINT posting_specialty_new_source_code_source_posting_id_fkey FOREIGN KEY (source_code,source_posting_id) REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE;
ALTER TABLE posting_tech_stack ADD CONSTRAINT posting_tech_stack_new_source_code_source_posting_id_fkey FOREIGN KEY (source_code,source_posting_id) REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE;

CREATE TABLE job_posting_detail (
    source_code VARCHAR(32) NOT NULL,
    source_posting_id VARCHAR(64) NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    keywords TEXT,
    description TEXT,
    PRIMARY KEY (source_code,source_posting_id),
    CONSTRAINT fk_job_posting_detail_posting FOREIGN KEY (source_code,source_posting_id)
        REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE
);
CREATE TABLE job_posting_collection_metadata (
    source_code VARCHAR(32) NOT NULL,
    source_posting_id VARCHAR(64) NOT NULL,
    source_metadata TEXT,
    PRIMARY KEY (source_code,source_posting_id),
    CONSTRAINT fk_job_posting_metadata_posting FOREIGN KEY (source_code,source_posting_id)
        REFERENCES job_posting(source_code,source_posting_id) ON DELETE CASCADE
);
INSERT INTO job_posting_detail(source_code,source_posting_id,source_url,keywords,description)
    SELECT source_code,source_posting_id,source_url,keywords,description FROM job_posting;
INSERT INTO job_posting_collection_metadata(source_code,source_posting_id,source_metadata)
    SELECT source_code,source_posting_id,source_metadata FROM job_posting;
ALTER TABLE job_posting DROP COLUMN source_url;
ALTER TABLE job_posting DROP COLUMN keywords;
ALTER TABLE job_posting DROP COLUMN description;
ALTER TABLE job_posting DROP COLUMN source_metadata;
