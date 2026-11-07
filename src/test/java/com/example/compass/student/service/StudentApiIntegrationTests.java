package com.example.compass.student.service;

import com.example.compass.TestDatabase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.time.LocalDate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:student-api-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","compass.security.allowed-origins=https://frontend.example.test","compass.recruit.import-enabled=true"
}) @ContextConfiguration(initializers=TestDatabase.class) class StudentApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;
    final JsonMapper json=JsonMapper.builder().build();
    record Account(String login,String password,String email,long id,MockHttpSession session){
    }
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    String body(Object value){
        return json.writeValueAsString(value);
    }
    MockHttpSession anonymous() throws Exception{
        return (MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    String token(MockHttpSession session) throws Exception {
        var result=mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    ResultActions send(String method,String path,MockHttpSession session,Object value) throws Exception {
        var builder=request(org.springframework.http.HttpMethod.valueOf(method),path).session(session).header("X-CSRF-TOKEN",token(session)).contentType(MediaType.APPLICATION_JSON);
        if(value!=null)builder.content(body(value));
        return mvc.perform(builder);
    }
    Account account() throws Exception {
        String login="u"+UUID.randomUUID().toString().replace("-","").substring(0,15);
        String password=UUID.randomUUID().toString();
        String email=login+"@sju.ac.kr";
        var session=anonymous();
        VerifiedSignupFixture.prepare(db,session,email);
        var result=send("POST","/api/v1/auth/signup",session,Map.of("password",password,"schoolEmail",email,"name","학생","isTransfer",false)).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.emailVerified").value(true)).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        return new Account(login,password,email,id,session);
    }
    MockHttpSession login(Account a) throws Exception {
        var session=anonymous();
        send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",a.email(),"password",a.password())).andExpect(status().isOk());
        return session;
    }
    Map<String,Object> project(){
        return new LinkedHashMap<>(Map.of("name","완료 프로젝트","overview","목적과 기능","description","본인의 기여","techStackCodes",List.of("201")));
    }
    Map<String,Object> internship(){
        return new LinkedHashMap<>(Map.of("companyName","회사","jobCategoryCode","84","startedOn","2025-01-01","endedOn","2025-02-01","description","구현 및 기여"));
    }
    Map<String,Object> course(){
        return new LinkedHashMap<>(Map.of("courseNumber","001234","courseName","수업","academicYear",2025,"semester","1","evaluationMethod","GRADE","grade","A+","gradePoint",4.5));
    }
    long created(String path,MockHttpSession session,Object input) throws Exception {
        var result=send("POST",path,session,input).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void signupValidatesAndHashesAndNeverReturnsHash() throws Exception {
        var a=account();
        String hash=db.queryForObject("select password_hash from student where id=?",String.class,a.id());
        assertThat(encoder.matches(a.password(),hash)).isTrue();
        assertThat(hash.equals(a.password())).isFalse();
        send("POST","/api/v1/auth/signup",a.session(),Map.of("password",a.password(),"schoolEmail",a.email(),"name","학생","isTransfer",false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
        for(String email:List.of("bad@example.com","UPPER@sju.ac.kr","bad@@sju.ac.kr"))send("POST","/api/v1/auth/signup",a.session(),Map.of("password",a.password(),"schoolEmail",email,"name","학생","isTransfer",false)).andExpect(status().isBadRequest());
    }
    @Test void loginRotatesSessionAndCsrfAndPersistsIdentity() throws Exception {
        var a=account();
        var s=anonymous();
        String before=s.getId();
        String oldToken=token(s);
        mvc.perform(post("/api/v1/auth/login").session(s).with(r->{
            r.setRequestedSessionId(s.getId());r.setRequestedSessionIdValid(true);return r;
        }).header("X-CSRF-TOKEN",oldToken).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("schoolEmail",a.email(),"password",a.password())))).andExpect(status().isOk());
        assertThat(s.getId().equals(before)).isFalse();
        mvc.perform(get("/api/v1/me").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a.id())).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(patch("/api/v1/me").session(s).header("X-CSRF-TOKEN",oldToken).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"변경\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        send("PATCH","/api/v1/me",s,Map.of("name","변경","isTransfer",true,"studentId",a.id()+1)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("변경"));
    }
    @Test void badCredentialsAndMalformedRequestsUseCommonErrors() throws Exception {
        var a=account();
        for(String email:List.of(a.email(),"no_such_user@sju.ac.kr"))send("POST","/api/v1/auth/login",a.session(),Map.of("schoolEmail",email,"password",UUID.randomUUID().toString())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        send("POST","/api/v1/auth/login",a.session(),Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void csrfRequiredForSignupLoginMutationsAndLogout() throws Exception {
        var a=account();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").session(a.session()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        var s=login(a);
        mvc.perform(patch("/api/v1/me").session(s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").session(s)).andExpect(status().isOk());
    }
    @Test void logoutInvalidatesSession() throws Exception {
        var s=login(account());
        send("POST","/api/v1/auth/logout",s,null).andExpect(status().isNoContent());
        assertThat(s.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
    @Test void passwordChangeExpiresAllSessionsAndRejectsOldPassword() throws Exception {
        var a=account();
        var first=login(a);
        var second=login(a);
        send("PATCH","/api/v1/me/password",first,Map.of("currentPassword",UUID.randomUUID().toString(),"newPassword",UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        String next=UUID.randomUUID().toString();
        send("PATCH","/api/v1/me/password",first,Map.of("currentPassword",a.password(),"newPassword",next)).andExpect(status().isNoContent());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me").session(second)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        var anon=anonymous();
        send("POST","/api/v1/auth/login",anon,Map.of("schoolEmail",a.email(),"password",a.password())).andExpect(status().isUnauthorized());
        send("POST","/api/v1/auth/login",anon,Map.of("schoolEmail",a.email(),"password",next)).andExpect(status().isOk());
    }
    @Test void unauthenticatedAndInternalAccessDeniedAndPublicCatalogsPreserved() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        var s=login(account());
        send("POST","/internal/recruit/companies",s,Map.of("name","denied")).andExpect(status().isForbidden());
        for(String type:List.of("majors","major-completion-types","certifications","tech-stacks","job-categories","regions","employment-types","salary-units"))mvc.perform(get("/api/v1/student-codes/"+type)).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").exists());
        mvc.perform(get("/api/v1/recruit-codes/salary-units")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/job-categories")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruit-codes/regions")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/student-codes/unknown")).andExpect(status().isNotFound());
    }
    @Test void corsAllowsOnlyConfiguredCredentialedOrigin() throws Exception {
        mvc.perform(options("/api/v1/me").header("Origin","https://frontend.example.test").header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","content-type,x-csrf-token")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://frontend.example.test")).andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/api/v1/me").header("Origin","https://untrusted.example.test").header("Access-Control-Request-Method","PATCH")).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void majorsAndCertificationsReplaceClearAndValidateCodes() throws Exception {
        var s=login(account());
        String major=db.queryForObject("select min(code) from major",String.class);
        String completion=db.queryForObject("select min(code) from major_completion_type",String.class);
        var selection=Map.of("majorCode",major,"completionTypeCode",completion);
        send("PUT","/api/v1/me/majors",s,Map.of("majors",List.of(selection))).andExpect(status().isOk()).andExpect(jsonPath("$.majors.length()").value(1));
        send("PUT","/api/v1/me/majors",s,Map.of("majors",List.of(selection,selection))).andExpect(status().isBadRequest());
        send("PUT","/api/v1/me/majors",s,Map.of("majors",List.of(Map.of("majorCode","missing","completionTypeCode",completion)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/majors").session(s)).andExpect(jsonPath("$.majors.length()").value(1));
        send("PUT","/api/v1/me/majors",s,Map.of()).andExpect(status().isBadRequest());
        send("PUT","/api/v1/me/majors",s,Map.of("majors",List.of())).andExpect(jsonPath("$.majors").isEmpty());
        send("PUT","/api/v1/me/certifications",s,Map.of("certificationCodes",List.of("QNET_1320"))).andExpect(status().isOk());
        send("PUT","/api/v1/me/certifications",s,Map.of("certificationCodes",List.of("missing"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/certifications").session(s)).andExpect(jsonPath("$.certificationCodes[0]").value("QNET_1320"));
        send("PUT","/api/v1/me/certifications",s,Map.of("certificationCodes",List.of())).andExpect(jsonPath("$.certificationCodes").isEmpty());
    }
    Map<String,Object> preferences(long amount){
        return new LinkedHashMap<>(Map.of("jobCategoryCodes",List.of("84"),"regionCodes",List.of(db.queryForObject("select min(code) from region",String.class)),"employmentTypeCodes",List.of(db.queryForObject("select min(code) from employment_type",String.class)),"minAmount",amount,"salaryUnitCode",db.queryForObject("select min(code) from salary_unit",String.class)));
    }
    @Test void preferencesReplaceAllTablesAndRollbackOnDatabaseFailure() throws Exception {
        var a=account();
        var s=login(a);
        var initial=preferences(100);
        send("PUT","/api/v1/me/preferences",s,initial).andExpect(status().isOk());
        db.execute("alter table student_salary_preference add constraint api_atomicity_guard check(min_amount<>777)");
        try{
            var invalid=preferences(777);
            invalid.put("jobCategoryCodes",List.of("85"));
            invalid.put("regionCodes",List.of());
            send("PUT","/api/v1/me/preferences",s,invalid).andExpect(status().isConflict());
            mvc.perform(get("/api/v1/me/preferences").session(s)).andExpect(jsonPath("$.minAmount").value(100)).andExpect(jsonPath("$.jobCategoryCodes[0]").value("84")).andExpect(jsonPath("$.regionCodes.length()").value(1));
        }
        finally{
            db.execute("alter table student_salary_preference drop constraint api_atomicity_guard");
        }
        var missing=preferences(100);
        missing.remove("regionCodes");
        send("PUT","/api/v1/me/preferences",s,missing).andExpect(status().isBadRequest());
        var half=preferences(100);
        half.remove("salaryUnitCode");
        send("PUT","/api/v1/me/preferences",s,half).andExpect(status().isBadRequest());
        send("PUT","/api/v1/me/preferences",s,Map.of("jobCategoryCodes",List.of(),"regionCodes",List.of(),"employmentTypeCodes",List.of())).andExpect(status().isOk()).andExpect(jsonPath("$.jobCategoryCodes").isEmpty()).andExpect(jsonPath("$.minAmount").doesNotExist());
    }
    @Test void projectCrudOwnershipTechReplacementAndReadmeInvalidation() throws Exception {
        var a=account();
        var s=login(a);
        var other=login(account());
        var q=project();
        q.put("githubUrl","https://github.com/example/first");
        long id=created("/api/v1/me/projects",s,q);
        String path="/api/v1/me/projects/"+id;
        db.update("insert into student_project_readme(project_id,repository_url,last_attempt_at,last_attempt_status) values(?,?,CURRENT_TIMESTAMP,'NOT_FOUND')",id,"https://github.com/example/first");
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        send("PUT",path,other,q).andExpect(status().isNotFound());
        send("DELETE",path,other,null).andExpect(status().isNotFound());
        q.put("techStackCodes",List.of());
        q.put("githubUrl","https://github.com/example/second");
        q.put("name","수정");
        send("PUT",path,s,q).andExpect(status().isOk()).andExpect(jsonPath("$.techStackCodes").isEmpty()).andExpect(jsonPath("$.name").value("수정"));
        assertThat(db.queryForObject("select count(*) from student_project_readme where project_id=?",Integer.class,id)).isZero();
        mvc.perform(get("/api/v1/me/projects").session(s)).andExpect(jsonPath("$[0].id").value(id));
        send("DELETE",path,s,null).andExpect(status().isNoContent());
        mvc.perform(get(path).session(s)).andExpect(status().isNotFound());
    }
    @Test void projectRejectsBlankUnknownDuplicateAndUnsafeUrls() throws Exception {
        var s=login(account());
        for(String field:List.of("name","overview","description")){
            var q=project();
            q.put(field,"  ");
            send("POST","/api/v1/me/projects",s,q).andExpect(status().isBadRequest());
        }
        for(var codes:List.of(List.of("missing"),List.of("201","201"))){
            var q=project();
            q.put("techStackCodes",codes);
            send("POST","/api/v1/me/projects",s,q).andExpect(status().isBadRequest());
        }
        for(String url:List.of("http://github.com/a/b","https://evil.test/a/b","https://github.com@evil.test/a/b")){
            var q=project();
            q.put("githubUrl",url);
            send("POST","/api/v1/me/projects",s,q).andExpect(status().isBadRequest());
        }
    }
    @Test void internshipCrudOwnershipAndDates() throws Exception {
        var s=login(account());
        var other=login(account());
        long id=created("/api/v1/me/internships",s,internship());
        String path="/api/v1/me/internships/"+id;
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        send("PUT",path,other,internship()).andExpect(status().isNotFound());
        send("DELETE",path,other,null).andExpect(status().isNotFound());
        var q=internship();
        q.put("companyName","수정 회사");
        send("PUT",path,s,q).andExpect(jsonPath("$.companyName").value("수정 회사"));
        mvc.perform(get("/api/v1/me/internships").session(s)).andExpect(jsonPath("$[0].id").value(id));
        for(String field:List.of("startedOn","endedOn")){
            q=internship();
            q.put(field,LocalDate.now().plusDays(1).toString());
            send("POST","/api/v1/me/internships",s,q).andExpect(status().isBadRequest());
        }
        q=internship();
        q.put("startedOn","2025-03-01");
        send("POST","/api/v1/me/internships",s,q).andExpect(status().isBadRequest());
        q=internship();
        q.put("jobCategoryCode","missing");
        send("POST","/api/v1/me/internships",s,q).andExpect(status().isBadRequest());
        q=internship();
        q.put("description"," ");
        send("POST","/api/v1/me/internships",s,q).andExpect(status().isBadRequest());
        send("DELETE",path,s,null).andExpect(status().isNoContent());
    }
    @Test void awardCrudOwnershipAndOptionalDescription() throws Exception {
        var s=login(account());
        var other=login(account());
        var q=new LinkedHashMap<String,Object>(Map.of("name","수상","organizer","기관"));
        long id=created("/api/v1/me/awards",s,q);
        String path="/api/v1/me/awards/"+id;
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        send("PUT",path,other,q).andExpect(status().isNotFound());
        send("DELETE",path,other,null).andExpect(status().isNotFound());
        q.put("description","수상 설명");
        send("PUT",path,s,q).andExpect(jsonPath("$.description").value("수상 설명"));
        mvc.perform(get("/api/v1/me/awards").session(s)).andExpect(jsonPath("$[0].id").value(id));
        send("DELETE",path,s,null).andExpect(status().isNoContent());
    }
    @Test void courseCrudOwnershipLeadingZerosAndGradeRules() throws Exception {
        var s=login(account());
        var other=login(account());
        long id=created("/api/v1/me/courses",s,course());
        String path="/api/v1/me/courses/"+id;
        mvc.perform(get(path).session(s)).andExpect(jsonPath("$.courseNumber").value("001234"));
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        send("PUT",path,other,course()).andExpect(status().isNotFound());
        send("DELETE",path,other,null).andExpect(status().isNotFound());
        var q=course();
        q.put("evaluationMethod","P/NP");
        q.put("grade","P");
        q.remove("gradePoint");
        send("PUT",path,s,q).andExpect(status().isOk()).andExpect(jsonPath("$.gradePoint").doesNotExist());
        q.put("gradePoint",4.5);
        send("PUT",path,s,q).andExpect(status().isBadRequest());
        q=course();
        q.put("gradePoint",4.0);
        send("POST","/api/v1/me/courses",s,q).andExpect(status().isBadRequest());
        q=course();
        q.put("gradePoint",4.51);
        send("POST","/api/v1/me/courses",s,q).andExpect(status().isBadRequest());
        q=course();
        q.remove("gradePoint");
        created("/api/v1/me/courses",s,q);
        mvc.perform(get("/api/v1/me/courses").session(s)).andExpect(jsonPath("$.length()").value(2));
        send("DELETE",path,s,null).andExpect(status().isNoContent());
    }
    @Test void nestedNullsDuplicateListsNegativeSalaryAndBlankProfileAreRejected() throws Exception {
        var s=login(account());
        var nullMajor=new HashMap<String,Object>();
        nullMajor.put("majors",Arrays.asList((Object)null));
        send("PUT","/api/v1/me/majors",s,nullMajor).andExpect(status().isBadRequest());
        send("PUT","/api/v1/me/certifications",s,Map.of("certificationCodes",List.of("QNET_1320","QNET_1320"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_INPUT"));
        for(String field:List.of("jobCategoryCodes","regionCodes","employmentTypeCodes")){
            var q=preferences(100);
            var values=(List<?>)q.get(field);
            q.put(field,List.of(values.get(0),values.get(0)));
            send("PUT","/api/v1/me/preferences",s,q).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_INPUT"));
            q=preferences(100);
            q.put(field,List.of("missing"));
            send("PUT","/api/v1/me/preferences",s,q).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_CODE"));
        }
        send("PUT","/api/v1/me/preferences",s,preferences(-1)).andExpect(status().isBadRequest());
        var q=preferences(100);
        q.put("salaryUnitCode","missing");
        send("PUT","/api/v1/me/preferences",s,q).andExpect(status().isBadRequest());
        send("PATCH","/api/v1/me",s,Map.of("name","  ")).andExpect(status().isBadRequest());
        send("PATCH","/api/v1/me",s,Map.of("isTransfer",true)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("학생")).andExpect(jsonPath("$.isTransfer").value(true));
    }
    @Test void completeManualInputFlowCanBeReadBackAndCleared() throws Exception {
        var a=account();
        var s=login(a);
        var other=account();
        send("PATCH","/api/v1/me",s,Map.of("name","연동 사용자","isTransfer",true,"studentId",other.id())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a.id()));
        assertThat(db.queryForObject("select name from student where id=?",String.class,other.id())).isEqualTo("학생");
        var prefs=preferences(0);
        send("PUT","/api/v1/me/preferences",s,prefs).andExpect(status().isOk()).andExpect(jsonPath("$.minAmount").value(0));
        String major=db.queryForObject("select min(code) from major",String.class);
        String type=db.queryForObject("select min(code) from major_completion_type",String.class);
        send("PUT","/api/v1/me/majors",s,Map.of("majors",List.of(Map.of("majorCode",major,"completionTypeCode",type)))).andExpect(status().isOk());
        created("/api/v1/me/projects",s,project());
        created("/api/v1/me/internships",s,internship());
        created("/api/v1/me/awards",s,Map.of("name","성과","organizer","기관"));
        created("/api/v1/me/courses",s,course());
        send("PUT","/api/v1/me/certifications",s,Map.of("certificationCodes",List.of("QNET_1320"))).andExpect(status().isOk());
        for(String path:List.of("projects","internships","awards","courses"))mvc.perform(get("/api/v1/me/"+path).session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/me/preferences").session(s)).andExpect(jsonPath("$.jobCategoryCodes[0]").value("84"));
        mvc.perform(get("/api/v1/me/majors").session(s)).andExpect(jsonPath("$.majors.length()").value(1));
        mvc.perform(get("/api/v1/me").session(s)).andExpect(jsonPath("$.name").value("연동 사용자"));
        send("POST","/api/v1/auth/logout",s,null).andExpect(status().isNoContent());
        assertThat(s.isInvalid()).isTrue();
    }

    @Test void schoolEmailIsTheOnlyAuthenticationIdentifier() throws Exception {
        var a=account();
        String hash=db.queryForObject("select password_hash from student where id=?",String.class,a.id());
        var session=login(a);
        mvc.perform(get("/api/v1/me").session(session)).andExpect(jsonPath("$.schoolEmail").value(a.email()))
            .andExpect(jsonPath("$.loginId").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        send("POST","/api/v1/auth/login",anonymous(),Map.of("loginId",a.login(),"password",a.password())).andExpect(status().isBadRequest());
        send("POST","/api/v1/auth/login",anonymous(),Map.of("schoolEmail",a.login(),"password",a.password())).andExpect(status().isBadRequest());
        for(String email:List.of("someone@example.com",a.email().toUpperCase(Locale.ROOT),"bad@@sju.ac.kr"))
            send("POST","/api/v1/auth/login",anonymous(),Map.of("schoolEmail",email,"password",a.password())).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select password_hash from student where id=?",String.class,a.id())).isEqualTo(hash);
    }
    @Test void emailLongerThanLegacyIdentifierLimitCanSignupAndLogin() throws Exception {
        String email="student"+UUID.randomUUID().toString().replace("-","")+"@sejong.co.kr";
        var session=anonymous();VerifiedSignupFixture.prepare(db,session,email);
        send("POST","/api/v1/auth/signup",session,Map.of("schoolEmail",email,"password","Password-12345!","name","학생","isTransfer",false))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.loginId").doesNotExist());
        send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",email,"password","Password-12345!"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.schoolEmail").value(email));
    }
    @Test void groupedRegionsExposeAllExistingCodesExactlyOnce() throws Exception {
        var flat = mvc.perform(get("/api/v1/student-codes/regions")).andExpect(status().isOk()).andReturn();
        var grouped = mvc.perform(get("/api/v1/student-codes/regions/grouped")).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == '서울')].regions[*].code").value(org.hamcrest.Matchers.hasItems("101000", "101010")))
            .andExpect(jsonPath("$[?(@.name == '경기')].regions[*].code").value(org.hamcrest.Matchers.hasItems("102000", "102020")))
            .andExpect(jsonPath("$[?(@.name == '전국')].regions[*].code").value(org.hamcrest.Matchers.contains("117000")))
            .andExpect(jsonPath("$[?(@.name == '해외')].regions[*].code").value(org.hamcrest.Matchers.hasItem("210000")))
            .andReturn();
        List<String> flatCodes = new ArrayList<>();
        for (var code : json.readTree(flat.getResponse().getContentAsString())) flatCodes.add(code.get("code").asText());
        List<String> groupedCodes = new ArrayList<>();
        for (var group : json.readTree(grouped.getResponse().getContentAsString()))
            for (var code : group.get("regions")) groupedCodes.add(code.get("code").asText());
        assertThat(groupedCodes).containsExactlyInAnyOrderElementsOf(flatCodes);
    }

    @Test void studentInformationSupportsScreenFieldsAndPartialUpdatesWithoutChangingIdentity() throws Exception {
        var a=account();var session=login(a);
        send("PATCH","/api/v1/me",session,Map.of("name","홍길동","semester",6,"gpa",3.85,"gpaScale",4.5))
            .andExpect(status().isOk()).andExpect(jsonPath("$.studentNumber").doesNotHaveJsonPath()).andExpect(jsonPath("$.gpa").value(3.85));
        send("PATCH","/api/v1/me",session,Map.of("name","변경","gpa",4.0)).andExpect(status().isOk())
            .andExpect(jsonPath("$.gpa").value(4.0)).andExpect(jsonPath("$.gpaScale").value(4.5)).andExpect(jsonPath("$.semester").value(6));
        mvc.perform(get("/api/v1/me").session(session)).andExpect(jsonPath("$.name").value("변경"))
            .andExpect(jsonPath("$.schoolEmail").value(a.email())).andExpect(jsonPath("$.studentNumber").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.semester").value(6)).andExpect(jsonPath("$.gradeYear").doesNotHaveJsonPath());
    }
    @Test void studentInformationRejectsInvalidRangesAndGpaCombinationsAtomically() throws Exception {
        var session=login(account());
        for(Map<String,Object> bad:List.<Map<String,Object>>of(Map.of("semester",-1),Map.of("gpa",3.0),
            Map.of("gpaScale",4.5),Map.of("gpa",-1,"gpaScale",4.5),Map.of("gpa",4.6,"gpaScale",4.5),
            Map.of("gpa",0,"gpaScale",0),Map.of("gpa",3.001,"gpaScale",4.5),Map.of("gpa",4,"gpaScale",5)))
            send("PATCH","/api/v1/me",session,bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/me").session(session)).andExpect(jsonPath("$.gpa").doesNotExist()).andExpect(jsonPath("$.studentNumber").doesNotExist());
        send("PATCH","/api/v1/me",session,Map.of("gpa",4.2,"gpaScale",4.5)).andExpect(status().isOk());
        send("PATCH","/api/v1/me",session,Map.of("name","반영 금지","gpaScale",4.0)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me").session(session)).andExpect(jsonPath("$.name").value("학생")).andExpect(jsonPath("$.gpaScale").value(4.5));
    }
    Map<String,Object> careerGoal(){
        return new LinkedHashMap<>(Map.of("jobCategoryCode","84","companySizes",List.of("LARGE","SMALL"),"targetSalary",5000,
            "regionCodes",List.of("101000"),"targetCompany","네이버","targetDate","2027-06"));
    }
    @Test void careerGoalStoresYearMonthAndSharesOnlyTheExistingRegionPreference() throws Exception {
        var a=account();var session=login(a);
        mvc.perform(get("/api/v1/me/career-goal").session(session)).andExpect(status().isNotFound());
        send("PUT","/api/v1/me/preferences",session,preferences(100)).andExpect(status().isOk());
        var goal=careerGoal();send("PUT","/api/v1/me/career-goal",session,goal).andExpect(status().isOk())
            .andExpect(jsonPath("$.jobCategoryCode").value("84"))
            .andExpect(jsonPath("$.targetDate").value("2027-06")).andExpect(jsonPath("$.targetSalary").value(5000));
        assertThat(db.queryForObject("select target_date from student_career_goal where student_id=?",java.sql.Date.class,a.id()).toLocalDate()).isEqualTo(LocalDate.of(2027,6,1));
        mvc.perform(get("/api/v1/me/preferences").session(session)).andExpect(jsonPath("$.regionCodes[0]").value("101000"))
            .andExpect(jsonPath("$.jobCategoryCodes[0]").value("84")).andExpect(jsonPath("$.minAmount").value(100));
        goal.put("targetDate","2028-12");send("PUT","/api/v1/me/career-goal",session,goal).andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from student_career_goal where student_id=?",Integer.class,a.id())).isEqualTo(1);
        var second=login(a);mvc.perform(get("/api/v1/me/career-goal").session(second)).andExpect(jsonPath("$.targetDate").value("2028-12"));
        var other=login(account());mvc.perform(get("/api/v1/me/career-goal").session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/onboarding").session(session)).andExpect(jsonPath("$.completedSteps").value(0));
    }
    @Test void careerGoalUsesOnlyCurrentJobAndCompanySelections() throws Exception {
        var session=login(account());
        var goal=careerGoal();
        send("PUT","/api/v1/me/career-goal",session,goal).andExpect(status().isOk())
            .andExpect(jsonPath("$.companySizes.length()").value(2));
        goal.put("jobCategoryCode","82");
        goal.put("companySizes",List.of("SMALL"));
        send("PUT","/api/v1/me/career-goal",session,goal).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/career-goal").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.jobCategoryCode").value("82"))
            .andExpect(jsonPath("$.companySizes.length()").value(1)).andExpect(jsonPath("$.companySizes[0]").value("SMALL"))
            .andExpect(jsonPath("$.companySize").doesNotHaveJsonPath()).andExpect(jsonPath("$.legacyJobField").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.careerType").doesNotHaveJsonPath()).andExpect(jsonPath("$.targetRole").doesNotHaveJsonPath());
    }

    @Test void careerGoalValidatesYearMonthEnumsCodesAndMandatoryFields() throws Exception {
        var session=login(account());send("PUT","/api/v1/me/career-goal",session,careerGoal()).andExpect(status().isOk());
        for(String date:List.of("2027-00","2027-13","2027-2H","2027-6","2027-06-01","1899-12","2101-01","")){
            var q=careerGoal();q.put("targetDate",date);send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isBadRequest());
        }
        for(String field:careerGoal().keySet()){
            var q=careerGoal();q.remove(field);send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isBadRequest());
        }
        for(var change:List.of(Map.entry("jobCategoryCode",(Object)"OTHER"),Map.entry("companySizes",(Object)List.of("UNKNOWN")),Map.entry("companySizes",(Object)List.of()),Map.entry("companySizes",(Object)List.of("LARGE","LARGE")),
            Map.entry("targetSalary",(Object)(-1)),Map.entry("targetCompany",(Object)" "),Map.entry("regionCodes",(Object)List.of()),
            Map.entry("regionCodes",(Object)List.of("101000","101000")),Map.entry("regionCodes",(Object)List.of("MISSING")))){
            var q=careerGoal();q.put(change.getKey(),change.getValue());send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/me/career-goal").session(session)).andExpect(jsonPath("$.targetDate").value("2027-06"));
        for(String boundary:List.of("1900-01","2100-12")){var q=careerGoal();q.put("targetDate",boundary);send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isOk());}
    }
    @Test void careerGoalAndRegionsRollbackTogetherAndRequireSessionAndCsrf() throws Exception {
        var a=account();var session=login(a);send("PUT","/api/v1/me/career-goal",session,careerGoal()).andExpect(status().isOk());
        db.execute("alter table student_career_goal add constraint goal_atomicity_guard check(target_company<>'blocked')");
        try{
            var q=careerGoal();q.put("targetCompany","blocked");q.put("regionCodes",List.of(db.queryForObject("select max(code) from region",String.class)));
            send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isConflict());
            mvc.perform(get("/api/v1/me/career-goal").session(session)).andExpect(jsonPath("$.targetCompany").value("네이버")).andExpect(jsonPath("$.regionCodes[0]").value("101000"));
        }finally{db.execute("alter table student_career_goal drop constraint goal_atomicity_guard");}
        String blockedRegion=db.queryForObject("select max(code) from region where code<>'101000'",String.class);
        db.execute("alter table student_preferred_region add constraint goal_region_guard check(student_id<>"+a.id()+" or region_code<>'"+blockedRegion+"')");
        try{
            var q=careerGoal();q.put("targetDate","2028-12");q.put("regionCodes",List.of(blockedRegion));
            send("PUT","/api/v1/me/career-goal",session,q).andExpect(status().isConflict());
            mvc.perform(get("/api/v1/me/career-goal").session(session)).andExpect(jsonPath("$.targetDate").value("2027-06"))
                .andExpect(jsonPath("$.regionCodes[0]").value("101000"));
        }finally{db.execute("alter table student_preferred_region drop constraint goal_region_guard");}
        mvc.perform(get("/api/v1/me/career-goal")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/me/career-goal").session(session).contentType(MediaType.APPLICATION_JSON).content(body(careerGoal()))).andExpect(status().isForbidden());
        send("PUT","/api/v1/me/career-goal",anonymous(),careerGoal()).andExpect(status().isUnauthorized());
    }
}
