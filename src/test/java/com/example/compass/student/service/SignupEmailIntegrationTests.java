package com.example.compass.student.service;

import com.example.compass.TestDatabase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={
 "spring.config.location=file:./src/main/resources/application.yml","spring.config.import=",
 "spring.datasource.url=jdbc:h2:mem:signup-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
 "spring.flyway.enabled=true","compass.docs.enabled=true","compass.mail.enabled=false",
 "compass.recruit.import-enabled=false","compass.recruit.crawl.enabled=false","compass.activity.collect.enabled=false"
})
@ContextConfiguration(initializers=TestDatabase.class)
@Import(AccountIntegrationTests.Fakes.class)
class SignupEmailIntegrationTests {
 static final String ROOT="/api/v1/auth/signup/email";
 @Autowired WebApplicationContext context;@Autowired JdbcTemplate db;
 @Autowired AccountIntegrationTests.TestMail mail;@Autowired AccountIntegrationTests.TestClock clock;
 MockMvc mvc;final JsonMapper json=JsonMapper.builder().build();
 record Candidate(String login,String email,MockHttpSession session){}
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();mail.fail=false;}
 MockHttpSession session()throws Exception{return (MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andReturn().getRequest().getSession(false);}
 Candidate candidate()throws Exception{String id="u"+UUID.randomUUID().toString().replace("-","").substring(0,15);return new Candidate(id,id+"@sju.ac.kr",session());}
 String token(MockHttpSession session)throws Exception{return json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).get("token").asText();}
 ResultActions post(String path,MockHttpSession session,Object body)throws Exception{
  return mvc.perform(postRequest(path,session,body));
 }
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postRequest(String path,MockHttpSession session,Object body)throws Exception{
  return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).session(session).header("X-CSRF-TOKEN",token(session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
 }
 Map<String,Object> signup(Candidate c){return Map.of("schoolEmail",c.email(),"password","Signup-test-1234","name","가입 테스트","isTransfer",false);}
 void send(Candidate c)throws Exception{post(ROOT+"/send",c.session(),Map.of("schoolEmail",c.email())).andExpect(status().isOk());}
 void verify(Candidate c)throws Exception{
  send(c);post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",mail.codes.get(c.email()))).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
 }
 int students(Candidate c){return db.queryForObject("select count(*) from student where school_email=?",Integer.class,c.email());}
 @Test void unverifiedSignupCannotCreateAccountEvenWithForgedVerifiedFields()throws Exception{
  var c=candidate();var input=new HashMap<>(signup(c));input.put("emailVerified",true);input.put("verificationToken","forged");
  post("/api/v1/auth/signup",c.session(),input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
  assertThat(students(c)).isZero();
 }
 @Test void availabilitySendConfirmSignupLoginWorksWithCsrfAndVerifiedProfile()throws Exception{
  var c=candidate();
  post(ROOT+"/availability",c.session(),Map.of("schoolEmail",c.email())).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
  assertThat(students(c)).isZero();verify(c);assertThat(students(c)).isZero();
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(true));
  post(ROOT+"/availability",c.session(),Map.of("schoolEmail",c.email())).andExpect(jsonPath("$.available").value(false));
  post(ROOT+"/send",c.session(),Map.of("schoolEmail",c.email())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
  post("/api/v1/auth/login",c.session(),Map.of("schoolEmail",c.email(),"password","Signup-test-1234")).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
  mvc.perform(get("/api/v1/me").session(c.session())).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
  assertThat(db.queryForObject("select owner_hash from signup_email_challenge where school_email=?",String.class,c.email())).isNull();
 }
 @Test void sessionAndEmailBindingCannotBeBypassedAndConsumedProofCannotBeReused()throws Exception{
  var c=candidate();send(c);String code=mail.codes.get(c.email());
  post(ROOT+"/confirm",session(),Map.of("schoolEmail",c.email(),"code",code)).andExpect(status().isBadRequest());
  post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",code)).andExpect(status().isOk());
  post("/api/v1/auth/signup",session(),signup(c)).andExpect(status().isBadRequest());
  var different=new Candidate(c.login(),"different"+c.email(),c.session());
  post("/api/v1/auth/signup",c.session(),signup(different)).andExpect(status().isBadRequest());
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isCreated());
  var replay=new Candidate("v"+c.login(),c.email(),c.session());
  post("/api/v1/auth/signup",c.session(),signup(replay)).andExpect(status().isConflict());
  assertThat(students(c)).isEqualTo(1);
 }
 @Test void expiredProofAndResendInvalidateSignupPermission()throws Exception{
  var c=candidate();verify(c);clock.advance(600);
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
  verify(c);clock.advance(60);send(c);
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isBadRequest());
 }
 @Test void wrongCodesPersistLockoutAndExpiryIsEnforced()throws Exception{
  var c=candidate();send(c);String correct=mail.codes.get(c.email()),wrong=correct.equals("000000")?"111111":"000000";
  for(int i=0;i<4;i++)post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",wrong)).andExpect(status().isBadRequest());
  post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",wrong)).andExpect(status().isTooManyRequests());
  post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",correct)).andExpect(status().isTooManyRequests());
  clock.advance(60);send(c);clock.advance(600);
  post(ROOT+"/confirm",c.session(),Map.of("schoolEmail",c.email(),"code",mail.codes.get(c.email()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
 }
 @Test void failedSmtpInvalidatesPriorProofAndPersistsQuotaWithoutLeakingDetails()throws Exception{
  var c=candidate();verify(c);clock.advance(60);mail.fail=true;
  var result=post(ROOT+"/send",c.session(),Map.of("schoolEmail",c.email())).andExpect(status().isServiceUnavailable()).andReturn();
  assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic SMTP",mail.codes.get(c.email()));
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("select send_count from signup_email_challenge where school_email=?",Integer.class,c.email())).isEqualTo(2);
  post(ROOT+"/send",session(),Map.of("schoolEmail",c.email())).andExpect(status().isTooManyRequests());
 }
 @Test void emailQuotaIsSharedAcrossAnonymousSessions()throws Exception{
  var c=candidate();mail.fail=true;
  for(int i=0;i<10;i++){post(ROOT+"/send",session(),Map.of("schoolEmail",c.email())).andExpect(status().isServiceUnavailable());clock.advance(60);}
  post(ROOT+"/send",session(),Map.of("schoolEmail",c.email())).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("EMAIL_SEND_LIMIT"));
  clock.advance(86400);mail.fail=false;send(c);
 }
 @Test void sessionBudgetLimitsMultipleRecipientAddresses()throws Exception{
  var session=session();mail.fail=true;
  for(int i=0;i<10;i++)post(ROOT+"/send",session,Map.of("schoolEmail",candidate().email())).andExpect(status().isServiceUnavailable());
  post(ROOT+"/send",session,Map.of("schoolEmail",candidate().email())).andExpect(status().isTooManyRequests());
 }
 @Test void failedSignupRollsBackConsumptionAndCanBeRetried()throws Exception{
  var c=candidate();verify(c);
  db.execute("alter table student add constraint signup_rollback_guard check(school_email<>'"+c.email()+"')");
  try{post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isConflict());assertThat(students(c)).isZero();}
  finally{db.execute("alter table student drop constraint signup_rollback_guard");}
  post("/api/v1/auth/signup",c.session(),signup(c)).andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(true));
 }
 @Test void concurrentSignupsCreateExactlyOneAccountAndConsumeProofAtomically()throws Exception{
  var c=candidate();verify(c);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  var first=postRequest("/api/v1/auth/signup",c.session(),signup(c));
  var second=postRequest("/api/v1/auth/signup",c.session(),signup(new Candidate("v"+c.login(),c.email(),c.session())));
  try{
   var a=pool.submit(()->{start.await();return mvc.perform(first).andReturn().getResponse().getStatus();});
   var b=pool.submit(()->{start.await();return mvc.perform(second).andReturn().getResponse().getStatus();});
   start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).contains(201).allMatch(i->i==201||i==400||i==409);
   assertThat(students(c)).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
 @Test void validationCsrfAndSwaggerDescribePreSignupFlow()throws Exception{
  var c=candidate();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ROOT+"/send").session(c.session()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("schoolEmail",c.email())))).andExpect(status().isForbidden());
  for(String email:List.of("bad@example.com","UPPER@sju.ac.kr","bad@@sju.ac.kr","")){
   post(ROOT+"/send",c.session(),Map.of("schoolEmail",email)).andExpect(status().isBadRequest());
  }
  var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  for(String action:List.of("availability","send","confirm")){
   var op=spec.get("paths").get(ROOT+"/"+action).get("post");
   assertThat(op.get("security").get(0).has("csrfToken")).isTrue();
   assertThat(op.get("security").get(0).has("sessionCookie")).isFalse();
   assertThat(op.get("requestBody")).isNotNull();
  }
  assertThat(spec.get("paths").get("/api/v1/auth/signup").get("post").get("responses").get("400").get("description").asText()).contains("EMAIL_VERIFICATION_REQUIRED");
 }
}
