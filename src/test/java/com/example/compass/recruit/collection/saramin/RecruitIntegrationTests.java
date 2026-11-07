package com.example.compass.recruit.collection.saramin;
import com.example.compass.recruit.entity.JobPostingDetail;
import com.example.compass.recruit.entity.JobPostingCollectionMetadata;
import com.example.compass.recruit.service.RecruitService;
import com.example.compass.recruit.collection.ocr.PostingBodyOcrTests;
import com.example.compass.recruit.entity.Company;
import com.example.compass.recruit.entity.JobCategory;
import com.example.compass.recruit.collection.ocr.PostingBodyOcr;
import com.example.compass.recruit.entity.PostingId;
import com.example.compass.recruit.repository.PostingRepository;
import com.example.compass.recruit.controller.RecruitController;
import com.example.compass.recruit.exception.RecruitException;
import com.example.compass.recruit.exception.RecruitExceptionHandler;
import com.example.compass.recruit.controller.RecruitImportController;
import com.example.compass.recruit.collection.saramin.SaraminCollectedPostingStore;
import com.example.compass.recruit.collection.saramin.SaraminCrawler;
import com.example.compass.recruit.collection.saramin.SaraminCrawlRunner;
import com.example.compass.recruit.collection.saramin.SaraminKeywordClassifier;
import com.example.compass.recruit.collection.saramin.SaraminPageParser;
import com.example.compass.recruit.entity.Specialty;
import com.example.compass.recruit.entity.TechStack;
import com.example.compass.recruit.dto.request.CompanyRequest;
import com.example.compass.recruit.dto.response.CodeResponse;
import com.example.compass.recruit.dto.request.CodeSelection;
import com.example.compass.recruit.dto.request.PostingRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers=com.example.compass.TestDatabase.class)
@Transactional
class RecruitIntegrationTests {
    @Autowired RecruitService service;
    @Autowired PostingRepository postings;
    @Autowired RecruitController controller;
    @Autowired RecruitImportController importer;
    private MockMvc mvc;
    private long companyId;

    @BeforeEach void setup() {
        var validator=new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc=MockMvcBuilders.standaloneSetup(controller,importer)
            .setControllerAdvice(new RecruitExceptionHandler()).setValidator(validator).build();
        companyId=service.createCompany(new CompanyRequest("테스트 기업",null)).id();
    }

    private PostingRequest request(String title,String jobCode) {
        return new PostingRequest(companyId,title,"https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=123",
            true,null,null,"Java,SQL","자격요건: Java 경험",null,null,null,
            new CodeSelection(java.util.List.of("101070"),java.util.List.of("1"),java.util.List.of(jobCode),"8","2",null,"INTERVIEW","3",java.util.List.of(),java.util.List.of()));
    }

    @Test void officialCodesAreSeededWithZeroAndSpecialValues() {
        assertThat(service.codes("job-categories")).contains(new CodeResponse("84","백엔드/서버개발"));
        assertThat(service.codes("salary-decisions")).contains(new CodeResponse("INTERVIEW","면접 후 결정"));
        assertThat(service.codes("experience-types")).contains(new CodeResponse("0","경력무관"));
        assertThat(service.codes("regions")).hasSize(677);
    }

    @Test void sameCompositeKeyUpdatesAndDifferentSourceDoesNotCollide() {
        service.save("SARAMIN","123",request("처음","84"));
        service.save("SARAMIN","123",request("수정","84"));
        service.save("UDREAM","123",request("유드림","84"));
        assertThat(postings.count()).isEqualTo(2);
        assertThat(service.get("saramin","123").posting().title()).isEqualTo("수정");
        assertThat(service.get("UDREAM","123").posting().title()).isEqualTo("유드림");
    }

    @Test void storedPostingsCanBeFilteredAndRetainRawDescription() {
        service.save("SARAMIN","123",request("백엔드","84"));
        service.save("SARAMIN","456",request("프론트엔드","92"));
        var page=service.list(0,20,null,"84","101070",null);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).jobCategories()).containsExactly(new CodeResponse("84","백엔드/서버개발"));
        assertThat(service.get("SARAMIN","123").description()).isEqualTo("자격요건: Java 경험");
    }

    @Test void rejectsUnknownCodesWithoutInserting() {
        assertThatThrownBy(()->service.save("SARAMIN","bad",request("공고","NOT_A_CODE")))
            .isInstanceOf(RecruitException.class).hasMessageContaining("없는 코드");
        assertThat(postings.count()).isZero();
    }

    @Test void publicPostingRoutesAreNotMappedAndCodeLookupRemainsAvailable() throws Exception {
        mvc.perform(get("/api/v1/job-postings")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/job-postings/SARAMIN/123")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recruit-codes/job-categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/regions")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/unknown")).andExpect(status().isNotFound());
    }

    @Test void importValidatesFieldsAndPersistsJson() throws Exception {
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/789")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"companyId":%d,"title":"테스트 공고","sourceUrl":"https://example.com/jobs/789",
                 "active":true,"codes":{"jobCategories":["84"],"salaryDecision":"INTERVIEW"}}
                """.formatted(companyId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.salaryDecision.code").value("INTERVIEW"));
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/bad")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test void companyNameAloneDoesNotMergeCompanies() {
        long other=service.createCompany(new CompanyRequest("테스트 기업",null)).id();
        assertThat(other).isNotEqualTo(companyId);
    }

    @Test void rejectsReversedApplicationRanges() {
        var good=request("공고","84");
        var badDates=new PostingRequest(companyId,good.title(),good.sourceUrl(),true,
            Instant.parse("2026-11-02T00:00:00Z"),Instant.parse("2026-11-01T00:00:00Z"),
            null,null,null,null,null,good.codes());
        assertThatThrownBy(()->service.save("SARAMIN","bad",badDates)).isInstanceOf(RecruitException.class);
    }
    @Autowired SaraminCollectedPostingStore collectedStore;
    @Test void collectedPostingUpsertsWithoutDuplicateCompanyAndPreservesMultipleCodes() {
        var data=SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,SaraminCrawlerTests.BODY,
            "https://www.saramin.co.kr/zf_user/jobs/relay/view-detail?rec_idx=123");
        var first=collectedStore.save(data);
        var second=collectedStore.save(data);
        assertThat(second.posting().company().id()).isEqualTo(first.posting().company().id());
        assertThat(second.posting().jobCategories().get(0).code()).isEqualTo("84");
        assertThat(second.posting().regions()).extracting(CodeResponse::code).containsExactly("101050","101070");
        assertThat(second.posting().educationLevel().code()).isEqualTo("8");
        assertThat(second.posting().salaryDecision().code()).isEqualTo("INTERVIEW");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata())
            .contains("101070","101050","collectedAt");
    }
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Test void multipleJobsUpdateWithoutDuplicateCompany() {
        var valid=SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,SaraminCrawlerTests.BODY,"unused");
        var first=collectedStore.save(valid);
        long companies=entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult();
        String multiple=SaraminCrawlerTests.SUMMARY.replace("<div class=\"tags\">",
            "<div class=\"tags\"><a href='/zf_user/jobs/list/job-category?cat_kewd=92'>프론트엔드</a>");
        var result=collectedStore.save(SaraminPageParser.parse("123",multiple,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().jobCategories()).extracting(CodeResponse::code).containsExactly("84","92");
        assertThat(result.posting().company().id()).isEqualTo(first.posting().company().id());
        assertThat(entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult()).isEqualTo(companies);
    }
    @Test void immediateApplyAndMixedCareerAreParsedFromPublicPage() {
        String summary=SaraminCrawlerTests.SUMMARY.replace(" data-rec_idx=\"123\">지원",">입사지원")
            .replace("신입","신입/경력 5~8년").replace("대졸(4년제) 이상","고교졸업 이상");
        var data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused");
        var result=collectedStore.save(data);
        assertThat(result.posting().active()).isTrue();
        assertThat(result.posting().educationLevel().code()).isEqualTo("6");
        assertThat(result.posting().experienceType().code()).isEqualTo("3");
    }
    @Test void multipleRegionsAreReturnedFilteredAndReplacedWithoutDuplicates() {
        var base=request("복수 지역 공고","84");
        var c=base.codes();
        var multi=new PostingRequest(base.companyId(),base.title(),base.sourceUrl(),true,
            null,null,null,null,
            null,null,null,
            new CodeSelection(java.util.List.of("101050","101070","101050"),c.employmentTypes(),
                c.jobCategories(),c.educationLevel(),c.deadlineType(),c.salaryUnit(),c.salaryDecision(),c.experienceType(),c.specialties(),c.techStacks()));
        service.save("SARAMIN","multi-region",multi);
        assertThat(service.get("SARAMIN","multi-region").posting().regions()).extracting(CodeResponse::code)
            .containsExactly("101050","101070");
        for(String region:java.util.List.of("101050","101070")) {
            var page=service.list(0,20,null,null,region,null);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content().get(0).regions()).hasSize(2);
        }
        service.save("SARAMIN","multi-region",base);
        assertThat(service.list(0,20,null,null,"101050",null).totalElements()).isZero();
        assertThat(service.list(0,20,null,null,"101070",null).totalElements()).isEqualTo(1);
    }
    @Test void deadlineKeepsEvidenceWithinPostingContent() {
        String summary=SaraminCrawlerTests.SUMMARY
            +"<div class='jv_company'><dl><dt>업종</dt><dd>포털 및 기타 인터넷 정보매개 서비스업</dd></dl></div>";
        var data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY+"<p>해당 공고는 수시 채용으로 조기에 마감될 수 있습니다.</p>","unused");
        // The extra sentence must be in the user-content scope, not in unrelated page furniture.
        assertThat(data.deadlineTypeCode()).isEqualTo("1");
        data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY.replace("</div>","<p>해당 공고는 수시 채용입니다.</p></div>"),"unused");
        var result=collectedStore.save(data);
        assertThat(result.deadlineType().code()).isEqualTo("4");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata())
            .contains("수시 채용")
            .doesNotContain("industryMapping");
    }
    @Test void industryLookupIsNotExposed() throws Exception {
        mvc.perform(get("/api/v1/recruit-codes/industries")).andExpect(status().isNotFound());
    }
    @Test void paginatedCollectionWritesBothPagesToTheDatabase() throws Exception {
        var pages=new SaraminPaginationTests.Pages();
        pages.data.put(1,new SaraminCrawler.ListingPage(1,java.util.List.of("9123"),true));
        pages.data.put(2,new SaraminCrawler.ListingPage(2,java.util.List.of("9456"),false));
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.main.web-application-type","none");
        new SaraminCrawlRunner(pages,collectedStore,env,new org.springframework.context.support.GenericApplicationContext())
            .run(new org.springframework.boot.DefaultApplicationArguments());
        assertThat(postings.findById(new PostingId("SARAMIN","9123"))).isPresent();
        assertThat(postings.findById(new PostingId("SARAMIN","9456"))).isPresent();
        assertThat(service.list(0,20,"SARAMIN","84",null,null).totalElements()).isEqualTo(2);
    }
    @Autowired SaraminKeywordClassifier keywordClassifier;
    @Test void databaseKeepsOfficialRoleSpecialtyAndTechnologyTypes() {
        assertThat(entityManager.createQuery("select count(c) from JobCategory c",Long.class).getSingleResult()).isEqualTo(28);
        assertThat(entityManager.createQuery("select count(c) from Specialty c",Long.class).getSingleResult()).isEqualTo(92);
        assertThat(entityManager.createQuery("select count(c) from TechStack c",Long.class).getSingleResult()).isEqualTo(140);
    }
    @Test void oneActualRoleWithMultipleTechnicalKeywordsIsSaved() {
        String summary=SaraminCrawlerTests.SUMMARY.replace("<div class=\"tags\">",
            "<div class=\"tags\"><a href='/zf_user/jobs/list/job-category?cat_kewd=235,201,214,122'>기술/전문분야</a>");
        var data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused");
        var saved=collectedStore.save(data);
        assertThat(saved.posting().jobCategories().get(0).code()).isEqualTo("84");
        assertThat(saved.keywords()).contains("Java","AWS","Docker","알고리즘");
        assertThat(saved.posting().specialties()).extracting(CodeResponse::code).containsExactly("122");
        assertThat(saved.posting().techStacks()).extracting(CodeResponse::code).containsExactly("201","214","235");
        var classified=keywordClassifier.classify(data.codes().get("cat_kewd"));
        assertThat(classified.roles()).containsExactly("84");
        assertThat(classified.technologies()).containsExactlyInAnyOrder("235","201","214");
        assertThat(classified.specialties()).containsExactly("122");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata())
            .contains("roleCodes","technologyCodes","specialtyCodes");
    }
    @Test void itMajorAllowsRoleTechnologyOrSpecialty() {
        var accepted=java.util.Map.of("cat_mcls",java.util.List.of("2"),"cat_kewd",java.util.List.of("84","235","92"));
        assertThat(keywordClassifier.eligibleItRoles(accepted)).containsExactly("84","92");
        for(var rejected:java.util.List.of(
                java.util.Map.of("cat_mcls",java.util.List.of("2")),
                java.util.Map.of("cat_mcls",java.util.List.of("2"),"cat_kewd",java.util.List.of("999999")),
                java.util.Map.of("cat_mcls",java.util.List.of("3"),"cat_kewd",java.util.List.of("84")),
                java.util.Map.of("cat_kewd",java.util.List.of("84")))) {
            assertThatThrownBy(()->keywordClassifier.eligibleItRoles(rejected)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(keywordClassifier.eligibleItRoles(java.util.Map.of("cat_mcls",java.util.List.of("2"),
            "cat_kewd",java.util.List.of("2230","122")))).isEmpty();
        var specialtyData=SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY.replace("cat_kewd=84", "cat_kewd=2230"),SaraminCrawlerTests.BODY,"unused");
        var specialtySaved=collectedStore.save(specialtyData);
        assertThat(specialtySaved.posting().jobCategories()).isEmpty();
        assertThat(specialtySaved.posting().specialties()).extracting(CodeResponse::code).containsExactly("2230");
        String summary=SaraminCrawlerTests.SUMMARY.replace("cat_kewd=84", "cat_kewd=235");
        var data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused");
        var saved=collectedStore.save(data);
        assertThat(saved.posting().jobCategories()).isEmpty();
        assertThat(saved.posting().techStacks()).extracting(CodeResponse::code).containsExactly("235");
        assertThat(postings.count()).isEqualTo(1);
    }
    @Test void atLeastOneKnownItRoleIsRequiredAndOtherTagsDoNotVetoIt() {
        assertThat(keywordClassifier.itRoles(java.util.List.of("84","92","235"))).containsExactly("84","92");
        assertThat(keywordClassifier.itRoles(java.util.List.of("84","1625","999999"))).containsExactly("84");
        for(var codes:java.util.List.of(java.util.List.of("235","201"),java.util.List.of("1625"),java.util.List.of("999999")))
            assertThatThrownBy(()->keywordClassifier.itRoles(codes)).isInstanceOf(IllegalArgumentException.class);
        assertThat(postings.count()).isZero();
    }
    @Test void keywordLinksDeduplicateAndReplaceOnImport() throws Exception {
        String body="""
            {"companyId":%d,"title":"연결 테스트","sourceUrl":"https://example.com/jobs/tags","active":true,
             "codes":{"jobCategories":["84"],"specialties":["122","122"],"techStacks":["201","214","201"]}}
            """.formatted(companyId);
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/tags").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.specialties.length()").value(1))
            .andExpect(jsonPath("$.posting.techStacks.length()").value(2));
        assertThat(service.get("SARAMIN","tags").posting().specialties()).extracting(CodeResponse::code).containsExactly("122");
        String clear=body.replace("[\"122\",\"122\"]","[]").replace("[\"201\",\"214\",\"201\"]","[]");
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/tags").contentType(MediaType.APPLICATION_JSON).content(clear))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.specialties.length()").value(0))
            .andExpect(jsonPath("$.posting.techStacks.length()").value(0));
        assertThat(((Number)entityManager.createNativeQuery("select count(*) from posting_specialty").getSingleResult()).intValue()).isZero();
        assertThat(((Number)entityManager.createNativeQuery("select count(*) from posting_tech_stack").getSingleResult()).intValue()).isZero();
    }
    @Test void keywordLinksRejectWrongClassificationAndUnknownCode() throws Exception {
        for(String fields:java.util.List.of("\"specialties\":[\"84\"]","\"techStacks\":[\"122\"]","\"techStacks\":[\"999999\"]")) {
            String body="""
                {"companyId":%d,"title":"잘못된 연결","sourceUrl":"https://example.com/jobs/tags","active":true,
                 "codes":{"jobCategories":["84"],%s}}
                """.formatted(companyId,fields);
            mvc.perform(put("/internal/recruit/job-postings/SARAMIN/bad-tags").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(postings.count()).isZero();
    }
    @Test void nonItRolesAndUnknownTagsAreNotAssignedAsItJobs() {
        assertThat(keywordClassifier.itRoles(java.util.List.of("92","1006","1029","1036","503864","147191"))).containsExactly("92");
        assertThat(keywordClassifier.classify(java.util.List.of("503864","147191")).unknown()).containsExactly("503864","147191");
        assertThatThrownBy(()->keywordClassifier.itRoles(java.util.List.of("503864","147191")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IT 직무가 없어");
    }
    @Test void multipleItJobsCanBeFilteredAndReplacedWithoutDuplicateRows() {
        String summary=SaraminCrawlerTests.SUMMARY.replace("<div class=\"tags\">",
            "<div class=\"tags\"><a href='/zf_user/jobs/list/job-category?cat_kewd=92,235,92'>추가 직무/기술</a>"
            +"<a href='/zf_user/jobs/list/job-category?cat_kewd=503864'>#RTL</a>");
        var result=collectedStore.save(SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().jobCategories()).extracting(CodeResponse::code).containsExactly("84","92");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata()).contains("RTL","503864","unknownCodes");
        for(String code:java.util.List.of("84","92")) {
            var page=service.list(0,20,null,code,"101070",null);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.content().get(0).jobCategories()).hasSize(2);
        }
        collectedStore.save(SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,SaraminCrawlerTests.BODY,"unused"));
        assertThat(service.list(0,20,null,"92",null,null).totalElements()).isZero();
        assertThat(service.list(0,20,null,"84",null,null).totalElements()).isEqualTo(1);
    }
    @Test void manualImportRejectsTechnologyAsJobCode() throws Exception {
        for(String jobs:java.util.List.of("[\"201\"]")) {
            String body="""
                {"companyId":%d,"title":"직무 검증","sourceUrl":"https://example.com/jobs/check","active":true,"codes":{"jobCategories":%s}}
                """.formatted(companyId,jobs);
            mvc.perform(put("/internal/recruit/job-postings/SARAMIN/bad-job").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(postings.count()).isZero();
    }
    @Test void postingWithoutCompanyInfoLinkStillPersistsWithNullUrl() {
        String summary=SaraminCrawlerTests.SUMMARY.replace("href=\"/zf_user/company-info/view?csn=example\"", "");
        var result=collectedStore.save(SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().company().companyInfoUrl()).isNull();
        assertThat(postings.findById(new PostingId("SARAMIN","123"))).isPresent();
    }

    @Test void employmentArraysRoundTripDeduplicateReplaceAndClear() throws Exception {
        String body="""
            {"companyId":%d,"title":"고용형태 테스트","sourceUrl":"https://example.com/jobs/test","active":true,
             "codes":{"employmentTypes":["9","2","2"]}}
            """.formatted(companyId);
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/employment").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.employmentTypes.length()").value(2))
            .andExpect(jsonPath("$.posting.employmentTypes[0].code").value("2"));
        entityManager.flush();entityManager.clear();
        assertThat(service.get("SARAMIN","employment").posting().employmentTypes()).extracting(CodeResponse::code)
            .containsExactly("2","9");
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/employment").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("[\"9\",\"2\",\"2\"]","[\"1\"]")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.employmentTypes.length()").value(1))
            .andExpect(jsonPath("$.posting.employmentTypes[0].code").value("1"));
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/employment").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"employmentTypes\":[\"9\",\"2\",\"2\"]","")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.employmentTypes").isEmpty());
        assertThat(((Number)entityManager.createNativeQuery("select count(*) from posting_employment_type").getSingleResult()).intValue()).isZero();
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/unknown-employment").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("[\"9\",\"2\",\"2\"]","[\"999999\"]")))
            .andExpect(status().isBadRequest());
    }
    @Test void collectedEntryTypesAndEmploymentDescriptionsArePreserved() {
        for(var entry:java.util.Map.of("신입","1","신입/경력 3년 이상","3","경력무관","0","경력무관(신입포함)","0","신입·경력(년수무관)","3").entrySet()) {
            String summary=SaraminCrawlerTests.SUMMARY.replace("<dd>신입</dd>","<dd>"+entry.getKey()+"</dd>")
                .replace("<dd>정규직</dd>","<dd>정규직 수습기간 3개월, 프리랜서</dd>");
            var result=collectedStore.save(SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused"));
            assertThat(result.posting().experienceType().code()).isEqualTo(entry.getValue());
            assertThat(result.posting().employmentTypes()).extracting(CodeResponse::code).containsExactly("1","9");
            assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata())
                .contains(entry.getKey(),"정규직 수습기간 3개월","employmentTypeMapping");
        }
    }
    @Test void experienceOnlyUnknownAndMissingAreRejectedBeforeCompanyCreation() {
        long before=entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult();
        for(String value:java.util.List.of("경력 2년 ↑","경력 0~3년","협의","")) {
            String summary=SaraminCrawlerTests.SUMMARY.replace("<dd>신입</dd>","<dd>"+value+"</dd>");
            var data=SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused");
            assertThatThrownBy(()->collectedStore.save(data)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(postings.count()).isZero();
        assertThat(entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult()).isEqualTo(before);
    }
    @Test void educationAndSalaryRoundTripOnImportAndClear() throws Exception {
        String body="""
            {"companyId":%d,"title":"학력 급여","sourceUrl":"https://example.com/jobs/salary","active":true,
             "educationNote":"예정자 가능","salaryMinAmount":35000000,"salaryMaxAmount":35000000,
             "codes":{"educationLevel":"8","salaryUnit":"ANNUAL","salaryDecision":"DISCLOSED"}}
            """.formatted(companyId);
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/salary").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.posting.salaryMinAmount").value(35000000))
            .andExpect(jsonPath("$.posting.salaryMaxAmount").value(35000000))
            .andExpect(jsonPath("$.posting.educationNote").value("예정자 가능"))
            .andExpect(jsonPath("$.posting.salaryUnit.code").value("ANNUAL"))
            .andExpect(jsonPath("$.posting.salaryDecision.code").value("DISCLOSED"))
            .andExpect(jsonPath("$.posting.salaryType").doesNotExist());
        entityManager.flush();entityManager.clear();
        var conditions=service.get("SARAMIN","salary").posting();
        assertThat(conditions.salaryMinAmount()).isEqualTo(35000000L);
        assertThat(conditions.educationNote()).isEqualTo("예정자 가능");
        assertThat(conditions.salaryUnit().code()).isEqualTo("ANNUAL");
        String clear="""
            {"companyId":%d,"title":"조건 비움","sourceUrl":"https://example.com/jobs/salary","active":true,"codes":{}}
            """.formatted(companyId);
        mvc.perform(put("/internal/recruit/job-postings/SARAMIN/salary").contentType(MediaType.APPLICATION_JSON).content(clear))
            .andExpect(status().isOk());
        var saved=service.get("SARAMIN","salary").posting();
        assertThat(saved.educationNote()).isNull();assertThat(saved.salaryMinAmount()).isNull();
        assertThat(saved.salaryMaxAmount()).isNull();assertThat(saved.salaryUnit()).isNull();assertThat(saved.salaryDecision()).isNull();
        mvc.perform(get("/api/v1/recruit-codes/salary-units")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/salary-decisions")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/salary-types")).andExpect(status().isNotFound());
    }
    @Test void salaryImportRejectsInvalidRangesMissingUnitsAndConflictingDecisions() throws Exception {
        for(String fields:java.util.List.of(
                "\"salaryMinAmount\":-1,\"codes\":{\"salaryUnit\":\"ANNUAL\",\"salaryDecision\":\"DISCLOSED\"}",
                "\"salaryMinAmount\":4000,\"salaryMaxAmount\":3000,\"codes\":{\"salaryUnit\":\"ANNUAL\",\"salaryDecision\":\"DISCLOSED\"}",
                "\"salaryMinAmount\":3000,\"codes\":{\"salaryDecision\":\"DISCLOSED\"}",
                "\"salaryMinAmount\":3000,\"codes\":{\"salaryUnit\":\"ANNUAL\",\"salaryDecision\":\"INTERVIEW\"}",
                "\"codes\":{\"salaryUnit\":\"ANNUAL\",\"salaryDecision\":\"DISCLOSED\"}",
                "\"codes\":{\"salaryUnit\":\"UNKNOWN\"}",
                "\"codes\":{\"salaryDecision\":\"UNKNOWN\"}")) {
            String body="""
                {"companyId":%d,"title":"오류","sourceUrl":"https://example.com/jobs/bad","active":true,%s}
                """.formatted(companyId,fields);
            mvc.perform(put("/internal/recruit/job-postings/SARAMIN/bad-salary").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(postings.count()).isZero();
    }
    @Test void collectedConditionsKeepExactOriginalTextAndUpdateAmounts() {
        String summary=SaraminCrawlerTests.SUMMARY.replace("대졸(4년제) 이상","대졸(4년제) 이상(예정자 가능)")
            .replace("면접 후 결정","월급 220 만원 (주 40시간)");
        var result=collectedStore.save(SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().educationLevel().code()).isEqualTo("8");
        assertThat(result.posting().educationNote()).isEqualTo("예정자 가능");
        assertThat(result.posting().salaryUnit().code()).isEqualTo("MONTHLY");
        assertThat(result.posting().salaryMinAmount()).isEqualTo(2_200_000L);
        assertThat(result.posting().salaryMaxAmount()).isEqualTo(2_200_000L);
        var stored=postings.findById(new PostingId("SARAMIN","123")).orElseThrow();
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,stored.getId()).getSourceMetadata()).contains("대졸(4년제) 이상(예정자 가능)","월급 220 만원 (주 40시간)");
        result=collectedStore.save(SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().salaryMinAmount()).isNull();assertThat(result.posting().educationNote()).isNull();
        assertThat(result.posting().salaryDecision().code()).isEqualTo("INTERVIEW");
    }
    @Test void collectedAmbiguousEducationAndUnknownSalaryKeepRawText() {
        String education="대졸(4년제) 이상 or 실무 3년 이상";
        String summary=SaraminCrawlerTests.SUMMARY.replace("대졸(4년제) 이상",education).replace("면접 후 결정","급여 협의");
        var result=collectedStore.save(SaraminPageParser.parse("123",summary,SaraminCrawlerTests.BODY,"unused"));
        assertThat(result.posting().educationLevel()).isNull();assertThat(result.posting().educationNote()).isEqualTo(education);
        assertThat(result.posting().salaryMinAmount()).isNull();assertThat(result.posting().salaryDecision()).isNull();
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata()).contains(education,"급여 협의");
    }

    @Test void ocrDescriptionAndEvidencePersistWithoutChangingStructuredConditions() throws Exception {
        String url="https://www.saraminimage.co.kr/poster.png";
        String body="<div class='user_content'><img src='"+url+"'></div>";
        var result=PostingBodyOcrTests.supplement(
            "상시 채용 연봉 9,999만원. OCR 본문 테스트 설명으로 구조화된 조건은 원본 요약만 사용합니다. ".repeat(3),body,url);
        collectedStore.save(SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,body,url,result));
        entityManager.flush();
        entityManager.clear();
        var stored=postings.findById(new PostingId("SARAMIN","123")).orElseThrow();
        assertThat(entityManager.find(JobPostingDetail.class,stored.getId()).getDescription()).contains("OCR 본문");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,stored.getId()).getSourceMetadata()).contains("bodyOcr","SUCCESS","test-model","poster.png","confidence","box");
        var detail=service.get("SARAMIN","123");
        assertThat(detail.posting().salaryDecision().code()).isEqualTo("INTERVIEW");
        assertThat(detail.posting().salaryMinAmount()).isNull();
        assertThat(detail.deadlineType().code()).isEqualTo("1");
    }

    @Test void mixedRoleTagsRemainEligibleAndOnlyItRolesAreLinked() {
        for(String code:java.util.List.of("2203","2201","2210")) {
            var codes=java.util.Map.of("cat_mcls",java.util.List.of("2"),
                "cat_kewd",java.util.List.of("84","92","235",code));
            assertThat(keywordClassifier.eligibleItRoles(codes)).containsExactly("84","92");
            assertThat(keywordClassifier.classify(codes.get("cat_kewd")).roles()).contains(code);
        }
        assertThat(keywordClassifier.eligibleItRoles(java.util.Map.of("cat_mcls",java.util.List.of("2"),
            "cat_kewd",java.util.List.of("84","92","84","235","122","503864")))).containsExactly("84","92");
    }
    @Test void mixedRolesStillRequireItMajorAndKnownItEvidence() {
        for(var codes:java.util.List.of(
            java.util.Map.of("cat_mcls",java.util.List.of("2"),"cat_kewd",java.util.List.of("2203","2201","2210")),
            java.util.Map.of("cat_mcls",java.util.List.of("8"),"cat_kewd",java.util.List.of("84","2203")))) {
            assertThatThrownBy(()->keywordClassifier.eligibleItRoles(codes)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(keywordClassifier.eligibleItRoles(java.util.Map.of("cat_mcls",java.util.List.of("2"),
            "cat_kewd",java.util.List.of("235","2203")))).isEmpty();
    }
    @Test void mixedPostingPreservesWholeBodyAndOriginalNonItRoleCodes() {
        String mixed=SaraminCrawlerTests.SUMMARY.replace("cat_kewd=84","cat_kewd=84,92,2203,2201,2210");
        String body=SaraminCrawlerTests.BODY.replace("</div>","<p>영업 부문 자격요건 및 마케팅 부문 자격요건</p></div>");
        var saved=collectedStore.save(SaraminPageParser.parse("123",mixed,body,"unused"));
        entityManager.flush();entityManager.clear();
        assertThat(saved.posting().jobCategories()).extracting(CodeResponse::code).containsExactly("84","92");
        assertThat(service.get("SARAMIN","123").description()).contains("영업 부문 자격요건","마케팅 부문 자격요건");
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata())
            .contains("2203","2201","2210","originalCodes","roleCodes");
    }
    @Test void mixedRecollectionUpdatesSamePostingWithoutDuplicateCompany() {
        var original=collectedStore.save(SaraminPageParser.parse("123",SaraminCrawlerTests.SUMMARY,SaraminCrawlerTests.BODY,"unused"));
        long companies=entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult();
        String mixed=SaraminCrawlerTests.SUMMARY.replace("cat_kewd=84","cat_kewd=84,2203").replace("백엔드 개발","개발 및 영업 모집");
        var updated=collectedStore.save(SaraminPageParser.parse("123",mixed,SaraminCrawlerTests.BODY,"unused"));
        entityManager.flush();entityManager.clear();
        assertThat(updated.posting().company().id()).isEqualTo(original.posting().company().id());
        assertThat(service.get("SARAMIN","123").posting().title()).isEqualTo("개발 및 영업 모집");
        assertThat(postings.count()).isEqualTo(1);
        assertThat(entityManager.find(JobPostingCollectionMetadata.class,new PostingId("SARAMIN","123")).getSourceMetadata()).contains("2203");
        assertThat(entityManager.createQuery("select count(c) from Company c",Long.class).getSingleResult()).isEqualTo(companies);
    }
    @Test void batchAcceptsMixedFixtureAndNextItPosting() throws Exception {
        var fixtures=new SaraminPaginationTests.Pages() {
            @Override public SaraminPageParser.Collected collect(String id) {
                String summary=SaraminCrawlerTests.SUMMARY.replace("123",id);
                if(id.equals("9123")) summary=summary.replace("cat_kewd=84","cat_kewd=84,2203");
                return SaraminPageParser.parse(id,summary,SaraminCrawlerTests.BODY,"fixture");
            }
        };
        fixtures.data.put(1,new SaraminCrawler.ListingPage(1,java.util.List.of("9123","9456"),false));
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.main.web-application-type","none");
        new SaraminCrawlRunner(fixtures,collectedStore,env,new org.springframework.context.support.GenericApplicationContext())
            .run(new org.springframework.boot.DefaultApplicationArguments());
        assertThat(postings.findById(new PostingId("SARAMIN","9123"))).isPresent();
        assertThat(postings.findById(new PostingId("SARAMIN","9456"))).isPresent();
        assertThat(postings.count()).isEqualTo(2);
    }
}





