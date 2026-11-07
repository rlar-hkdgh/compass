# 학생 DB·입력 API — V16

학교 이메일 인증 후 가입하며 schoolEmail과 password로 세션 로그인한다. 성적표 미리보기·확인 저장, 프로젝트 README 수집과 온보딩 API를 제공한다.

학생 정보 화면은 PATCH /api/v1/me(이름, 편입 여부, 정규 이수학기 수 semester 0 이상, gpa/gpaScale)와 PUT /api/v1/me/majors(전공·이수 구분)를 사용한다. 평점은 만점 이하여야 한다.
목표 설정/수정 화면은 GET/PUT /api/v1/me/career-goal을 사용한다. jobCategoryCode(공고 공통 직무 코드), companySizes(1~4개), targetSalary(만원 단위 연봉), regionCodes, targetCompany, targetDate(YYYY-MM)가 필요하다. 지역은 기존 preferences와 공유한다. 성적·프로젝트·인턴·수상·자격증은 기존 개별 API로 입력하고 온보딩 상태를 별도로 저장한다. 진단·로드맵 생성은 목표 저장만으로 실행되지 않는다.
학과명은 전공 code로 연결한다.

## 테이블

- student: id, password_hash, school_email, name, is_transfer, semester(성적표의 정규 이수학기 수), gpa, gpa_scale.
- student_career_goal: student_id(PK), job_category_code(필수), target_salary, target_company, target_date(해당 월 1일).
- student_career_goal_company_size: student_id와 company_size의 복합 키로 복수 선택을 저장한다. 기존 단일 선택은 V18에서 한 항목으로 복사한다.
- major: code, name, college_name.
- major_completion_type: code, name.
- student_major: student_id, major_code, completion_type_code (복합 PK).
- student_preferred_job_category: student_id, job_category_code (복합 PK).
- student_preferred_region: student_id, region_code (복합 PK).
- student_preferred_employment_type: student_id, employment_type_code (복합 PK).
- student_salary_preference: student_id (PK), salary_unit_code, min_amount.
- student_course: id, student_id, course_number, course_name, academic_year, semester, evaluation_method, grade, grade_point.
- student_project: id, student_id, name, overview(필수), description(필수), outcome(선택), github_url.
- student_project_tech_stack: project_id, tech_stack_code (복합 PK, 기존 tech_stack 복수 선택).
- student_project_readme: project_id, repository_url, readme_path, readme_sha, content, last_success_at, last_attempt_at, last_attempt_status, last_error_code.
- student_internship: id, student_id, company_name, job_category_code, started_on, ended_on, description.
- student_award: id, student_id, name, organizer, description.
- certification: code, name, issuing_organization.
- student_certification: student_id, certification_code (복합 PK).

## 입력 원칙

- 완료된 프로젝트/인턴만 등록하는 정책이다. 진행 중 여부 컬럼은 없다. 프로젝트 개요는 overview, 본인 역할·수행 내용은 description, 성과는 선택 outcome으로 구분한다. 개요·수행 설명은 공백 불가이며 사용 기술은 기존 tech_stack 코드와 복수 연결한다. 별도 결과물 URL은 없다.
- 인턴은 프로젝트와 독립되며 사용 기술을 받지 않는다. 직무는 기존 job_category 코드 하나를 참조한다(현재 IT 직무 28개). 기간은 필수 DATE이고 시작일≤종료일은 DB CHECK, 미래 종료일 거절은 JPA @PastOrPresent 검증이다. 직접 SQL로 미래일 입력을 막는 동적 DB CHECK는 추가하지 않았다.
- 수상은 수상명·주최기관·설명만 받는다. 설명은 선택이며 등급·역할 등을 자유롭게 작성할 수 있다.
- 보유 자격증은 종류만 연결한다. 취득/만료일·등급·점수 필드는 없다. 종류별 현재 보유 연결 하나다.
- 졸업 예정 연월, 비교과, 부트캠프, 인턴 사용 기술 저장 구조는 제거했다. 프로젝트 사용 기술 연결은 V7에서 다시 추가했다.
- 학생·활동 ID는 BIGINT IDENTITY, 학교 이메일은 UNIQUE다. 삭제는 기존 RESTRICT 정책이며 자동 연쇄 삭제하지 않는다.
- 희망 직무·지역·고용형태·급여 단위는 공고 공용 코드 테이블을 재사용한다. 원 금액·단위를 유지한다.
- 수업의 학수번호 앞자리 0, 재수강별 기록, P/NP, NULL 평점은 유지한다.

## 코드 적재

- V5에 승인된 전공 62개와 자격증 9개를 적재한다. 전공 이수 구분 6개는 기존 V2를 유지한다.
- 전공 code는 2026-2 공식 시간표의 개설학과코드를 서비스 선택용 키로 사용한다. 학생 학적 코드와 동일하다고 표시하거나 수강기록으로 학생 전공을 추정하지 않는다.
- 컴퓨터공학과는 3513(인공지능융합대학), 구 소속 3210은 선택 목록에서 제외한다. 미확인 코드/명칭 변환/연계·융합전공은 임의 추가하지 않는다.
- 자격증 code는 서비스 내부 식별자다. QNET 뒤 숫자는 Q-net 종목코드이며 나머지를 국가 공통 공식 코드라고 주장하지 않는다. 리눅스 1급/2급은 별도 자격증 종류다.
- 목록과 출처는 docs/student-code-catalogs.md에 보존한다. 새 목록 추가/변경은 V6 이후 새 마이그레이션으로 한다.

## README

- 프로젝트별 선택적 1:1 저장 구조(V3)는 유지한다. 수집 전 행은 없다.
- readme_path/readme_sha/content/last_success_at은 마지막 성공본이며 모두 함께 채우거나 비운다. SHA는 README 파일 식별값이다.
- last_attempt_at/status/error_code는 마지막 시도 결과이며 실패 후 성공본 유지가 가능하다. URL 변경 시 수집 문서를 무효화하며 공개 GitHub README 수집 API를 제공한다.

## 마이그레이션·검증

- V1~V4는 변경하지 않는다. V5는 삭제/변환 대상에 데이터가 있으면 변경 전에 중단한다. 현재 개발 DB의 해당 데이터는 0건임을 사전 확인했다.
- 기존 데이터가 있는 다른 환경에서는 백업과 명시적인 변환 결정이 먼저 필요하다. 인턴 직무/기간을 추정하거나 과거 설명·자격증 정보를 조용히 삭제하지 않는다.
- StudentV5MigrationTests는 15개 테이블/시드/기존 공고 보존/직무 FK/기간/중복 자격증/변환 필요 시 중단을 검증한다. StudentV5PersistenceTests는 새 인턴·수상·자격증 JPA 연결과 미래 종료일 거절을 검증한다.

## V7 프로젝트 입력 구분
- overview는 목적·대상·핵심 기능, description은 본인의 역할·구현 내용·기여, outcome은 성과·개선 효과·배운 점이다.
- overview/description은 DB NOT NULL·공백 검사 및 JPA @NotBlank. outcome은 NULL 허용.
- student_project_tech_stack은 기존 기술 코드 복수 선택, 같은 프로젝트·기술 중복 불가. 기술 선택 최소 개수 제약은 없다.
- FK는 스칼라/EmbeddedId로 저장하고 객체 탐색은 읽기 전용. 삭제는 RESTRICT.
- V1~V6 변경 없음. V7은 기존 프로젝트가 있으면 스키마 변경 전 중단한다. 설명 분리·개요 추정·데이터 삭제 없음.
- 개발 DB 프로젝트 0건 확인 후 V6·V7 적용 완료. 기존 35개 업무 테이블의 건수·전체 값 지문 동일, Flyway/JPA validate 성공. 서버·수집기는 실행하지 않았다.
- 학생 API/AI 분석 연동은 추가하지 않는다. 사용 기술은 숙련도를 의미하지 않는다.


