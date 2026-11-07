# 팀 개발용 데이터베이스 공유

기준: 2026-11-02. Supabase 프로젝트 sejong-compass(rqecthphhcvmnvxrbubg), 서울 리전. V1~V12 적용 성공.

## 저장 데이터

수업 217건, 비교과 152건, 인턴 20건, 부트캠프 8건(코드잇 4/솔데스크 4), 자격증 기본 9종, 자격증 일정 6건. exam_content는 V12로 제거했다. 비교과·인턴에는 지난 마감 이력이 포함된다. 날짜가 미확인이면 NULL이며 모집 중으로 간주하지 않는다.

## 대시보드에서 함께 보기

Supabase 조직 Team 설정에서 팀원의 개별 계정으로 초대한다. 가능한 최소 역할/범위를 선택한다. Developer도 프로젝트 데이터 변경이 가능한 개발 권한이므로 단순 열람자에게 무조건 주지 않는다. Read-Only와 프로젝트 한정 역할의 이용 가능 여부는 현재 요금제에서 확인한다. 계정 비밀번호를 공용으로 공유하지 않는다.

## 백엔드 연결

최신 코드 전체(V12 마이그레이션과 exam_content가 제거된 Certification 엔티티 포함)를 먼저 전달한다. 현재 변경은 로컬 미커밋 상태이며 이 작업에서는 커밋/푸시하지 않았다. 이전 코드로 접속하면 Hibernate validate 실패 또는 마이그레이션 불일치가 생길 수 있다. Git의 추적 파일만 복사하면 현재 untracked 구현/마이그레이션을 놓칠 수 있다.

application-supabase.yml과 docs/supabase.md에 서울 Session pooler의 호스트/포트/사용자명 및 SSL 설정이 있다. SPRING_PROFILES_ACTIVE=supabase, SUPABASE_DB_PASSWORD는 실행 시 숨김 입력 또는 비밀 관리 도구로만 전달한다. 프론트엔드 코드·메신저 단체방·Git·평문 설정 파일에 DB 비밀번호나 service_role 키를 넣지 않는다.

현재 연결 사용자는 데이터베이스 소유자 postgres.<project-ref>이며 데이터 및 스키마 변경 권한이 크다. 팀원의 일상 조회/쓰기에는 역할을 분리하는 편이 좋다. Flyway는 백엔드 시작 시 실행되므로 공유 DB에 새 마이그레이션을 적용할 담당자를 정해 중복 작업을 피한다. collector/import는 명시적으로 비활성화한 채 개발한다. 기본/로컬 프로필과 supabase 프로필을 함께 활성화하지 않는다.

## 브라우저에서 직접 Supabase API 사용

public의 모든 테이블은 RLS 활성, 정책 0개다. 일반 anon/authenticated 역할에 허용된 행 접근이 없으므로 publishable/anon 키만 전달해서 바로 조회할 수 있는 구성은 아니다. 현재는 백엔드 경유로 연결한다. 브라우저 직접 접근이 필요하면 실제 사용자별 권한에 맞는 정책 설계를 별도 수행한다. 편의를 위해 RLS를 끄거나 service_role 키를 브라우저에 넣지 않는다.

## 검증 범위

원격 스키마·행 수·마이그레이션 이력과 보안 진단을 확인했다. 이번 작업에서 팀원 초대/새 계정 발급/비밀번호 전달/서버 시작/팀원 PC에서의 실제 연결 확인은 하지 않았다. 로컬 DB·기존 백업·공유 덤프는 그대로 유지했다. 이번 신규 데이터는 기존 덤프에 자동 반영되지 않으므로 해당 덤프를 현재 상태로 오해하지 않는다.

공식 안내:
- https://supabase.com/docs/guides/platform/access-control
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.com/docs/guides/database/connecting-to-postgres
