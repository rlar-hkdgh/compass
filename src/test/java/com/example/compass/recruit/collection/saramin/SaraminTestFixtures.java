package com.example.compass.recruit.collection.saramin;
public final class SaraminTestFixtures {
    private SaraminTestFixtures() {}
    public static final String SUMMARY="""
        <div class="jv_header" data-rec_idx="123"><a class="company" href="/zf_user/company-info/view?csn=example">예시 회사</a>
        <h1 class="tit_job">백엔드 개발</h1><div class="btn_apply"><button data-rec_idx="123">지원</button></div></div>
        <div class="jv_summary"><dl><dt>경력</dt><dd>신입</dd></dl><dl><dt>학력</dt><dd>대졸(4년제) 이상</dd></dl>
        <dl><dt>급여</dt><dd>면접 후 결정</dd></dl><dl><dt>근무형태</dt><dd>정규직</dd></dl></div>
        <dl class="info_period"><dt>시작일</dt><dd>2026.10.01 00:00</dd><dt>마감일</dt><dd>2099.11.30 23:59</dd></dl>
        <div class="recommend"><a href="/zf_user/jobs/list/job-category?cat_kewd=92">추천 카드</a></div>
        <div class="jv_footer"><div class="tags"><a href="/zf_user/jobs/list/job-category?cat_mcls=2">IT개발·데이터</a><a href="/zf_user/jobs/list/job-category?cat_kewd=84">백엔드</a>
        <a href="/zf_user/jobs/list/domestic?loc_cd=101070">구로</a><a href="/zf_user/jobs/list/domestic?loc_cd=101050">관악</a></div></div>
        """;
    public static final String BODY="<div class='user_content'><h2>지원자격</h2><p>Java와 SQL 개발 경험을 요구합니다. 역할과 수행 내용을 구체적으로 검토합니다.</p>"
        +"<h2>우대사항</h2><p>PostgreSQL 사용 경험과 팀 코드 리뷰 경험을 우대합니다. 실제 테스트를 위한 가상 설명입니다.</p>"
        +"<script>SHOULD_NOT_BE_SAVED</script></div>";
}
