-- 관리자용 직접 SQL 예시. 자동 실행/수집/파일 업로드 기능이 아니다.
-- 실제 DB에는 아직 실행하지 않았다. V9와 V10을 별도 승인·적용한 뒤,
-- 공식 자료를 확인해 아래 바인드 매개변수 값을 채우고 필요한 문장만 실행한다.
-- 자격증 기본 정보와 일정에는 source_url이 없다. 합격 발표일도 저장하지 않는다.
-- 대상 기존 9종:
-- QNET_1320, QNET_2290, KDATA_SQLD, KDATA_SQLP, KDATA_ADSP, KDATA_ADP,
-- KAIT_LINUX_1, KAIT_LINUX_2, ICQA_NETWORK_2.


-- 접수일은 미확인이면 NULL. 확정 시험일/기간만 입력한다.
-- 하루 시험은 starts_on = ends_on. 단계: WRITTEN/PRACTICAL/FIRST/SECOND/SINGLE.
INSERT INTO certification_exam_schedule
    (certification_code, exam_year, round_number, exam_stage,
     application_starts_on, application_ends_on, starts_on, ends_on)
VALUES
    (:certification_code, :exam_year, :round_number, :exam_stage,
     :application_starts_on, :application_ends_on, :starts_on, :ends_on)
ON CONFLICT (certification_code, exam_year, round_number, exam_stage)
DO UPDATE SET
    application_starts_on = EXCLUDED.application_starts_on,
    application_ends_on = EXCLUDED.application_ends_on,
    starts_on = EXCLUDED.starts_on,
    ends_on = EXCLUDED.ends_on;
