# 공고 도메인

## 저장 구조

- PostgreSQL / Java 17 / Spring Boot / JPA / Flyway.
- 공고 식별자는 `source_code + source_posting_id` 복합키다.
- 회사는 내부 BIGINT ID로 식별한다. 회사명만으로 자동 병합하지 않는다.
- `job_category`: 실제 IT 직무 28개.
- `specialty`: 전문분야 92개.
- `tech_stack`: 기술스택 140개.
- 세 기준 테이블은 사람인 원본 코드와 명칭을 보존한다. `category_type` 대신 소속 테이블로 구분한다.
- `posting_job_category`, `posting_specialty`, `posting_tech_stack`은 각각 해당 기준 테이블을 참조한다. 공고별 복수 연결을 허용한다.
- 지역도 `posting_region`으로 복수 연결한다.
- 고용형태는 `posting_employment_type`으로 M:N 연결한다. 학력·경력·마감방식은 공고에 단일 FK를 둔다. 급여는 지급 단위·결정 방식 코드와 최소·최대 금액으로 분리한다. 미확보 값은 NULL이며 임의 코드를 넣지 않는다.
- 업종 테이블과 공고의 업종 FK, 원본 게시일·수정일은 제거했다. 수집일 컬럼도 추가하지 않는다.
- 수집 메타데이터의 `collectedAt`은 원본 처리 기록으로 유지한다.
- 공고 원문은 `job_posting_detail.description` TEXT에 저장하고 상세 응답에서만 제공한다.
- 목록은 출처와 공고번호 기준으로 정렬한다. 게시일 순서를 보장하지 않는다.

## 실행

JDK 17을 설치하고 IntelliJ의 Project SDK와 Gradle JVM을 JDK 17로 지정합니다.
프로젝트 루트의 실제 .env는 Spring Boot가 직접 읽으며 Git에 포함하지 않습니다.
공유 실행 설정 CompassApplication를 선택하고 실행 버튼을 누릅니다.
Supabase 프로필로 실행하며 자동 마이그레이션·수집기는 비활성화됩니다.

PowerShell에서 격리 테스트와 빌드 검증:

    .\gradlew.bat test bootJar --console=plain

Flyway를 유지하며 현재 완성된 구조를 새 기준으로 관리한다.
- `V1__schema.sql`: 이전 V17까지의 최종 업무 테이블 37개, 제약조건, 인덱스.
- `V2__seed.sql`: 확정된 기준 데이터만 INSERT. 회사·공고·학생 데이터와 전공·자격증 시드는 포함하지 않는다.
- 이후 변경은 V3부터 추가하고 적용된 파일은 수정하지 않는다.
- 기존 개발 DB는 스키마와 업무 데이터를 유지한 채 동등성 검증 후 Flyway 이력만 V1·V2로 전환했다. 과거 데이터의 `source_metadata.legacySalaryType`은 유지된다.
- 기존 V1~V17 및 Java 데이터 이관 마이그레이션은 새 배포에서 제거했다. 과거 DB에 새 파일을 그대로 실행하지 말고 먼저 동등성 검증과 이력 전환을 수행해야 한다.
스키마 변경 전 기존 웹 서버와 수집기를 중단하고, 변경 후 새 빌드로 재시작한다.

## API

| Method | URL | 설명 |
|---|---|---|
| GET | /api/v1/recruit-codes/{type} | 기준 코드 목록 |
| POST | /internal/recruit/companies | 회사 등록, local 전용 |
| PUT | /internal/recruit/job-postings/{source}/{sourcePostingId} | 전체 저장·교체, local 전용 |

코드 type: `sources`, `job-categories`, `specialties`, `tech-stacks`, `regions`, `employment-types`, `education-levels`, `salary-units`, `salary-decisions`, `experience-types`, `deadline-types`.

PUT에서 생략한 값은 기존 값을 지운다.
요청의 `codes.employmentTypes`는 코드 문자열 배열이고, 응답의 `posting.employmentTypes`는 코드·명칭 객체 배열이다. 미확보 시 빈 배열이며 PUT 생략 시 연결을 비운다. 기존 단일 `employmentType` 및 경력 연차 필드는 제거했다.
요청 예제는 `docs/examples/job-posting.json`을 참고한다.

## 수집기

```powershell
.\gradlew.bat bootJar '-Dorg.gradle.java.installations.paths=C:/Program Files/JAVA/jdk-17,C:/Program Files/JAVA/jdk-21'
.\scripts\crawl-saramin.ps1 -StartPage 1 -MaxPages 1
```

- MaxPages 0은 마지막 페이지까지 순회한다. 목록 페이지는 100개 단위다.
- 공개 IT 목록에서 신입과 경력무관 검색 조건으로 후보를 찾고 상세 페이지를 수집한다. 사람인 API는 호출하지 않는다.
- 루트 목록: https://www.saramin.co.kr/zf_user/jobs/list/job-category?cat_mcls=2&exp_cd=1&exp_none=y&search_optional_item=y&page=1&page_count=100&sort=RD&isAjaxRequest=0
- 사람인 신입 필터(exp_cd=1)는 신입·경력 동시 모집도 포함하고, 경력무관은 별도 조건(exp_none=y)으로 포함한다. 이후 페이지도 같은 조건을 유지한다. 목록 검색은 후보 축소용이며 개별 공고의 IT 원본 태그·신입 지원 가능 여부 검증은 그대로 수행한다.
- 공고 원본 태그에 대분류 cat_mcls=2가 있고 확인된 IT 직무·기술스택·전문분야 중 하나 이상이 있는 경우 저장한다. 기술스택이나 전문분야만 있으면 직무 연결은 비워 둔다. 두 조건 중 하나라도 없으면 제외한다. IT 직무가 여러 개면 모두 연결한다. IT 수집 조건을 만족하면 영업·마케팅·홍보 등 비 IT 직무가 섞여 있어도 저장한다. 직무 연결에는 확인된 IT 직무만 넣고, 비 IT 직무 원본 코드와 전체 본문은 보존한다. 모집 부문별 자격요건 분리 여부는 현재 판별하거나 제외하지 않는다. 기존 저장 공고의 일괄 삭제·수정은 하지 않으며 동일 식별자 재수집은 기존처럼 갱신한다. 목록 검색 조건만으로 개별 공고의 대분류를 추정하지 않는다.
- 위 IT 조건에 더해 원본 경력 요약이 신입 / 신입·경력 동시 모집 / 경력무관인 공고만 수집한다. 경력 전용·누락·미확인 표현은 제외한다. 원본 경력 요약은 메타데이터에 보존하며 `experience_type`은 각각 1 / 3 / 0을 사용한다. 기존 경력 공고를 자동 삭제하지 않는다.
- 고용형태의 수습기간·계약기간 부가 설명을 분리하고, 복수 고용형태는 모두 연결한다. 정규직 전환 가능은 기존 전환형 코드(계약직 10, 인턴직 11)를 사용한다. 미확인 표현은 추정하지 않고 메타데이터에 보존한다. 기존 NULL은 재수집 시 보완한다.
- 전문분야와 기술스택도 각각의 연결 테이블에 저장한다.
- 미확인 키워드는 임의 분류하지 않고 원본 코드·명칭을 메타데이터에 보존한다.
- `saramin-keyword-categories.json`은 비 IT 그룹 판별을 위한 원본 분류 자료다.
- 수집 요청 간 최소 간격을 유지한다. 인증·차단 우회나 무한 재시도는 하지 않는다.
- 공고별 독립 트랜잭션으로 저장한다. 동일 식별자 재수집은 갱신한다.
- 공고 데이터 검증 오류와 404/410은 건너뛴다. 그 외 통신·DB 오류는 배치를 중단한다.
- 회사 정보 URL이 없는 경우 NULL을 허용한다.
- AI 분석·추천·정기 스케줄링은 아직 연결하지 않았다.
- 공고 본문 PaddleOCR 보완은 선택 활성화한다(기본 꺼짐). 혼합형·긴 이미지·작은 글씨 처리와 제한은 docs/ocr.md 참고. OCR 문구는 급여·마감일 자동 확정에 사용하지 않는다.

## 연결 조회 예제

```sql
SELECT p.source_posting_id, j.code, j.name
FROM job_posting p
JOIN posting_job_category x USING (source_code, source_posting_id)
JOIN job_category j ON j.code = x.job_category_code
WHERE p.source_posting_id = '55446377';

SELECT x.source_posting_id, s.code, s.name
FROM posting_specialty x
JOIN specialty s ON s.code = x.specialty_code
WHERE x.source_posting_id = '55446377';

SELECT x.source_posting_id, t.code, t.name
FROM posting_tech_stack x
JOIN tech_stack t ON t.code = x.tech_stack_code
WHERE x.source_posting_id = '55446377';
```

## 검증

`gradlew.bat test`로 API·파싱·페이지 순회·마이그레이션을 검증한다.
BaselineSchemaTests는 V1 스키마 생성, V2 기준 데이터, 재실행·체크섬 검증 및 참조·급여 제약을 확인한다.

## 학력·급여

- `education_level_code`는 유지하고 `education_note` TEXT를 추가했다. API 요청 및 목록·상세 응답 필드는 `educationNote`다.
- 학력의 예정자·휴학생 가능 설명은 note로 분리한다. 부문별 상이, 학력 또는 경력, 미확인 복합 조건은 학력 코드 NULL 및 note에 전체 조건을 보존한다.
- 급여 테이블: `salary_unit`(ANNUAL / MONTHLY / WEEKLY / DAILY / HOURLY / PER_TASK), `salary_decision`(DISCLOSED / INTERVIEW / COMPANY_POLICY). 이 코드는 서비스 내부 코드이며 사람인 금액 구간 코드가 아니다.
- 공고 컬럼: `salary_unit_code`, `salary_decision_code`, `salary_min_amount`, `salary_max_amount`. 금액은 BIGINT, 원 단위다. 월급·시급을 연봉으로 환산하지 않는다.
- PUT 코드 필드: `codes.salaryUnit`, `codes.salaryDecision`. 금액 필드: `salaryMinAmount`, `salaryMaxAmount`. 목록 항목·상세의 posting에는 같은 이름의 필드를 제공하며 코드 응답은 code/name 객체다.
- 정확한 금액은 min=max, 이상은 min만, 이하는 max만, 범위는 양쪽을 저장한다. 금액이 있으면 단위와 DISCLOSED가 필요하다. 면접 후 결정·회사 내규는 금액을 임의로 채우지 않는다.
- 음수·역전 범위·금액과 결정 방식 충돌·미등록 코드는 거절한다. 파싱 불명확·단위 누락·오버플로 금액은 추정하지 않는다.
- 학력·급여의 원본 전체 문구와 주당 근무시간 등은 `source_metadata.summary`에 그대로 보존한다. 수집 분류 결과도 educationMapping / salaryMapping에 보존한다.
- 기존 salaryType API 필드와 salary-types 코드 API는 제거했다. PUT에서 생략한 새 조건 필드는 기존 값을 비운다.




## 공고 목록/상세/수집 자료 분리 (V4)

- `job_posting`: 식별자, 회사, 제목, 활성 여부, 접수기간, 학력·경력·급여·마감 조건. 목록은 이 엔티티와 기존 코드 연결만 읽는다.
- `job_posting_detail`: 동일 복합키 PK/FK, source_url, keywords, description. 상세 조회/저장 때 명시적으로 읽고 쓴다.
- `job_posting_collection_metadata`: 동일 복합키 PK/FK, source_metadata(TEXT). 수집 저장 때 명시적으로 읽고 쓴다. 목록/상세 응답에는 노출하지 않는다.
- source_posting_id는 기본·상세·메타데이터·기존 연결 5개 테이블 및 Java 복합키/서비스 입력 검증에서 VARCHAR(64)로 통일한다.
- V4는 기존 값을 복사한 뒤 원래 큰 필드를 제거한다. V1~V3는 수정하지 않는다. API 응답·수집 정책·메타데이터 원문·회사 재사용 기준은 유지한다.
- 공고의 기존 연결 테이블과 동일하게 새 종속 테이블 FK는 ON DELETE CASCADE다. 삭제 API/일괄 삭제 작업은 추가하지 않는다.

개발 DB V3/V4 적용 완료: 기존 공고 2,217건의 전체 필드와 기존 연결 데이터 지문 일치, PostgreSQL 17.11 Flyway/JPA 검증 완료. 기존 웹 서버는 전환 전에 종료했고 새 서버/수집기를 자동 기동하지 않았다.
