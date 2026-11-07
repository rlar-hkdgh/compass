package com.example.compass.student.service;
import com.example.compass.student.dto.OnboardingDtos;
import com.example.compass.student.dto.StudentDtos;


import com.example.compass.TestDatabase;
import com.example.compass.student.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static com.example.compass.student.dto.OnboardingDtos.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:onboarding-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "compass.recruit.import-enabled=false","compass.recruit.crawl.enabled=false","compass.activity.collect.enabled=false"
})
@ContextConfiguration(initializers=TestDatabase.class)
class OnboardingIntegrationTests {
    private static final String PATH="/api/v1/me/onboarding";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired OnboardingService service;
    @Autowired StudentInputService inputs;
    @Autowired EntityManager em;
    MockMvc mvc;
    final JsonMapper json=JsonMapper.builder().build();
    record Account(long id,String login,String password) {}
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    MockHttpSession anonymous() throws Exception {return (MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    String token(MockHttpSession session) throws Exception {return json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();}
    ResultActions send(String method,String path,MockHttpSession session,Object value) throws Exception {
        var request=request(org.springframework.http.HttpMethod.valueOf(method),path).session(session).header("X-CSRF-TOKEN",token(session)).contentType(MediaType.APPLICATION_JSON);
        if(value!=null)request.content(json.writeValueAsString(value));return mvc.perform(request);
    }
    Account account() throws Exception {
        String login="u"+UUID.randomUUID().toString().replace("-","").substring(0,15), password=UUID.randomUUID().toString();
        var result=send("POST","/api/v1/auth/signup",(MockHttpSession)VerifiedSignupFixture.prepare(db,anonymous(),login+"@sju.ac.kr"),Map.of("password",password,"schoolEmail",login+"@sju.ac.kr","name","온보딩 테스트","isTransfer",false)).andExpect(status().isCreated()).andReturn();
        return new Account(json.readTree(result.getResponse().getContentAsString()).get("id").asLong(),login,password);
    }
    MockHttpSession login(Account a) throws Exception {
        var session=anonymous();send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",a.login()+"@sju.ac.kr","password",a.password())).andExpect(status().isOk());return session;
    }
    Map<String,String> change(String step,String status){return Map.of("step",step,"status",status);}
    ResultActions patchSteps(MockHttpSession session,Object... changes) throws Exception {return send("PATCH",PATH,session,Map.of("steps",List.of(changes)));}
    int rows(long student){return db.queryForObject("select count(*) from student_onboarding_step where student_id=?",Integer.class,student);}
    String saved(long student,String step){return db.queryForObject("select status from student_onboarding_step where student_id=? and step_code=?",String.class,student,step);}
    JsonNode current(MockHttpSession session) throws Exception {return json.readTree(mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

    @Test void initialGetReturnsAllOrderedDefaultsWithoutWritingOrInferringFromExistingInputs() throws Exception {
        var a=account();var session=login(a);
        inputs.saveCourse(a.id(),null,new StudentDtos.CourseInput("001234","이미 입력한 과목",2025,"1학기","P/NP","P",null));
        var result=current(session);
        assertThat(result.get("steps").size()).isEqualTo(8);
        int i=0;for(var step:OnboardingStep.values()) {assertThat(result.get("steps").get(i).get("step").asText()).isEqualTo(step.name());assertThat(result.get("steps").get(i++).get("status").asText()).isEqualTo("NOT_STARTED");}
        assertThat(result.get("nextStep").asText()).isEqualTo("BASIC_INFO");
        assertThat(result.get("finished").asBoolean()).isFalse();assertThat(result.get("completedSteps").asInt()).isZero();assertThat(result.get("skippedSteps").asInt()).isZero();
        assertThat(rows(a.id())).isZero();
    }

    @Test void partialPatchMatchesAgreedExampleAndJpaMappingAndIsIdempotent() throws Exception {
        var a=account();var session=login(a);
        var changes=new Object[]{change("BASIC_INFO","COMPLETED"),change("MAJORS","COMPLETED"),change("PREFERENCES","SKIPPED")};
        patchSteps(session,changes).andExpect(status().isOk()).andExpect(jsonPath("$.nextStep").value("COURSES")).andExpect(jsonPath("$.completedSteps").value(2)).andExpect(jsonPath("$.skippedSteps").value(1)).andExpect(jsonPath("$.totalSteps").value(8)).andExpect(jsonPath("$.finished").value(false));
        patchSteps(session,changes).andExpect(status().isOk());assertThat(rows(a.id())).isEqualTo(3);
        var stored=em.find(StudentOnboardingProgress.class,new StudentOnboardingProgress.Key(a.id(),OnboardingStep.MAJORS));assertThat(stored.getStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        patchSteps(session,change("PROJECTS","COMPLETED")).andExpect(status().isOk()).andExpect(jsonPath("$.nextStep").value("COURSES"));
        assertThat(saved(a.id(),"PREFERENCES")).isEqualTo("SKIPPED");
    }

    @Test void logoutAndNewLoginResumePersistedStateForSameStudentOnly() throws Exception {
        var a=account();var session=login(a);
        patchSteps(session,change("BASIC_INFO","COMPLETED"),change("MAJORS","SKIPPED")).andExpect(status().isOk());
        send("POST","/api/v1/auth/logout",session,null).andExpect(status().isNoContent());assertThat(session.isInvalid()).isTrue();
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        var fresh=login(a);mvc.perform(get(PATH).session(fresh)).andExpect(status().isOk()).andExpect(jsonPath("$.nextStep").value("PREFERENCES")).andExpect(jsonPath("$.completedSteps").value(1)).andExpect(jsonPath("$.skippedSteps").value(1));
        var other=account();mvc.perform(get(PATH).session(login(other))).andExpect(jsonPath("$.nextStep").value("BASIC_INFO")).andExpect(jsonPath("$.completedSteps").value(0));assertThat(rows(other.id())).isZero();
    }

    @Test void resettingAndChangingDecisionsNeverDeletesOrChangesInputData() throws Exception {
        var a=account();var session=login(a);
        var course=inputs.saveCourse(a.id(),null,new StudentDtos.CourseInput("000007","보존할 과목",2025,"2학기","GRADE","A+",new java.math.BigDecimal("4.5")));
        patchSteps(session,change("COURSES","COMPLETED")).andExpect(status().isOk());
        patchSteps(session,change("COURSES","SKIPPED")).andExpect(status().isOk());assertThat(inputs.course(a.id(),course.id()).courseName()).isEqualTo("보존할 과목");
        patchSteps(session,change("COURSES","NOT_STARTED")).andExpect(status().isOk()).andExpect(jsonPath("$.steps[3].status").value("NOT_STARTED"));assertThat(rows(a.id())).isZero();assertThat(inputs.course(a.id(),course.id()).grade()).isEqualTo("A+");
    }

    @Test void everyResolvedStepFinishesAndResetReturnsEarliestUnresolvedStep() throws Exception {
        var a=account();var session=login(a);List<Object> changes=new ArrayList<>();
        for(var step:OnboardingStep.values())changes.add(change(step.name(),"SKIPPED"));
        patchSteps(session,changes.toArray()).andExpect(status().isOk()).andExpect(jsonPath("$.finished").value(true)).andExpect(jsonPath("$.nextStep").doesNotExist()).andExpect(jsonPath("$.skippedSteps").value(8)).andExpect(jsonPath("$.completedSteps").value(0));
        patchSteps(session,change("PREFERENCES","NOT_STARTED"),change("CERTIFICATIONS","NOT_STARTED")).andExpect(status().isOk()).andExpect(jsonPath("$.finished").value(false)).andExpect(jsonPath("$.nextStep").value("PREFERENCES"));
        patchSteps(session,change("PREFERENCES","COMPLETED")).andExpect(jsonPath("$.nextStep").value("CERTIFICATIONS")).andExpect(jsonPath("$.completedSteps").value(1));
    }

    @Test void userIdentifiersInBodyOrQueryCannotAccessOtherStudentsProgress() throws Exception {
        var first=account();var second=account();var session=login(first);var other=login(second);
        patchSteps(other,change("BASIC_INFO","SKIPPED")).andExpect(status().isOk());
        send("PATCH",PATH,session,Map.of("studentId",second.id(),"steps",List.of(change("MAJORS","COMPLETED")))).andExpect(status().isOk());
        mvc.perform(get(PATH).param("studentId",Long.toString(second.id())).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.steps[0].status").value("NOT_STARTED")).andExpect(jsonPath("$.steps[1].status").value("COMPLETED"));
        assertThat(saved(second.id(),"BASIC_INFO")).isEqualTo("SKIPPED");assertThat(rows(second.id())).isEqualTo(1);
        mvc.perform(get("/api/v1/me/onboarding/"+second.id()).session(session)).andExpect(status().isNotFound());
    }

    @Test void authenticationAndCsrfAreRequiredAndOldCsrfIsRejectedAfterLogin() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        var anon=anonymous();send("PATCH",PATH,anon,Map.of("steps",List.of(change("BASIC_INFO","COMPLETED")))).andExpect(status().isUnauthorized());
        var a=account();var session=anonymous();String old=token(session);
        send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",a.login()+"@sju.ac.kr","password",a.password())).andExpect(status().isOk());
        String body=json.writeValueAsString(Map.of("steps",List.of(change("BASIC_INFO","COMPLETED"))));
        mvc.perform(patch(PATH).session(session).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(patch(PATH).session(session).header("X-CSRF-TOKEN",old).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());assertThat(rows(a.id())).isZero();
    }

    @Test void invalidEnumNullEmptyAndDuplicateStepsAreRejectedWithoutPartialWrites() throws Exception {
        var a=account();var session=login(a);
        List<Object> invalid=new ArrayList<>();
        invalid.add(Map.of());invalid.add(Map.of("steps",List.of()));invalid.add(Map.of("steps",Arrays.asList((Object)null)));
        invalid.add(Map.of("steps",List.of(Map.of("step","BASIC_INFO"))));
        invalid.add(Map.of("steps",List.of(Map.of("status","COMPLETED"))));
        invalid.add(Map.of("steps",List.of(change("UNKNOWN","COMPLETED"))));
        invalid.add(Map.of("steps",List.of(change("BASIC_INFO","UNKNOWN"))));
        invalid.add(Map.of("steps",List.of(change("BASIC_INFO","completed"))));
        invalid.add(Map.of("steps",List.of(change("BASIC_INFO","COMPLETED"),change("MAJORS","UNKNOWN"))));
        invalid.add(Map.of("steps",List.of(change("BASIC_INFO","COMPLETED"),change("BASIC_INFO","SKIPPED"))));
        for(var body:invalid)send("PATCH",PATH,session,body).andExpect(status().isBadRequest());
        assertThat(rows(a.id())).isZero();
    }

    @Test void laterDatabaseFailureRollsBackEarlierUpdatesAndResetsInSamePatch() throws Exception {
        var a=account();var session=login(a);
        patchSteps(session,change("BASIC_INFO","COMPLETED"),change("MAJORS","COMPLETED")).andExpect(status().isOk());
        db.execute("alter table student_onboarding_step add constraint onboarding_atomicity_guard check(not(student_id="+a.id()+" and step_code='PREFERENCES' and status='SKIPPED'))");
        try {
            patchSteps(session,change("BASIC_INFO","NOT_STARTED"),change("MAJORS","SKIPPED"),change("PREFERENCES","SKIPPED")).andExpect(status().isConflict());
            assertThat(rows(a.id())).isEqualTo(2);assertThat(saved(a.id(),"BASIC_INFO")).isEqualTo("COMPLETED");assertThat(saved(a.id(),"MAJORS")).isEqualTo("COMPLETED");
        } finally {db.execute("alter table student_onboarding_step drop constraint onboarding_atomicity_guard");}
    }

    @Test void concurrentPatchesSerializeWithoutLostDecisionsOrUniqueKeyFailures() throws Exception {
        var a=account();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{start.await();return service.patch(a.id(),new Patch(List.of(new StepChange(OnboardingStep.BASIC_INFO,OnboardingStatus.COMPLETED),new StepChange(OnboardingStep.MAJORS,OnboardingStatus.COMPLETED))));});
            var second=pool.submit(()->{start.await();return service.patch(a.id(),new Patch(List.of(new StepChange(OnboardingStep.BASIC_INFO,OnboardingStatus.SKIPPED),new StepChange(OnboardingStep.PREFERENCES,OnboardingStatus.SKIPPED))));});
            start.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isNotNull();assertThat(second.get(10,TimeUnit.SECONDS)).isNotNull();
            assertThat(rows(a.id())).isEqualTo(3);assertThat(saved(a.id(),"MAJORS")).isEqualTo("COMPLETED");assertThat(saved(a.id(),"PREFERENCES")).isEqualTo("SKIPPED");
        } finally {pool.shutdownNow();}
    }
}
