package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;

import com.example.compass.TestDatabase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "spring.config.location=file:./src/main/resources/application.yml","spring.config.import=",
    "spring.datasource.url=jdbc:h2:mem:accounts-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "compass.docs.enabled=true","compass.mail.enabled=false","compass.recruit.import-enabled=false",
    "compass.recruit.crawl.enabled=false","compass.activity.collect.enabled=false"
})
@ContextConfiguration(initializers=TestDatabase.class)
@Import(AccountIntegrationTests.Fakes.class)
class AccountIntegrationTests {
    static final String EMAIL="/api/v1/me/email-verification";
    @TestConfiguration static class Fakes {
        @Bean @Primary TestMail testMail() {return new TestMail();}
        @Bean @Primary TestClock testClock() {return new TestClock();}
    }
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-05T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
        void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    static class TestMail implements VerificationMailSender {
        final Map<String,String> codes=new ConcurrentHashMap<>();
        boolean fail;
        @Override public void send(String email,String code){
            if(fail)throw new IllegalStateException("synthetic SMTP detail must never be returned");
            codes.put(email,code);
        }
    }
    record Account(long id,String login,String email,String password,MockHttpSession session) {}
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired TestMail mail;
    @Autowired TestClock clock;
    @Autowired EmailVerificationService emails;
    @Autowired StudentInputService students;
    @Autowired AccountWithdrawalService withdrawals;
    MockMvc mvc;
    final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();mail.fail=false;}
    MockHttpSession anonymous() throws Exception {return (MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    String token(MockHttpSession s) throws Exception {return json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(s)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
    ResultActions send(String method,String path,MockHttpSession s,Object body) throws Exception {
        var request=request(org.springframework.http.HttpMethod.valueOf(method),path).session(s).header("X-CSRF-TOKEN",token(s)).contentType(MediaType.APPLICATION_JSON);
        if(body!=null)request.content(json.writeValueAsString(body));return mvc.perform(request);
    }
    Account account() throws Exception {
        String login="u"+UUID.randomUUID().toString().replace("-","").substring(0,15),password="Account-test-1234",email=login+"@sju.ac.kr";
        var session=anonymous();
        VerifiedSignupFixture.prepare(db,session,email);
        var result=send("POST","/api/v1/auth/signup",session,Map.of("password",password,"schoolEmail",email,"name","테스트","isTransfer",false))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(true)).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        // Legacy accounts may still use the post-signup verification endpoints.
        db.update("delete from student_email_verification where student_id=?",id);
        send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",email,"password",password)).andExpect(status().isOk());
        return new Account(id,login,email,password,session);
    }
    MockHttpSession login(Account a) throws Exception {
        var s=anonymous();send("POST","/api/v1/auth/login",s,Map.of("schoolEmail",a.email(),"password",a.password())).andExpect(status().isOk());return s;
    }
    void sendCode(Account a) throws Exception {send("POST",EMAIL,a.session(),null).andExpect(status().isOk());}
    int count(String table,long id){return db.queryForObject("select count(*) from "+table+" where student_id=?",Integer.class,id);}
    @Test void verifiesRegisteredEmailOnlyAndDoesNotExposeCodeOrHash() throws Exception {
        var a=account();var other=account();
        send("POST",EMAIL,a.session(),Map.of("studentId",other.id(),"schoolEmail",other.email())).andExpect(status().isOk())
            .andExpect(jsonPath("$.schoolEmail").value(a.email())).andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.codeHash").doesNotExist());
        assertThat(mail.codes).containsKey(a.email()).doesNotContainKey(other.email());
        String code=mail.codes.get(a.email());
        assertThat(code).matches("[0-9]{6}");
        assertThat(db.queryForObject("select code_hash from student_email_verification where student_id=?",String.class,a.id())).isNotEqualTo(code);
        send("POST",EMAIL+"/confirm",other.session(),Map.of("code",code)).andExpect(status().isBadRequest());
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",code)).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(get("/api/v1/me").session(login(a))).andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(db.queryForObject("select code_hash from student_email_verification where student_id=?",String.class,a.id())).isNull();
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",code)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
        send("POST",EMAIL,a.session(),null).andExpect(status().isConflict());
    }
    @Test void cooldownReplacementAndExpiryAreEnforced() throws Exception {
        var a=account();sendCode(a);String old=mail.codes.get(a.email());
        send("POST",EMAIL,a.session(),null).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("EMAIL_RESEND_COOLDOWN"));
        clock.advance(60);sendCode(a);
        // Force a distinct old code when the random generator happened to repeat.
        if(!old.equals(mail.codes.get(a.email())))send("POST",EMAIL+"/confirm",a.session(),Map.of("code",old)).andExpect(status().isBadRequest());
        clock.advance(600);
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",mail.codes.get(a.email()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
        assertThat(students.profile(a.id()).emailVerified()).isFalse();
    }
    @Test void failedAttemptsPersistAndCorrectCodeCannotBypassLockout() throws Exception {
        var a=account();sendCode(a);String correct=mail.codes.get(a.email()),wrong=correct.equals("000000")?"111111":"000000";
        for(int i=0;i<4;i++)send("POST",EMAIL+"/confirm",a.session(),Map.of("code",wrong)).andExpect(status().isBadRequest());
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",wrong)).andExpect(status().isTooManyRequests());
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",correct)).andExpect(status().isTooManyRequests());
        assertThat(db.queryForObject("select failed_attempts from student_email_verification where student_id=?",Integer.class,a.id())).isEqualTo(5);
        clock.advance(60);sendCode(a);
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",mail.codes.get(a.email()))).andExpect(status().isOk());
    }
    @Test void smtpFailureConsumesQuotaInvalidatesCodeAndHidesInternalDetails() throws Exception {
        var a=account();sendCode(a);String old=mail.codes.get(a.email());clock.advance(60);mail.fail=true;
        var result=send("POST",EMAIL,a.session(),null).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic SMTP",old);
        send("POST",EMAIL+"/confirm",a.session(),Map.of("code",old)).andExpect(status().isBadRequest());
        send("POST",EMAIL,a.session(),null).andExpect(status().isTooManyRequests());
        assertThat(db.queryForObject("select send_count from student_email_verification where student_id=?",Integer.class,a.id())).isEqualTo(2);
    }
    @Test void dailyQuotaPersistsAcrossSessionsAndResetsAfter24Hours() throws Exception {
        var a=account();mail.fail=true;
        for(int i=0;i<10;i++){send("POST",EMAIL,a.session(),null).andExpect(status().isServiceUnavailable());clock.advance(60);}
        send("POST",EMAIL,login(a),null).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("EMAIL_SEND_LIMIT"));
        clock.advance(86400);mail.fail=false;sendCode(a);
    }
    @Test void authenticationCsrfAndCodeValidationAreRequired() throws Exception {
        mvc.perform(get(EMAIL)).andExpect(status().isUnauthorized());
        var anon=anonymous();send("POST",EMAIL,anon,null).andExpect(status().isUnauthorized());
        var a=account();
        mvc.perform(post(EMAIL).session(a.session())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(delete("/api/v1/me").session(a.session()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword",a.password())))).andExpect(status().isForbidden());
        for(String code:List.of("12345","1234567","ABCDEF","１２３４５６",""))
            send("POST",EMAIL+"/confirm",a.session(),Map.of("code",code)).andExpect(status().isBadRequest());
        assertThat(count("student_email_verification",a.id())).isZero();
    }
    @Test void concurrentSendSerializesAndOnlyOneIsDelivered() throws Exception {
        var a=account();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<String> action=()->{start.await();try{emails.send(a.id());return "OK";}catch(ApiErrors.Failure e){return e.code;}};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","EMAIL_RESEND_COOLDOWN");
            assertThat(db.queryForObject("select send_count from student_email_verification where student_id=?",Integer.class,a.id())).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentConfirmationConsumesCodeOnce() throws Exception {
        var a=account();sendCode(a);String code=mail.codes.get(a.email());var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<String> action=()->{start.await();try{emails.confirm(a.id(),code);return "OK";}catch(ApiErrors.Failure e){return e.code;}};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","EMAIL_ALREADY_VERIFIED");
        }finally{pool.shutdownNow();}
    }
    void personalRows(Account a) {
        long id=a.id();
        db.update("insert into student_career_goal(student_id,job_category_code,target_salary,target_company,target_date) values(?,'84',5000,'기업',DATE '2027-06-01')",id);
        db.update("insert into student_career_goal_company_size values(?,'LARGE')",id);
        var project=students.saveProject(id,null,new StudentDtos.ProjectInput("프로젝트","개요","설명",null,"https://github.com/example/example",List.of()));
        db.update("insert into student_project_readme(project_id,repository_url,last_attempt_at,last_attempt_status) values(?,?,CURRENT_TIMESTAMP,'FAILED')",project.id(),"https://github.com/example/example");
        String tech=db.queryForObject("select min(code) from tech_stack",String.class);
        db.update("insert into student_project_tech_stack values(?,?)",project.id(),tech);
        students.saveCourse(id,null,new StudentDtos.CourseInput("001","과목",2025,"1학기","P/NP","P",null));
        String job=db.queryForObject("select min(code) from job_category",String.class);
        students.saveInternship(id,null,new StudentDtos.InternshipInput("회사",job,LocalDate.of(2025,1,1),LocalDate.of(2025,2,1),"설명"));
        students.saveAward(id,null,new StudentDtos.AwardInput("상","주최",null));
        db.update("insert into student_major select ?,min(code),(select min(code) from major_completion_type) from major",id);
        db.update("insert into student_certification select ?,min(code) from certification",id);
        db.update("insert into student_preferred_job_category values(?,?)",id,job);
        db.update("insert into student_preferred_region select ?,min(code) from region",id);
        db.update("insert into student_preferred_employment_type select ?,min(code) from employment_type",id);
        db.update("insert into student_salary_preference(student_id,salary_unit_code,min_amount) select ?,min(code),100 from salary_unit",id);
        db.update("insert into student_onboarding_step values(?,'BASIC_INFO','COMPLETED')",id);
        long company=students.insert("company","name","공용 회사");
        String posting="withdraw-"+id;
        db.update("insert into job_posting(source_code,source_posting_id,company_id,title,active) values('SARAMIN',?,?,'공고',true)",posting,company);
        db.update("insert into posting_analysis(source_code,source_posting_id,input_hash,input_snapshot,status,started_at) values('SARAMIN',?,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)",posting);
        long analysis=db.queryForObject("select id from posting_analysis where source_posting_id=?",Long.class,posting);
        db.update("insert into gap_analysis(student_id,posting_analysis_id,evaluation_policy_id,student_input_hash,student_input_snapshot,status,started_at) values(?,?,1,'hash','{}','SUCCESS',CURRENT_TIMESTAMP)",id,analysis);
        long gap=db.queryForObject("select id from gap_analysis where student_id=?",Long.class,id);
        db.update("insert into gap_analysis_category(gap_analysis_id,category_code,assessment_status,explanation) values(?,'TECHNICAL_COMPETENCY','NOT_MET','개인 분석')",gap);
        db.update("insert into recommended_task(student_id,gap_analysis_id,title,description,academic_year,period_type,display_order,status) values(?,?,'추천','설명',2026,'FIRST_SEMESTER',1,'TODO')",id,gap);
    }
    @Test void withdrawalDeletesEntirePersonalGraphAndEverySessionButPreservesOtherStudentsAndSharedData() throws Exception {
        var a=account();var other=account();personalRows(a);personalRows(other);sendCode(a);
        var second=login(a);second.setAttribute("private-preview","private transcript");a.session().setAttribute("private-preview","private transcript");
        Map<String,Integer> shared=new HashMap<>();
        for(String table:List.of("company","job_posting","posting_analysis","career_activity","course_activity","certification","tech_stack","major"))
            shared.put(table,db.queryForObject("select count(*) from "+table,Integer.class));
        send("DELETE","/api/v1/me",a.session(),Map.of("currentPassword",a.password(),"studentId",other.id()))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID",0));
        assertThat(a.session().isInvalid()).isTrue();assertThat(second.isInvalid()).isTrue();
        assertThat(db.queryForObject("select count(*) from student where id=?",Integer.class,a.id())).isZero();
        for(String table:List.of("student_project","student_course","student_internship","student_award","student_major","student_certification",
            "student_preferred_job_category","student_preferred_region","student_preferred_employment_type","student_salary_preference",
            "student_career_goal","student_onboarding_step","student_email_verification","gap_analysis","recommended_task"))assertThat(count(table,a.id())).as(table).isZero();
        assertThat(students.projects(other.id())).hasSize(1);
        assertThat(count("recommended_task",other.id())).isEqualTo(1);
        shared.forEach((table,count)->assertThat(db.queryForObject("select count(*) from "+table,Integer.class)).as(table).isEqualTo(count));
        send("POST","/api/v1/auth/login",anonymous(),Map.of("schoolEmail",a.email(),"password",a.password())).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->students.saveAward(a.id(),null,new StudentDtos.AwardInput("상","주최",null))).isInstanceOf(ApiErrors.Failure.class);
        assertThatThrownBy(()->emails.send(a.id())).isInstanceOf(ApiErrors.Failure.class);
    }
    @Test void wrongPasswordDoesNotDeleteOrExpireSessions() throws Exception {
        var a=account();personalRows(a);
        send("DELETE","/api/v1/me",a.session(),Map.of("currentPassword","wrong-password")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertThat(a.session().isInvalid()).isFalse();assertThat(students.projects(a.id())).hasSize(1);
        send("DELETE","/api/v1/me",a.session(),Map.of()).andExpect(status().isBadRequest());
    }
    @Test void databaseFailureRollsBackEveryDeletionAndKeepsSessions() throws Exception {
        var a=account();personalRows(a);sendCode(a);
        db.execute("create table withdrawal_guard(student_id bigint references student(id))");
        try {
            db.update("insert into withdrawal_guard values(?)",a.id());
            send("DELETE","/api/v1/me",a.session(),Map.of("currentPassword",a.password())).andExpect(status().isConflict());
            assertThat(a.session().isInvalid()).isFalse();
            assertThat(students.projects(a.id())).hasSize(1);assertThat(count("recommended_task",a.id())).isEqualTo(1);
            assertThat(count("student_email_verification",a.id())).isEqualTo(1);
        }finally{db.execute("drop table withdrawal_guard");}
    }
    @Test void documentationContainsActualContractsSecurityAndMultipart() throws Exception {
        var result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var spec=json.readTree(result.getResponse().getContentAsString());var paths=spec.get("paths");
        assertThat(paths.has("/api/v1/auth/logout")).isTrue();assertThat(paths.get("/api/v1/me").has("delete")).isTrue();
        assertThat(paths.get(EMAIL+"/confirm").get("post").get("responses").has("429")).isTrue();
        var secured=paths.get(EMAIL).get("post").get("security").get(0);
        assertThat(secured.has("sessionCookie")).isTrue();assertThat(secured.has("csrfToken")).isTrue();
        assertThat(paths.get("/api/v1/auth/login").get("post").get("security").get(0).has("sessionCookie")).isFalse();
        assertThat(paths.get("/api/v1/me/courses/import/preview").get("post").get("requestBody").get("content").has("multipart/form-data")).isTrue();
        assertThat(spec.get("components").get("schemas").get("Signup").get("properties").get("password").get("minLength").asInt()).isEqualTo(10);
        assertThat(spec.get("components").get("schemas").get("ConfirmEmail").get("properties").get("code").get("pattern").asText()).isEqualTo("[0-9]{6}");
        assertThat(paths.get("/api/v1/me").get("delete").get("responses").get("204").has("content")).isFalse();
        assertThat(paths.has("/internal/recruit/companies")).isFalse();
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        String initializer=mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(initializer).contains("requestInterceptor","/api/v1/auth/csrf","csrf.headerName","same-origin");
        mvc.perform(post("/internal/recruit/companies")).andExpect(status().isForbidden());
    }

    @Test void everyDocumentedOperationHasKoreanSummaryAndPreferenceExamplesMatchRealApi() throws Exception {
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var paths=spec.get("paths");
        paths.properties().forEach(entry->entry.getValue().properties().forEach(operation->{
            String summary=operation.getValue().path("summary").asText();
            assertThat(summary).as(entry.getKey()+" "+operation.getKey()).containsPattern("[가-힣]");
            assertThat(operation.getValue().path("description").asText()).containsPattern("[가-힣]");
            operation.getValue().get("responses").properties().forEach(response->
                assertThat(response.getValue().path("description").asText()).containsPattern("[가-힣]"));
        }));
        var put=paths.get("/api/v1/me/preferences").get("put");
        for(String type:List.of("job-categories","regions","employment-types","salary-units")){
            assertThat(put.get("description").asText()).contains("/api/v1/student-codes/"+type);
            mvc.perform(get("/api/v1/student-codes/"+type)).andExpect(status().isOk());
        }
        var examples=put.get("requestBody").get("content").get("application/json").get("examples");
        var a=account();
        for(String example:List.of("선호 선택","급여 조건 포함","선호 해제")){
            var payload=examples.get(example).get("value");
            send("PUT","/api/v1/me/preferences",a.session(),json.convertValue(payload,Map.class)).andExpect(status().isOk());
            var saved=json.readTree(mvc.perform(get("/api/v1/me/preferences").session(a.session())).andReturn().getResponse().getContentAsString());
            assertThat(saved).isEqualTo(payload);
        }
        var bad=json.convertValue(examples.get("선호 선택").get("value"),Map.class);
        bad.put("minAmount",1000);
        send("PUT","/api/v1/me/preferences",a.session(),bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(paths.get("/api/v1/me/projects/{id}").get("put").get("description").asText()).contains("목록 전체를 교체하는 API가 아닙니다");
        assertThat(paths.get("/api/v1/auth/login").get("post").get("responses").has("409")).isFalse();
        assertThat(paths.get("/api/v1/auth/signup/email/availability").get("post").get("responses").has("409")).isFalse();
        assertThat(paths.has("/api/v1/job-postings")).isFalse();
        assertThat(paths.has("/api/v1/job-postings/{source}/{sourcePostingId}")).isFalse();
    }


    @Test void allOpenApiReferencesResolveAndErrorSchemaIsPresent() throws Exception {
        var document=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var paths=document.get("paths");
        assertThat(paths.has("/api/v1/student-codes/{type}")).isTrue();
        assertThat(paths.has("/api/v1/recruit-codes/{type}")).isTrue();
        for(String type:List.of("job-categories","regions","employment-types","salary-units"))
            assertThat(paths.has("/api/v1/student-codes/"+type)).isFalse();
        int count=assertReferences(document,document,"");
        assertThat(count).isGreaterThan(0);
        var error=document.at("/components/schemas/ApiError");
        assertThat(error.isMissingNode()).isFalse();
        assertThat(error.get("properties").has("code")).isTrue();
        assertThat(error.get("properties").has("message")).isTrue();
    }
    int assertReferences(tools.jackson.databind.JsonNode document,tools.jackson.databind.JsonNode node,String location){
        int count=0;
        if(node.isObject()){
            if(node.has("$ref")){
                String ref=node.get("$ref").asText();
                assertThat(ref).as(location).startsWith("#/");
                assertThat(document.at(ref.substring(1)).isMissingNode()).as(location+" -> "+ref).isFalse();
                assertThat(document.at(ref.substring(1)).isNull()).as(location+" -> "+ref).isFalse();
                count++;
            }
            for(var entry:node.properties())count+=assertReferences(document,entry.getValue(),location+"/"+entry.getKey());
        }else if(node.isArray()){
            for(int i=0;i<node.size();i++)count+=assertReferences(document,node.get(i),location+"/"+i);
        }
        return count;
    }

    @Test void swaggerFlowUsesRealRotatingCsrfAndCookieSession() throws Exception {
        var s=anonymous();String old=token(s);
        var a=account();
        send("POST","/api/v1/auth/login",s,Map.of("schoolEmail",a.email(),"password",a.password())).andExpect(status().isOk());
        mvc.perform(post(EMAIL).session(s).header("X-CSRF-TOKEN",old)).andExpect(status().isForbidden());
        send("POST",EMAIL,s,null).andExpect(status().isOk());
        send("POST","/api/v1/auth/logout",s,null).andExpect(status().isNoContent());
        assertThat(s.isInvalid()).isTrue();
    }

    @Test void schoolEmailAndStudentScreenExamplesMatchActualApiAndSchemas() throws Exception {
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var schemas=spec.get("components").get("schemas");
        for(String schema:List.of("Signup","Login","Profile")){
            assertThat(schemas.get(schema).get("properties").has("loginId")).isFalse();
            assertThat(schemas.get(schema).get("properties").has("schoolEmail")).isTrue();
        }
        var a=account();var paths=spec.get("paths");
        var patch=paths.get("/api/v1/me").get("patch");
        var profile=json.convertValue(patch.get("requestBody").get("content").get("application/json").get("examples").get("학생 정보").get("value"),Map.class);
        for (String schema : List.of("Profile", "ProfilePatch"))
            assertThat(schemas.get(schema).get("properties").has("studentNumber")).isFalse();
        for (String schema : List.of("CareerGoal", "CareerGoalInput"))
            for (String field : List.of("legacyJobField", "careerType", "targetRole", "companySize"))
                assertThat(schemas.get(schema).get("properties").has(field)).isFalse();
        send("PATCH","/api/v1/me",a.session(),profile).andExpect(status().isOk()).andExpect(jsonPath("$.semester").value(6)).andExpect(jsonPath("$.gradeYear").doesNotHaveJsonPath());
        var goal=paths.get("/api/v1/me/career-goal").get("put");
        assertThat(goal.get("security").get(0).has("sessionCookie")).isTrue();assertThat(goal.get("security").get(0).has("csrfToken")).isTrue();
        assertThat(paths.get("/api/v1/me/career-goal").get("get").get("responses").has("404")).isTrue();
        var example=goal.get("requestBody").get("content").get("application/json").get("examples").get("취업 목표").get("value");
        send("PUT","/api/v1/me/career-goal",a.session(),json.convertValue(example,Map.class)).andExpect(status().isOk()).andExpect(jsonPath("$.targetDate").value("2027-06"));
        assertThat(schemas.get("CareerGoalInput").get("properties").get("targetDate").get("pattern").asText()).contains("0[1-9]","1[0-2]");
        assertThat(paths.get("/api/v1/auth/signup").get("post").get("responses").get("409").get("description").asText()).doesNotContain("LOGIN_ID_EXISTS");
    }
}
