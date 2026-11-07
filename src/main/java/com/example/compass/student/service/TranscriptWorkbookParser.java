package com.example.compass.student.service;
import com.example.compass.student.dto.InputAssistDtos;
import com.example.compass.student.exception.ApiErrors;
import com.example.compass.student.dto.StudentDtos;


import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;
import static com.example.compass.student.dto.InputAssistDtos.*;
import static com.example.compass.student.dto.StudentDtos.CourseInput;

/** Student transcript reader. Does not reuse the public timetable collector. */
@Component
public class TranscriptWorkbookParser {
    public static final int MAX_FILE_BYTES=5*1024*1024;
    private static final int MAX_EXPANDED_BYTES=24*1024*1024;
    private static final Map<String,String> HEADERS=Map.ofEntries(
        Map.entry("년도","academicYear"),Map.entry("연도","academicYear"),Map.entry("학년도","academicYear"),
        Map.entry("학기","semester"),Map.entry("학수번호","courseNumber"),Map.entry("과목번호","courseNumber"),
        Map.entry("교과목명","courseName"),Map.entry("과목명","courseName"),
        Map.entry("평가방식","evaluationMethod"),Map.entry("평가방법","evaluationMethod"),
        Map.entry("학점","credits"),Map.entry("등급","grade"),Map.entry("성적등급","grade"),Map.entry("평점","gradePoint"));
    private static final Set<String> REQUIRED=Set.of("academicYear","semester","courseNumber","courseName","evaluationMethod","grade");
    private final Validator validator;
    public TranscriptWorkbookParser(Validator validator) { this.validator=validator; }

    public ParsedTranscript parse(byte[] file) {
        checkArchive(file);
        List<PreviewRow> rows=new ArrayList<>();
        List<Issue> warnings=new ArrayList<>();
        var formatter=new DataFormatter(Locale.ROOT);
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(file))) {
            if (workbook.isMacroEnabled() || workbook.getNumberOfSheets()>5) throw invalidFile("매크로 또는 5개를 초과하는 시트는 지원하지 않습니다.");
            int visited=0;
            for (int sheetIndex=0;sheetIndex<workbook.getNumberOfSheets();sheetIndex++) {
                var sheet=workbook.getSheetAt(sheetIndex);
                Map<String,Integer> columns=null;
                boolean hasContent=false;
                for (Row row:sheet) {
                    if (++visited>5000 || row.getLastCellNum()>64 || row.getRowNum()>10000) throw invalidFile("성적표 행 또는 열 제한을 초과했습니다.");
                    if (blank(row,formatter)) continue;
                    hasContent=true;
                    Map<String,Integer> candidate=header(row,formatter);
                    if (candidate.keySet().containsAll(REQUIRED)) {
                        if (columns!=null && !columns.equals(candidate)) throw invalidFile("같은 시트에 서로 다른 열 구성이 있습니다.");
                        columns=candidate;
                        continue; // Includes the duplicated header in the supplied portal export.
                    }
                    if (columns==null) continue; // Student identity/title rows are never imported.
                    List<Issue> errors=new ArrayList<>(), rowWarnings=new ArrayList<>();
                    Map<String,String> raw=new LinkedHashMap<>();
                    for (var column:columns.entrySet()) {
                        var cell=row.getCell(column.getValue());
                        if (cell!=null && (cell.getCellType()==CellType.FORMULA || cell.getCellType()==CellType.ERROR || cell.getCellType()==CellType.BOOLEAN)) {
                            if (!"credits".equals(column.getKey())) errors.add(new Issue("UNSUPPORTED_CELL","수식·오류·논리값 셀은 직접 확인해 수정하세요."));
                            raw.put(column.getKey(),"");
                        } else {
                            String text=formatter.formatCellValue(cell).trim();
                            if (text.length()>1000) throw invalidFile("셀의 텍스트 길이 제한을 초과했습니다.");
                            raw.put(column.getKey(),text);
                        }
                    }
                    if (raw.values().stream().allMatch(String::isBlank) && errors.isEmpty()) continue;
                    var numberCell=row.getCell(columns.get("courseNumber"));
                    if (numberCell!=null && numberCell.getCellType()==CellType.NUMERIC) rowWarnings.add(new Issue("NUMERIC_COURSE_NUMBER","숫자로 저장된 학수번호입니다. 표시된 앞자리 0이 맞는지 확인하세요."));
                    if (!columns.containsKey("gradePoint")) rowWarnings.add(new Issue("MISSING_GRADE_POINT_COLUMN","평점 열이 없어 NULL로 추출했습니다."));
                    CourseInput course=null;
                    try {
                        BigDecimal point=raw.getOrDefault("gradePoint","").isBlank()?null:new BigDecimal(raw.get("gradePoint"));
                        if ("P/NP".equals(raw.get("evaluationMethod")) && point!=null && point.signum()==0) {
                            point=null;
                            rowWarnings.add(new Issue("PASS_ZERO_TO_NULL","원본 P/NP 평점 0을 NULL로 변환했습니다."));
                        }
                        course=new CourseInput(raw.get("courseNumber"),raw.get("courseName"),Integer.valueOf(raw.get("academicYear")),raw.get("semester"),raw.get("evaluationMethod"),raw.get("grade"),point);
                        if (!validator.validate(course).isEmpty()) errors.add(new Issue("INVALID_COURSE","필수 항목·길이·연도·평점을 확인하세요."));
                        else StudentInputService.validateCourse(course);
                    } catch (NumberFormatException e) {
                        errors.add(new Issue("INVALID_NUMBER","년도 또는 평점이 숫자 형식이 아닙니다."));
                    } catch (ApiErrors.Failure e) {
                        errors.add(new Issue(e.code,e.getMessage()));
                    }
                    rows.add(new PreviewRow(sheetIndex,sheet.getSheetName(),row.getRowNum()+1,Collections.unmodifiableMap(raw),course,List.copyOf(errors),List.copyOf(rowWarnings),List.of()));
                    if (rows.size()>1000) throw invalidFile("한 번에 1000개 과목까지 처리할 수 있습니다.");
                }
                if (columns==null && hasContent) warnings.add(new Issue("UNRECOGNIZED_SHEET","시트 "+(sheetIndex+1)+"에서 성적표 열을 찾지 못했습니다. 해당 시트는 추출되지 않았습니다."));
            }
        } catch (ApiErrors.Failure e) { throw e; }
        catch (Exception e) { throw invalidFile("정상적인 XLSX 성적표를 읽을 수 없습니다. 암호화·손상 여부를 확인하세요."); }
        if (rows.isEmpty()) throw invalidFile("지원하는 성적표 열 또는 과목 행을 찾지 못했습니다.");
        return new ParsedTranscript(List.copyOf(rows),List.copyOf(warnings));
    }
    private static boolean blank(Row row,DataFormatter formatter) {
        for (Cell c:row) if (c.getCellType()==CellType.FORMULA || !formatter.formatCellValue(c).isBlank()) return false;
        return true;
    }
    private static Map<String,Integer> header(Row row,DataFormatter formatter) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for (Cell c:row) {
            if (c.getCellType()!=CellType.STRING) continue;
            String field=HEADERS.get(formatter.formatCellValue(c).replaceAll("\\s+",""));
            if (field!=null && result.putIfAbsent(field,c.getColumnIndex())!=null) throw invalidFile("같은 항목의 열이 중복되어 있습니다.");
        }
        return result;
    }
    private static void checkArchive(byte[] file) {
        if (file.length==0 || file.length>MAX_FILE_BYTES) throw new ApiErrors.Failure(413,"UPLOAD_TOO_LARGE","비어 있지 않은 5MiB 이하 XLSX 파일이 필요합니다.");
        if (file.length<4 || file[0]!='P' || file[1]!='K') throw invalidFile("XLSX 파일만 지원합니다.");
        int total=0,entries=0;boolean workbook=false;
        Set<String> names=new HashSet<>();
        try (var zip=new ZipInputStream(new ByteArrayInputStream(file))) {
            byte[] buffer=new byte[8192];
            for (var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                String name=entry.getName();
                if (++entries>256 || !names.add(name) || name.contains("..") || name.startsWith("/") || name.contains("\\") || name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) throw invalidFile("지원하지 않는 XLSX 압축 구조입니다.");
                if (name.equals("xl/workbook.xml")) workbook=true;
                int size=0;
                for (int n;(n=zip.read(buffer))!=-1;) {
                    total+=n;size+=n;
                    if (total>MAX_EXPANDED_BYTES || size>8*1024*1024) throw invalidFile("XLSX 압축 해제 크기 제한을 초과했습니다.");
                }
            }
        } catch (IOException e) { throw invalidFile("손상된 XLSX 압축 파일입니다."); }
        if (!workbook) throw invalidFile("XLSX 통합 문서가 아닙니다.");
    }
    private static ApiErrors.Failure invalidFile(String message) { return new ApiErrors.Failure(400,"INVALID_TRANSCRIPT",message); }
}
