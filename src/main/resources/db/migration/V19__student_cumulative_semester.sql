-- Keep previously stored semester values and allow the full count from a transcript.
ALTER TABLE student DROP COLUMN grade_year;
ALTER TABLE student DROP CONSTRAINT chk_student_semester;
ALTER TABLE student ADD CONSTRAINT chk_student_semester CHECK (semester >= 0);
