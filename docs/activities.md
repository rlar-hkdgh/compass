V12에서 사용하지 않는 certification.exam_content를 제거했다. 기존 V10 설명과 테스트는 마이그레이션 이력 검증용으로 보존한다.

## 2026-11-02 실제 Supabase 실행 결과

V9~V11을 sejong-compass에 적용하고 수업 217건·비교과 152건·인턴 20건·부트캠프 8건, 총 397건을 신규 저장했다. 기존 활동 갱신은 0건이며 같은 실행의 비교과 신규분 149건은 신청기간을 오프라인 재분석해 보완했다. 전체 저장 컬럼 대조의 누락·불일치·중복은 0건이다. 비교과 279페이지와 인턴 43페이지를 끝까지 탐색했다. 사용자의 후속 승인으로 요청 간격은 사이트별 3초이며 병행 실행했다. 비교과·인턴에는 지난 마감 이력이 포함된다. 로컬 DB는 미변경이다. 아래의 실행 전 미적용 설명은 설계 당시 기록으로 참고한다.

# 추천 활동·현재 추천 과제 — V10 구현, 개발 DB 미적용

## 최종 테이블

- course_activity: id, course_number, academic_year, semester, section_number, name, department_code, department_name, credits, description, source_url.
- certification: code, name, issuing_organization. URL 없음. 기존 9종 유지, 관리자 수동 SQL.
- certification_exam_schedule: id, certification_code, exam_year, round_number, exam_stage, application_starts_on, application_ends_on, starts_on, ends_on. URL·확인 시각·합격 발표일 없음. 날짜를 확인한 회차만 수동 입력. 미확인 접수일은 NULL, 하루 시험은 시작/종료 동일.
- career_activity: id, activity_type_code, source_code, source_activity_id, name, provider_name, description, source_url, application_start_at, application_end_at, starts_on, ends_on. 비교과·인턴·부트캠프만 저장하며 수업·자격증의 상위 테이블이 아니다.
- recommended_task: id, student_id, gap_analysis_id, course_activity_id, career_activity_id, certification_code, certification_exam_schedule_id, title, description, academic_year, period_type, display_order, status, completed_at.
- activity_type(code,name,display_order), activity_source(code,name,base_url) 코드 테이블 유지. 과제에 activity_type_code는 없다.

## 추천·저장 규칙

일반 활동의 설명에 학습 내용·직무·지원 조건 등 공식 본문을 보존한다. 유형별 과도한 상세 컬럼과 중복 raw_content, 수집 메타데이터, 활성 여부, 관리 시각은 제거한다. 활동별 역량 분석·예상 점수 상승·추천 이유 컬럼을 만들지 않는다.

현재 과제만 학생별로 유지한다. gap_analysis_id → posting_analysis → job_posting으로 대상 공고와 분석을 확인한다. 학생과 갭 분석 소유자는 복합 FK로 일치시킨다. 수업·일반 활동·자격증 연결은 최대 하나이며, 목표 시험은 자격증과 일치해야 한다. 연결이 없는 자체 프로젝트·기타 과제도 허용한다. 유형 코드와 과제 예정 시작/종료일, JSON 스냅샷은 없다. 학기/방학과 표시 순서로 계획을 표현한다.

RecommendedTaskService.replace는 완성된 AI 결과를 전달받는 저장 기능이다. 학생 행 잠금, 전체 입력·참조 검증 후 현재 과제를 같은 트랜잭션에서 교체한다. 실패하면 기존 과제를 유지한다. 과제 완료는 사용자 액션으로만 기록하며 학생 프로젝트·활동·점수를 자동 변경하지 않는다. AI 생성기·API·자동 갱신 스케줄은 이번 범위가 아니다.

## 마이그레이션·데이터 영향

V1~V9 파일은 수정하지 않는다. V10 Java 마이그레이션과 db/changes/activity-final-v10.sql을 추가했다. 별도 코드 검증 후 Flyway 적용이 필요하며, 이번에는 실제 로컬/Supabase DB에 적용하지 않았다.

V10 당시에는 수업·일반 활동 ID를 유지하고 자격증 기존 시험 설명을 임시 본문 컬럼으로 옮겼다. 해당 컬럼은 V12에서 제거했다. 비교과·인턴·부트캠프의 기존 상세 값은 공통 본문 뒤에 보존한다. 현재 활성 로드맵의 과제·완료 상태·연도/학기만 recommended_task로 옮긴다. 학생에게 활성 공고가 여러 개면 자동 선택하지 않고 적용을 중단한다.

기존 로드맵·기간·활성 연결·과제 테이블과 유형별 상세 테이블, 이전 계획 이력, 활동 스냅샷과 관리/중복 원문 컬럼은 제거 대상이다. 학생·공고·공고 분석·갭 분석과 실제 학생 수행 정보는 유지한다. 실제 적용 전 데이터 건수·삭제/변환 범위·실행 상태를 별도 확인하고 알린다. 이번 작업은 개발 DB의 데이터를 확인·삭제·변환하지 않았다.

현재 개발 DB가 V8이면 V9·V10 별도 적용 전 새 엔티티와 JPA validate가 일치하지 않는다. 서버를 임의 실행하지 않는다. 자격증 SQL 예시는 scripts/sql/manual-certifications.sql에 있으며 이번에는 실행하지 않았다.
