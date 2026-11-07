package com.example.compass.student.service;
import com.example.compass.student.exception.ApiErrors;


import jakarta.validation.Validation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class TranscriptWorkbookParserTests {
    static final String[] HEADERS={"순번","년도","학기","학수번호","교과목명","이수구분","교직영역","선택영역","학점","평가방식","등급","평점","개설학과코드"};
    static byte[] workbook(String firstName,boolean badGrade,boolean formula) throws Exception {
        try (var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("성적");
            sheet.createRow(0).createCell(0).setCellValue("학생 신원 정보는 추출 대상 아님");
            for (int r:List.of(2,3)) {var header=sheet.createRow(r);for(int c=0;c<HEADERS.length;c++)header.createCell(c).setCellValue(HEADERS[c]);}
            Object[][] data={{1,"2024","1학기","001234",firstName,"전공","","",3,"GRADE",badGrade?"UNKNOWN":"A+","4.50","3513"},{2,"2024","2학기","000007","실습","전공","","",1,"P/NP","P","0","3513"}};
            for (int r=0;r<data.length;r++) {var row=sheet.createRow(r+4);for(int c=0;c<data[r].length;c++) {var cell=row.createCell(c);if(data[r][c] instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(data[r][c].toString());}}
            if (formula) sheet.getRow(4).getCell(11).setCellFormula("1+1");
            book.write(out);return out.toByteArray();
        }
    }
    static byte[] rewrite(byte[] archive,String entry,java.util.function.UnaryOperator<String> change) throws Exception {
        var out=new ByteArrayOutputStream();
        try(var input=new ZipInputStream(new ByteArrayInputStream(archive));var zip=new ZipOutputStream(out)) {
            for(var e=input.getNextEntry();e!=null;e=input.getNextEntry()) {byte[] bytes=input.readAllBytes();zip.putNextEntry(new ZipEntry(e.getName()));zip.write(e.getName().equals(entry)?change.apply(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8):bytes);zip.closeEntry();}
        }
        return out.toByteArray();
    }
    TranscriptWorkbookParser parser;
    jakarta.validation.ValidatorFactory validators;
    @BeforeEach void setup(){validators=Validation.buildDefaultValidatorFactory();parser=new TranscriptWorkbookParser(validators.getValidator());}
    @AfterEach void close(){validators.close();}
    @Test void portalExportWithWrongDimensionsAndRepeatedHeaderPreservesRowsAndNormalizesPassZero() throws Exception {
        byte[] source=rewrite(workbook("교과목",false,false),"xl/worksheets/sheet1.xml",s->s.replaceAll("<dimension ref=\"[^\"]+\"/>","<dimension ref=\"A1\"/>"));
        var result=parser.parse(source);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows()).allSatisfy(r->assertThat(r.errors()).isEmpty());
        assertThat(result.rows().get(0).sourceRow()).isEqualTo(5);
        assertThat(result.rows().get(0).course().courseNumber()).isEqualTo("001234");
        assertThat(result.rows().get(1).course().gradePoint()).isNull();
        assertThat(result.rows().get(1).raw().get("gradePoint")).isEqualTo("0");
        assertThat(result.rows().get(1).warnings()).anyMatch(w->w.code().equals("PASS_ZERO_TO_NULL"));
        assertThat(result.rows().get(0).raw()).doesNotContainKey("개설학과코드");
    }
    @Test void numericCourseNumberUsesDisplayedZeroPaddingAndWarns() throws Exception {
        byte[] source;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook("교과목",false,false)));var out=new ByteArrayOutputStream()) {
            var cell=book.getSheetAt(0).getRow(4).getCell(3);cell.setCellValue(1234);
            var style=book.createCellStyle();style.setDataFormat(book.createDataFormat().getFormat("000000"));cell.setCellStyle(style);book.write(out);source=out.toByteArray();
        }
        var row=parser.parse(source).rows().get(0);
        assertThat(row.course().courseNumber()).isEqualTo("001234");
        assertThat(row.warnings()).anyMatch(w->w.code().equals("NUMERIC_COURSE_NUMBER"));
    }
    @Test void formulasAndInvalidGradesBecomeEditableErrorsWithoutEvaluation() throws Exception {
        assertThat(parser.parse(workbook("교과목",false,true)).rows().get(0).errors()).anyMatch(i->i.code().equals("UNSUPPORTED_CELL"));
        assertThat(parser.parse(workbook("교과목",true,false)).rows().get(0).errors()).isNotEmpty();
    }
    @Test void timetableAndMalformedFilesAreNotAcceptedAsTranscripts() throws Exception {
        byte[] source;
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {book.createSheet().createRow(0).createCell(0).setCellValue("개설학과코드");book.write(out);source=out.toByteArray();}
        final byte[] timetable=source;
        assertThatThrownBy(()->parser.parse(timetable)).isInstanceOf(ApiErrors.Failure.class);
        assertThatThrownBy(()->parser.parse(new byte[]{1,2,3})).isInstanceOf(ApiErrors.Failure.class);
        assertThatThrownBy(()->parser.parse(new byte[TranscriptWorkbookParser.MAX_FILE_BYTES+1])).isInstanceOf(ApiErrors.Failure.class);
    }
    @Test void compressedExpansionAndDuplicateColumnsAreRejected() throws Exception {
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)) {zip.putNextEntry(new ZipEntry("xl/workbook.xml"));zip.write(new byte[8*1024*1024+1]);zip.closeEntry();}
        byte[] bomb=out.toByteArray();assertThatThrownBy(()->parser.parse(bomb)).isInstanceOf(ApiErrors.Failure.class);
        byte[] duplicate=rewrite(workbook("교과목",false,false),"xl/sharedStrings.xml",s->s.replace("개설학과코드","학수번호"));
        assertThatThrownBy(()->parser.parse(duplicate)).isInstanceOf(ApiErrors.Failure.class);
    }
    @Test void officialFaGradeIsPreservedWithZeroPoint() throws Exception {
        byte[] source;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(workbook("교과목",false,false)));var out=new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(4).getCell(10).setCellValue("FA");
            book.getSheetAt(0).getRow(4).getCell(11).setCellValue("0");
            book.write(out);source=out.toByteArray();
        }
        var row=parser.parse(source).rows().get(0);
        assertThat(row.errors()).isEmpty();assertThat(row.course().grade()).isEqualTo("FA");
    }}

