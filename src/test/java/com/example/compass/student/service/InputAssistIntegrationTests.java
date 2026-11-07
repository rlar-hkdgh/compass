package com.example.compass.student.service;
import com.example.compass.student.dto.InputAssistDtos;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;


import com.example.compass.TestDatabase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URI;
import static com.example.compass.student.dto.InputAssistDtos.*;
import static com.example.compass.student.dto.StudentDtos.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:input-assist-isolated;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","compass.recruit.import-enabled=false"})
@ContextConfiguration(initializers=TestDatabase.class)
@Import(InputAssistIntegrationTests.Fakes.class)
class InputAssistIntegrationTests {
    static class MutableClock extends Clock {
        volatile Instant now=Instant.parse("2026-11-05T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
        void advance(long seconds){now=now.plusSeconds(seconds);}
    }
    static class FakeTransport implements GitHubReadmeTransport {
        volatile Reply reply;
        volatile Runnable callback;
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        volatile URI uri;
        public Reply get(URI uri) {this.uri=uri;calls.incrementAndGet();if(callback!=null)callback.run();return reply;}
        void reset(){reply=new Reply(404,Map.of(),new byte[0]);callback=null;calls.set(0);uri=null;}
    }
    @TestConfiguration static class Fakes {
        @Bean @Primary FakeTransport fakeTransport(){return new FakeTransport();}
        @Bean @Primary MutableClock fakeClock(){return new MutableClock();}
    }
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired FakeTransport upstream;
    @Autowired MutableClock clock;
    @Autowired ProjectReadmeService readmes;
    @Autowired StudentInputService students;
    MockMvc mvc;
    final JsonMapper json=JsonMapper.builder().build();
    record Account(long id,MockHttpSession session){}
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();upstream.reset();clock.now=Instant.parse("2026-11-05T00:00:00Z");}
    MockHttpSession anonymous() throws Exception{return (MockHttpSession)mvc.perform(get("/api/v1/auth/csrf")).andReturn().getRequest().getSession();}
    String token(MockHttpSession session) throws Exception {return json.readTree(mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).get("token").asText();}
    ResultActions send(String method,String path,MockHttpSession session,Object input) throws Exception {
        var request=request(org.springframework.http.HttpMethod.valueOf(method),path).session(session).header("X-CSRF-TOKEN",token(session)).contentType(MediaType.APPLICATION_JSON);
        if(input!=null)request.content(json.writeValueAsString(input));return mvc.perform(request);
    }
    Account account() throws Exception {
        String login="u"+UUID.randomUUID().toString().replace("-","").substring(0,15),password=UUID.randomUUID().toString();
        var session=anonymous();
        VerifiedSignupFixture.prepare(db,session,login+"@sju.ac.kr");
        var signup=send("POST","/api/v1/auth/signup",session,Map.of("password",password,"schoolEmail",login+"@sju.ac.kr","name","입력 보조 테스트","isTransfer",false)).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(signup.getResponse().getContentAsString()).get("id").asLong();
        send("POST","/api/v1/auth/login",session,Map.of("schoolEmail",login+"@sju.ac.kr","password",password)).andExpect(status().isOk());
        return new Account(id,session);
    }
    JsonNode preview(Account a,byte[] bytes) throws Exception {
        var request=multipart("/api/v1/me/courses/import/preview").file(new MockMultipartFile("file","transcript.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes)).session(a.session()).header("X-CSRF-TOKEN",token(a.session()));
        var result=mvc.perform(request).andExpect(status().isOk()).andReturn();return json.readTree(result.getResponse().getContentAsString());
    }
    List<ConfirmedRow> rows(JsonNode preview) {List<ConfirmedRow> rows=new ArrayList<>();for(var row:preview.get("rows"))rows.add(new ConfirmedRow(row.get("sheetIndex").asInt(),row.get("sourceRow").asInt(),json.treeToValue(row.get("course"),CourseInput.class)));return rows;}
    Object confirmation(JsonNode preview,List<ConfirmedRow> rows){return Map.of("previewId",preview.get("previewId").asText(),"rows",rows);}
    int count(long student){return db.queryForObject("select count(*) from student_course where student_id=?",Integer.class,student);}
    long project(Account a) throws Exception {var result=send("POST","/api/v1/me/projects",a.session(),projectInput("https://github.com/fixture/repository")).andExpect(status().isCreated()).andReturn();return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();}
    ProjectInput projectInput(String url){return new ProjectInput("프로젝트","목적과 기능","본인의 구현",null,url,List.of());}
    String readmePath(long project){return "/api/v1/me/projects/"+project+"/readme";}
    @Test void previewDoesNotWriteAndConfirmSavesOnlyReviewedRowsWithNumericIdempotence() throws Exception {
        var a=account();var preview=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));
        assertThat(count(a.id())).isZero();assertThat(preview.get("rows").size()).isEqualTo(2);
        assertThat(preview.get("gpa").asDouble()).isEqualTo(4.5);
        var selected=List.of(rows(preview).get(0));
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,selected)).andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(1));
        mvc.perform(get("/api/v1/me/courses").session(a.session())).andExpect(jsonPath("$[0].courseNumber").value("001234"));
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,selected)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREVIEW_CONSUMED"));
        var again=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));
        assertThat(again.get("rows").get(0).get("existingCourseIds").size()).isEqualTo(1);
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(again,rows(again))).andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(1)).andExpect(jsonPath("$.skipped").value(1));
        assertThat(count(a.id())).isEqualTo(2);
        assertThat(students.profile(a.id()).gpa()).isNull();
    }
    @Test void transcriptCalculatesCreditWeightedGpaAndSavesItOnlyOnConfirmation() throws Exception {
        byte[] file;
        try (var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(
                TranscriptWorkbookParserTests.workbook("수업",false,false)));
             var out=new java.io.ByteArrayOutputStream()) {
            var row=book.getSheetAt(0).getRow(5);
            row.getCell(9).setCellValue("GRADE");
            row.getCell(10).setCellValue("B0");
            row.getCell(11).setCellValue("3.0");
            book.write(out);
            file=out.toByteArray();
        }
        var a=account();var result=preview(a,file);
        assertThat(result.get("gpa").asDouble()).isEqualTo(4.13);
        assertThat(students.profile(a.id()).gpa()).isNull();
        send("POST","/api/v1/me/courses/import/confirm",a.session(),
            Map.of("previewId",result.get("previewId").asText(),"rows",rows(result),"saveGpa",true))
            .andExpect(status().isOk()).andExpect(jsonPath("$.savedGpa").value(4.13));
        assertThat(students.profile(a.id()).gpa()).isEqualByComparingTo("4.13");
        assertThat(students.profile(a.id()).gpaScale()).isEqualByComparingTo("4.5");
    }

    private org.springframework.test.web.servlet.ResultActions confirmAll(Account account, JsonNode preview) throws Exception {
        return send("POST", "/api/v1/me/courses/import/confirm-all", account.session(),
            Map.of("previewId", preview.get("previewId").asText()));
    }

    @Test void wholeImportSavesGpaAutomaticallyAndSkipsIdenticalExistingRows() throws Exception {
        var account = account();
        var file = TranscriptWorkbookParserTests.workbook("수업", false, false);
        var draft = preview(account, file);
        students.saveCourse(account.id(), null, rows(draft).get(0).course());
        draft = preview(account, file);
        assertThat(draft.at("/wholeImport/ready").asInt()).isEqualTo(1);
        assertThat(draft.at("/wholeImport/skipped").asInt()).isEqualTo(1);
        assertThat(draft.at("/wholeImport/rowIssues").size()).isZero();
        assertThat(students.profile(account.id()).gpa()).isNull();

        confirmAll(account, draft).andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(1))
            .andExpect(jsonPath("$.skipped").value(1))
            .andExpect(jsonPath("$.savedGpa").value(4.5));
        assertThat(students.profile(account.id()).gpa()).isEqualByComparingTo("4.5");
        assertThat(students.profile(account.id()).gpaScale()).isEqualByComparingTo("4.5");
        confirmAll(account, draft).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREVIEW_CONSUMED"));
        confirmAll(account, preview(account, file)).andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(0)).andExpect(jsonPath("$.skipped").value(2));
        assertThat(count(account.id())).isEqualTo(2);
    }

    @Test void wholeImportCountsIdenticalUploadRowsOnceForCoursesAndGpa() throws Exception {
        byte[] file;
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(
                TranscriptWorkbookParserTests.workbook("수업", false, false)));
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var second = sheet.getRow(5);
            second.getCell(9).setCellValue("GRADE");
            second.getCell(10).setCellValue("B0");
            second.getCell(11).setCellValue("3.0");
            sheet.copyRows(4, 4, 6, new org.apache.poi.ss.usermodel.CellCopyPolicy());
            book.write(out);
            file = out.toByteArray();
        }
        var account = account();
        var draft = preview(account, file);
        assertThat(draft.at("/wholeImport/ready").asInt()).isEqualTo(2);
        assertThat(draft.at("/wholeImport/skipped").asInt()).isEqualTo(1);
        assertThat(draft.at("/wholeImport/rowIssues").size()).isZero();
        confirmAll(account, draft).andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(2))
            .andExpect(jsonPath("$.skipped").value(1))
            .andExpect(jsonPath("$.savedGpa").value(4.13));
        assertThat(count(account.id())).isEqualTo(2);
    }

    @Test void wholeImportCountsDistinctRegularTermsAcrossYearsAndExcludesSeasonalTerms() throws Exception {
        byte[] file;
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(
                TranscriptWorkbookParserTests.workbook("수업", false, false)));
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var copy = new org.apache.poi.ss.usermodel.CellCopyPolicy();
            sheet.copyRows(5, 5, 6, copy);
            sheet.getRow(6).getCell(1).setCellValue("2025");
            sheet.copyRows(5, 5, 7, copy);
            sheet.getRow(7).getCell(3).setCellValue("REGULAR");
            sheet.copyRows(5, 5, 8, copy);
            sheet.getRow(8).getCell(2).setCellValue("여름학기");
            sheet.copyRows(5, 5, 9, copy);
            sheet.getRow(9).getCell(2).setCellValue("동계 계절학기");
            book.write(out);
            file = out.toByteArray();
        }
        var account = account();
        var draft = preview(account, file);
        assertThat(draft.get("completedSemesters").asInt()).isEqualTo(3);
        assertThat(students.profile(account.id()).semester()).isNull();
        confirmAll(account, draft).andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(6))
            .andExpect(jsonPath("$.savedCompletedSemesters").value(3))
            .andExpect(jsonPath("$.savedGpa").value(4.5));
        assertThat(students.profile(account.id()).semester()).isEqualTo(3);
        assertThat(students.profile(account.id()).gpa()).isEqualByComparingTo("4.5");
    }

    @Test void wholeImportKeepsSemesterOnUnknownTermAndSavesZeroForSeasonalOnly() throws Exception {
        var account = account();
        confirmAll(account, preview(account, TranscriptWorkbookParserTests.workbook("수업", false, false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.savedCompletedSemesters").value(2));
        for (String term : List.of("분류 불가", "여름학기")) {
            byte[] file;
            try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(
                    TranscriptWorkbookParserTests.workbook("수업", true, false)));
                 var out = new java.io.ByteArrayOutputStream()) {
                var sheet = book.getSheetAt(0);
                sheet.removeRow(sheet.getRow(4));
                sheet.getRow(5).getCell(2).setCellValue(term);
                book.write(out);
                file = out.toByteArray();
            }
            var draft = preview(account, file);
            if (term.equals("분류 불가")) {
                assertThat(draft.get("completedSemesters").isNull()).isTrue();
                confirmAll(account, draft).andExpect(status().isOk()).andExpect(jsonPath("$.savedCompletedSemesters").doesNotExist());
                assertThat(students.profile(account.id()).semester()).isEqualTo(2);
            } else {
                confirmAll(account, draft).andExpect(status().isOk()).andExpect(jsonPath("$.savedCompletedSemesters").value(0));
                assertThat(students.profile(account.id()).semester()).isZero();
                assertThat(students.profile(account.id()).gpa()).isEqualByComparingTo("4.5");
            }
        }
    }

    @Test void wholeImportReportsInvalidAndNewlyConflictingRowsWithoutOverwriting() throws Exception {
        var account = account();
        var draft = preview(account, TranscriptWorkbookParserTests.workbook("수업", true, false));
        assertThat(draft.at("/wholeImport/ready").asInt()).isEqualTo(1);
        assertThat(draft.at("/wholeImport/rowIssues/0/sourceRow").asInt()).isEqualTo(5);
        var course = rows(draft).get(1).course();
        students.saveCourse(account.id(), null, new CourseInput(course.courseNumber(), "기존 이름",
            course.academicYear(), course.semester(), course.evaluationMethod(), course.grade(), null));

        confirmAll(account, draft).andExpect(status().isOk())
            .andExpect(jsonPath("$.inserted").value(0))
            .andExpect(jsonPath("$.rowIssues.length()").value(2))
            .andExpect(jsonPath("$.rowIssues[1].issues[0].code").value("COURSE_IMPORT_CONFLICT"));
        assertThat(students.courses(account.id())).extracting(Course::courseName).containsExactly("기존 이름");
        assertThat(students.profile(account.id()).gpa()).isNull();

        var validDraft = preview(account, TranscriptWorkbookParserTests.workbook("수업", false, false));
        assertThat(validDraft.at("/wholeImport/rowIssues/0/issues/0/code").asText()).isEqualTo("COURSE_IMPORT_CONFLICT");
        confirmAll(account, validDraft).andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(1))
            .andExpect(jsonPath("$.rowIssues.length()").value(1));
        assertThat(count(account.id())).isEqualTo(2);
    }

    @Test void invalidSourceRowCanBeCorrectedAndDifferentSemestersPreserveRetakes() throws Exception {
        var a=account();var preview=preview(a,TranscriptWorkbookParserTests.workbook("수업",true,false));
        assertThat(preview.get("rows").get(0).get("errors").size()).isGreaterThan(0);
        assertThat(preview.get("gpa").isNull()).isTrue();
        var raw=rows(preview);var old=raw.get(0).course();
        var corrected=new CourseInput(old.courseNumber(),old.courseName(),old.academicYear(),old.semester(),old.evaluationMethod(),"FA",java.math.BigDecimal.ZERO);
        var first=new ConfirmedRow(0,5,corrected);
        var retake=new ConfirmedRow(0,6,new CourseInput(corrected.courseNumber(),corrected.courseName(),2025,"1학기","GRADE","A+",new java.math.BigDecimal("4.5")));
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,List.of(first,retake))).andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(2));
        mvc.perform(get("/api/v1/me/courses").session(a.session())).andExpect(jsonPath("$[0].grade").value("FA")).andExpect(jsonPath("$[1].academicYear").value(2025));
    }
    @Test void unknownDuplicateOrInvalidSelectionNeverWrites() throws Exception {
        var a=account();var preview=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));var first=rows(preview).get(0);
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,List.of(first,first))).andExpect(status().isBadRequest());
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,List.of(new ConfirmedRow(0,999,first.course())))).andExpect(status().isBadRequest());
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,List.of(first,new ConfirmedRow(0,6,first.course())))).andExpect(status().isBadRequest());
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,List.of())).andExpect(status().isBadRequest());
        assertThat(count(a.id())).isZero();
    }
    @Test void previewIsBoundToStudentSessionExpiryAndLatestUpload() throws Exception {
        var a=account();var other=account();var first=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));
        send("POST","/api/v1/me/courses/import/confirm",other.session(),confirmation(first,rows(first))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREVIEW_NOT_FOUND"));
        var second=preview(a,TranscriptWorkbookParserTests.workbook("다른 수업",false,false));
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(first,rows(first))).andExpect(status().isConflict());
        clock.advance(901);
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(second,rows(second))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PREVIEW_EXPIRED"));
        assertThat(count(a.id())).isZero();assertThat(count(other.id())).isZero();
    }
    @Test void databaseFailureRollsBackWholeImportAndDraftCanBeRetried() throws Exception {
        var a=account();var preview=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));var selected=new ArrayList<>(rows(preview));
        var row=selected.get(1);var c=row.course();selected.set(1,new ConfirmedRow(row.sheetIndex(),row.sourceRow(),new CourseInput(c.courseNumber(),"reject_import",c.academicYear(),c.semester(),c.evaluationMethod(),c.grade(),c.gradePoint())));
        db.execute("alter table student_course add constraint assist_import_guard check(course_name<>'reject_import')");
        try {send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,selected)).andExpect(status().isConflict());assertThat(count(a.id())).isZero();}
        finally {db.execute("alter table student_course drop constraint assist_import_guard");}
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,rows(preview))).andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(2));
    }
    @Test void existingConflictingRecordIsNotOverwrittenAndEarlierInsertsRollback() throws Exception {
        var a=account();var preview=preview(a,TranscriptWorkbookParserTests.workbook("수업",false,false));var selected=rows(preview);var second=selected.get(1).course();
        students.saveCourse(a.id(),null,new CourseInput(second.courseNumber(),"기존 이름",second.academicYear(),second.semester(),second.evaluationMethod(),second.grade(),null));
        send("POST","/api/v1/me/courses/import/confirm",a.session(),confirmation(preview,selected)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_IMPORT_CONFLICT"));
        assertThat(count(a.id())).isEqualTo(1);assertThat(students.courses(a.id()).get(0).courseName()).isEqualTo("기존 이름");
    }
    @Test void uploadRequiresAuthenticationCsrfCorrectPartAndBoundedXlsx() throws Exception {
        var a=account();byte[] file=TranscriptWorkbookParserTests.workbook("수업",false,false);
        mvc.perform(multipart("/api/v1/me/courses/import/preview").file(new MockMultipartFile("file","sample.xlsx","application/octet-stream",file)).session(a.session())).andExpect(status().isForbidden());
        var anon=anonymous();mvc.perform(multipart("/api/v1/me/courses/import/preview").file(new MockMultipartFile("file","sample.xlsx","application/octet-stream",file)).session(anon).header("X-CSRF-TOKEN",token(anon))).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/me/courses/import/preview").session(a.session()).header("X-CSRF-TOKEN",token(a.session()))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/me/courses/import/preview").file(new MockMultipartFile("file","sample.xls","application/octet-stream",file)).session(a.session()).header("X-CSRF-TOKEN",token(a.session()))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/me/courses/import/preview").file(new MockMultipartFile("file","sample.xlsx","application/octet-stream",new byte[TranscriptWorkbookParser.MAX_FILE_BYTES+1])).session(a.session()).header("X-CSRF-TOKEN",token(a.session()))).andExpect(status().isPayloadTooLarge());
    }
    @Test void readmeOwnershipAndCsrfAreCheckedBeforeAnyNetworkRequest() throws Exception {
        var owner=account();var other=account();long id=project(owner);String path=readmePath(id);
        mvc.perform(get(path).session(other.session())).andExpect(status().isNotFound());
        send("POST",path+"/collect",other.session(),null).andExpect(status().isNotFound());
        mvc.perform(post(path+"/collect").session(owner.session())).andExpect(status().isForbidden());
        mvc.perform(get(path).session(owner.session())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("README_NOT_COLLECTED"));
        assertThat(upstream.calls.get()).isZero();
    }
    @Test void readmeSuccessCooldownAndFailuresKeepLastSuccessfulSnapshot() throws Exception {
        var a=account();long id=project(a);String path=readmePath(id);
        upstream.reply=new GitHubReadmeTransport.Reply(200,Map.of(),GitHubReadmeClientTests.success("# 이전 성공본"));
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.lastAttemptStatus").value("SUCCESS")).andExpect(jsonPath("$.content").value("# 이전 성공본"));
        String successful=readmes.get(a.id(),id).lastSuccessAt().toString();
        assertThat(upstream.uri.toString()).isEqualTo("https://api.github.com/repos/fixture/repository/readme");
        send("POST",path+"/collect",a.session(),null).andExpect(status().isTooManyRequests());assertThat(upstream.calls.get()).isEqualTo(1);
        clock.advance(31);upstream.reply=new GitHubReadmeTransport.Reply(404,Map.of(),new byte[0]);
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.lastAttemptStatus").value("NOT_FOUND")).andExpect(jsonPath("$.content").value("# 이전 성공본"));
        assertThat(readmes.get(a.id(),id).lastSuccessAt().toString()).isEqualTo(successful);
        clock.advance(31);upstream.reply=new GitHubReadmeTransport.Reply(403,Map.of("X-RateLimit-Remaining",List.of("0")),new byte[0]);
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.lastAttemptStatus").value("RATE_LIMITED")).andExpect(jsonPath("$.readmeSha").value("a".repeat(40)));
        mvc.perform(get(path).session(a.session())).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("# 이전 성공본"));
        clock.advance(31);upstream.reply=new GitHubReadmeTransport.Reply(200,Map.of(),GitHubReadmeClientTests.success("새 성공본"));
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("새 성공본")).andExpect(jsonPath("$.lastAttemptStatus").value("SUCCESS")).andExpect(jsonPath("$.lastErrorCode").doesNotExist());
        assertThat(readmes.get(a.id(),id).lastSuccessAt().toString().equals(successful)).isFalse();
    }
    @Test void firstFailureStoresOnlyAttemptAndChangingUrlClearsOldSnapshot() throws Exception {
        var a=account();long id=project(a);String path=readmePath(id);
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.lastAttemptStatus").value("NOT_FOUND")).andExpect(jsonPath("$.content").doesNotExist());
        students.saveProject(a.id(),id,projectInput("https://github.com/fixture/changed"));
        mvc.perform(get(path).session(a.session())).andExpect(status().isNotFound());
        upstream.reply=new GitHubReadmeTransport.Reply(200,Map.of(),GitHubReadmeClientTests.success("새 성공본"));
        send("POST",path+"/collect",a.session(),null).andExpect(status().isOk()).andExpect(jsonPath("$.repositoryUrl").value("https://github.com/fixture/changed"));
        students.deleteProject(a.id(),id);assertThat(db.queryForObject("select count(*) from student_project_readme where project_id=?",Integer.class,id)).isZero();
    }
    @Test void concurrentCollectionDoesNotLockDatabaseOrWriteResultForChangedUrl() throws Exception {
        var a=account();long id=project(a);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        upstream.reply=new GitHubReadmeTransport.Reply(200,Map.of(),GitHubReadmeClientTests.success("stale result"));
        upstream.callback=()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
        var executor=Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->readmes.collect(a.id(),id));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            send("POST",readmePath(id)+"/collect",a.session(),null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("README_COLLECTION_IN_PROGRESS"));
            // Must complete while the network call is still blocked.
            students.saveProject(a.id(),id,projectInput("https://github.com/fixture/new-url"));release.countDown();
            assertThatThrownBy(()->future.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiErrors.Failure.class);
            assertThat(db.queryForObject("select count(*) from student_project_readme where project_id=?",Integer.class,id)).isZero();assertThat(upstream.calls.get()).isEqualTo(1);
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void repositoryUrlIsRequiredAndMissingRepositoryDoesNotTriggerArbitraryFetch() throws Exception {
        var a=account();var result=send("POST","/api/v1/me/projects",a.session(),projectInput(null)).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        send("POST",readmePath(id)+"/collect",a.session(),null).andExpect(status().isBadRequest());
        assertThat(upstream.calls.get()).isZero();
        assertThat(db.queryForObject("select count(*) from student_project_readme where project_id=?",Integer.class,id)).isZero();
    }    @Test void multipartSizeFailureBeforeHandlerSelectionUsesCommonErrorBody() throws Exception {
        var a=account();String csrf=token(a.session());
        mvc.perform(servletContext->{
            var request=new MockHttpServletRequest(servletContext) {
                @Override public java.util.Collection<jakarta.servlet.http.Part> getParts() {
                    throw new org.springframework.web.multipart.MaxUploadSizeExceededException(5*1024*1024);
                }
            };
            request.setMethod("POST");request.setRequestURI("/api/v1/me/courses/import/preview");
            request.setContentType("multipart/form-data; boundary=fixture");request.setSession(a.session());
            request.addHeader("X-CSRF-TOKEN",csrf);return request;
        }).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }}


