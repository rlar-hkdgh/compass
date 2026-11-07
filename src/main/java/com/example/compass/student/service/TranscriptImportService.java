package com.example.compass.student.service;
import com.example.compass.student.dto.InputAssistDtos;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;


import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import static com.example.compass.student.dto.InputAssistDtos.*;
import static com.example.compass.student.dto.StudentDtos.*;

@Service
public class TranscriptImportService {
    private static final String ATTRIBUTE=TranscriptImportService.class.getName()+".draft";
    private final TranscriptWorkbookParser parser;
    private final StudentInputService students;
    private final Validator validator;
    private final Clock clock;
    private final TransactionTemplate tx;
    private static final class Draft {
        final long student;
        final TranscriptPreview preview;
        boolean used;
        Draft(long student,TranscriptPreview preview) { this.student=student;this.preview=preview; }
    }
    public TranscriptImportService(TranscriptWorkbookParser parser,StudentInputService students,Validator validator,Clock clock,PlatformTransactionManager transactions) {
        this.parser=parser;this.students=students;this.validator=validator;this.clock=clock;this.tx=new TransactionTemplate(transactions);
    }
    public TranscriptPreview preview(long student,HttpSession session,MultipartFile file) {
        String name=file.getOriginalFilename();
        if (name==null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw ApiErrors.invalid("확장자가 .xlsx인 성적표가 필요합니다.");
        if (file.isEmpty() || file.getSize()>TranscriptWorkbookParser.MAX_FILE_BYTES) throw new ApiErrors.Failure(413,"UPLOAD_TOO_LARGE","비어 있지 않은 5MiB 이하 XLSX 파일이 필요합니다.");
        ParsedTranscript parsed;
        try (var input=file.getInputStream()) {
            parsed=parser.parse(input.readNBytes(TranscriptWorkbookParser.MAX_FILE_BYTES+1));
        } catch (IOException e) { throw ApiErrors.invalid("업로드 파일을 읽을 수 없습니다."); }
        var existing=students.courses(student);
        List<PreviewRow> rows=new ArrayList<>();
        Map<CourseKey,Integer> counts=new HashMap<>();
        for (var row:parsed.rows()) if (row.course()!=null) counts.merge(key(row.course()),1,Integer::sum);
        for (var row:parsed.rows()) {
            var warnings=new ArrayList<>(row.warnings());
            List<Long> matches=List.of();
            if (row.course()!=null) {
                var courseKey=key(row.course());
                matches=existing.stream().filter(c->key(c).equals(courseKey)).map(Course::id).toList();
                if (!matches.isEmpty()) warnings.add(new Issue("EXISTING_COURSE","같은 년도·학기·학수번호의 기존 기록이 있습니다. 완전히 같은 기록은 건너뛰고 다른 내용은 충돌로 처리합니다."));
                if (counts.getOrDefault(courseKey,0)>1) warnings.add(new Issue("DUPLICATE_SOURCE_COURSE","파일에 같은 년도·학기·학수번호가 중복됩니다. 하나만 선택하거나 수정하세요."));
            }
            rows.add(new PreviewRow(row.sheetIndex(),row.sheetName(),row.sourceRow(),row.raw(),row.course(),row.errors(),List.copyOf(warnings),matches));
        }
        var preview=new TranscriptPreview(UUID.randomUUID().toString(),clock.instant().plus(Duration.ofMinutes(15)),List.copyOf(rows),parsed.warnings(),transcriptGpa(parsed), completedSemesters(parsed),
            planWholeImport(rows, existing).summary());
        session.setAttribute(ATTRIBUTE,new Draft(student,preview)); // One bounded draft per authenticated session; file bytes are discarded.
        return preview;
    }
    public ImportResult confirm(long student,HttpSession session,ConfirmTranscript request) {
        return useDraft(student, session, request.previewId(), preview -> {
            boolean saveGpa=Boolean.TRUE.equals(request.saveGpa());
            BigDecimal gpa=saveGpa?preview.gpa():null;
            if(saveGpa && gpa==null) throw ApiErrors.invalid("이 파일에서 계산한 평점이 없습니다.");
            Set<RowKey> available=new HashSet<>(), selected=new HashSet<>();
            preview.rows().forEach(r->available.add(new RowKey(r.sheetIndex(),r.sourceRow())));
            Set<CourseKey> courseKeys=new HashSet<>();
            for (var row:request.rows()) {
                var rowKey=new RowKey(row.sheetIndex(),row.sourceRow());
                if (!available.contains(rowKey) || !selected.add(rowKey)) throw ApiErrors.invalid("미리보기의 행을 중복 없이 선택하세요.");
                if (!validator.validate(row.course()).isEmpty()) throw ApiErrors.invalid("수정한 과목 필드를 확인하세요.");
                StudentInputService.validateCourse(row.course());
                if (!courseKeys.add(key(row.course()))) throw new ApiErrors.Failure(400,"DUPLICATE_INPUT","같은 년도·학기·학수번호는 한 번만 선택하세요.");
            }
            return tx.execute(status->{
                students.lock(student);
                var existing=students.courses(student);
                List<Long> inserted=new ArrayList<>();
                int skipped=0;
                for (var row:request.rows()) {
                    var matching=existing.stream().filter(c->key(c).equals(key(row.course()))).toList();
                    if (!matching.isEmpty()) {
                        if (matching.stream().allMatch(c->input(c).equals(normalize(row.course())))) { skipped++;continue; }
                        throw new ApiErrors.Failure(409,"COURSE_IMPORT_CONFLICT","기존 과목 내용과 다릅니다. 기존 기록을 개별 수정하거나 해당 행의 선택을 해제하세요.");
                    }
                    inserted.add(students.saveCourse(student,null,row.course()).id());
                }
                saveTranscriptProfile(student, gpa, null);
                return new ImportResult(inserted.size(),skipped,List.copyOf(inserted),gpa);
            });
        });
    }

    public WholeImportResult confirmAll(long student, HttpSession session, ConfirmWholeTranscript request) {
        return useDraft(student, session, request.previewId(), preview -> tx.execute(status -> {
            students.lock(student);
            var plan = planWholeImport(preview.rows(), students.courses(student));
            for (var course : plan.courses()) students.saveCourse(student, null, course);
            var summary = plan.summary();
            boolean hasCourses = summary.ready() + summary.skipped() > 0;
            BigDecimal gpa = hasCourses ? preview.gpa() : null;
            Integer semesters = hasCourses ? preview.completedSemesters() : null;
            saveTranscriptProfile(student, gpa, semesters);
            return new WholeImportResult(summary.ready(), summary.skipped(), summary.rowIssues(), gpa, semesters);
        }));
    }

    private <T> T useDraft(long student, HttpSession session, String previewId, Function<TranscriptPreview, T> action) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (!(value instanceof Draft draft) || draft.student != student || !draft.preview.previewId().equals(previewId))
            throw new ApiErrors.Failure(409, "PREVIEW_NOT_FOUND", "같은 로그인 세션에서 성적표 미리보기를 다시 생성하세요.");
        synchronized (draft) {
            if (draft.used) throw new ApiErrors.Failure(409, "PREVIEW_CONSUMED", "이미 저장한 미리보기입니다.");
            if (!clock.instant().isBefore(draft.preview.expiresAt())) {
                if (session.getAttribute(ATTRIBUTE) == draft) session.removeAttribute(ATTRIBUTE);
                throw new ApiErrors.Failure(409, "PREVIEW_EXPIRED", "미리보기 유효시간이 지났습니다. 파일을 다시 올리세요.");
            }
            T result = action.apply(draft.preview);
            draft.used = true;
            return result;
        }
    }

    private void saveTranscriptProfile(long student, BigDecimal gpa, Integer semesters) {
        if (gpa != null || semesters != null) {
            students.patch(student, new ProfilePatch(null, null, null, semesters, gpa, gpa == null ? null : new BigDecimal("4.5")));
        }
    }

    private record RegularTerm(int year, String semester) {}

    private static Integer completedSemesters(ParsedTranscript transcript) {
        if (!transcript.warnings().isEmpty()) return null;
        Set<RegularTerm> terms = new HashSet<>();
        for (var row : transcript.rows()) {
            String semester = row.raw().getOrDefault("semester", "").replaceAll("\\s+", "");
            if (semester.contains("계절학기") || semester.equals("계절") || semester.matches("(여름|겨울|하계|동계)(학기)?")) continue;
            if (!semester.equals("1학기") && !semester.equals("2학기")) return null;
            String year = row.raw().getOrDefault("academicYear", "");
            if (!year.matches("[0-9]{4}")) return null;
            int academicYear = Integer.parseInt(year);
            if (academicYear < 1900 || academicYear > 2100) return null;
            terms.add(new RegularTerm(academicYear, semester));
        }
        return terms.size();
    }

    private record WholeImportPlan(List<CourseInput> courses, WholeImportSummary summary) {}

    private static WholeImportPlan planWholeImport(List<PreviewRow> rows, List<Course> existing) {
        Map<CourseKey, Set<CourseInput>> uploaded = new HashMap<>();
        Map<CourseKey, Set<CourseInput>> saved = new HashMap<>();
        for (var row : rows) {
            if (row.course() != null && row.errors().isEmpty())
                uploaded.computeIfAbsent(key(row.course()), ignored -> new HashSet<>()).add(normalize(row.course()));
        }
        for (var course : existing)
            saved.computeIfAbsent(key(course), ignored -> new HashSet<>()).add(input(course));

        Set<CourseKey> accepted = new HashSet<>();
        List<CourseInput> courses = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        int skipped = 0;
        for (var row : rows) {
            List<Issue> rowIssues = row.errors();
            if (rowIssues.isEmpty() && row.course() != null) {
                var course = normalize(row.course());
                var key = key(course);
                var stored = saved.get(key);
                if (uploaded.get(key).size() > 1) {
                    rowIssues = List.of(new Issue("DUPLICATE_SOURCE_COURSE", "파일에 같은 연도·학기·학수번호로 서로 다른 내용이 있습니다."));
                } else if (stored != null && !stored.equals(Set.of(course))) {
                    rowIssues = List.of(new Issue("COURSE_IMPORT_CONFLICT", "같은 연도·학기·학수번호의 기존 기록과 내용이 다릅니다."));
                } else if (stored != null || !accepted.add(key)) {
                    skipped++;
                } else {
                    courses.add(row.course());
                }
            } else if (rowIssues.isEmpty()) {
                rowIssues = List.of(new Issue("INVALID_COURSE", "이 행의 과목 정보를 읽을 수 없습니다."));
            }
            if (!rowIssues.isEmpty()) issues.add(new RowIssue(row.sheetName(), row.sourceRow(), rowIssues));
        }
        return new WholeImportPlan(courses, new WholeImportSummary(courses.size(), skipped, List.copyOf(issues)));
    }

    private record CreditCourse(CourseInput course, BigDecimal credits) {}

    private static BigDecimal transcriptGpa(ParsedTranscript transcript){
        if(!transcript.warnings().isEmpty()) return null;
        BigDecimal weighted=BigDecimal.ZERO, credits=BigDecimal.ZERO;
        Set<String> gradedCourses=new HashSet<>();
        Map<CourseKey, CreditCourse> seen=new HashMap<>();
        for(var row:transcript.rows()){
            var course=row.course();
            if(course==null || !row.errors().isEmpty()) return null;
            boolean graded=!"P/NP".equals(course.evaluationMethod());
            BigDecimal credit=null;
            if(graded){
                String value=row.raw().getOrDefault("credits","");
                if(!value.matches("[0-9]{1,2}(\\.[0-9]{1,2})?")) return null;
                credit=new BigDecimal(value).stripTrailingZeros();
                if(credit.signum()<=0) return null;
            }
            var current=new CreditCourse(normalize(course),credit);
            var previous=seen.putIfAbsent(key(course),current);
            if(previous!=null && !previous.equals(current)) return null;
            if(previous!=null || !graded) continue;
            // A later attempt at the same graded course may replace an earlier grade.
            if(!gradedCourses.add(course.courseNumber())) return null;
            weighted=weighted.add(credit.multiply(StudentInputService.gradePoint(course.grade())));
            credits=credits.add(credit);
        }
        return credits.signum()==0?null:weighted.divide(credits,2,RoundingMode.HALF_UP);
    }

    private record CourseKey(Integer year,String semester,String number) {}
    private static CourseKey key(CourseInput c) { return new CourseKey(c.academicYear(),c.semester(),c.courseNumber()); }
    private static CourseKey key(Course c) { return new CourseKey(c.academicYear(),c.semester(),c.courseNumber()); }
    private static CourseInput normalize(CourseInput c) { return new CourseInput(c.courseNumber(),c.courseName(),c.academicYear(),c.semester(),c.evaluationMethod(),c.grade(),c.gradePoint()==null?null:c.gradePoint().stripTrailingZeros()); }
    private static CourseInput input(Course c) { return new CourseInput(c.courseNumber(),c.courseName(),c.academicYear(),c.semester(),c.evaluationMethod(),c.grade(),c.gradePoint()==null?null:c.gradePoint().stripTrailingZeros()); }
}

