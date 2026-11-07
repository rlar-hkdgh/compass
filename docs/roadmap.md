# 공고·갭 분석과 현재 추천 과제 — V10

공고 분석·갭 분석 테이블과 6축 공통 가중치 정책은 유지한다. 공고별 공용 분석, 학생·공고별 갭 분석, 판단 불가 UNASSESSABLE 및 NULL 점수 정책을 변경하지 않는다. AI 연동·점수 산정·추천 생성 API는 구현 범위가 아니다.

## 현재 추천 과제

career_roadmap, roadmap_period, student_active_roadmap, roadmap_task는 V10에서 제거하고 recommended_task만 유지한다. 상위 로드맵이나 별도 기간 테이블이 없다. 과제는 학생과 성공한 갭 분석에 직접 연결하며, gap_analysis → posting_analysis → job_posting으로 기준 공고를 확인한다.

recommended_task 컬럼은 id, student_id, gap_analysis_id, course_activity_id, career_activity_id, certification_code, certification_exam_schedule_id, title, description, academic_year, period_type, display_order, status, completed_at이다. activity_type_code, planned_start_on, planned_end_on, 추천 이유·예상 상승 점수·스냅샷·등록/수정 시각은 없다. 과제의 활동 유형은 연결된 대상에서 확인한다. 프로젝트 등 연결 없는 자체 과제도 허용한다.

학생별 현재 추천 결과만 저장한다. 공고 변경 또는 학기 경과 후 최신 사용자 입력으로 재생성한 결과가 완성되면 RecommendedTaskService.replace로 검증·교체한다. 학생 행 잠금으로 같은 학생의 교체·완료 처리를 직렬화하고, 검증/저장 실패 시 트랜잭션을 롤백한다. 지난 계획·완료 기록을 별도 이력 테이블에 보관하지 않는다. 갭 분석 이력은 별도 정책이며 이번에 삭제하지 않는다.

과제 완료는 updateStatus의 명시적 사용자 액션으로만 기록한다. 시간 경과나 추천 변경으로 완료를 추정하지 않는다. 과제 완료를 학생 프로젝트·인턴·자격증 취득이나 점수에 자동 반영하지 않는다. 목표 공고/학기 경과 감지·AI 재생성·HTTP API는 아직 연결하지 않았다.

활동 시작·종료 및 자격증 접수/시험 기간은 카탈로그에 있고, 과제는 수행 연도·학기/방학만 가진다. 추천 활동 관리 상세는 activities.md, 수집 범위는 activity-collection.md를 참조한다.

V10은 코드와 테스트로만 준비하며 실제 개발 DB 적용·기존 계획 삭제는 실행하지 않았다. 적용 전에 실행 상태와 실제 삭제/변환 건수를 확인해 알린다.
