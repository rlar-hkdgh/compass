package com.example.compass.student.config;

import com.example.compass.student.entity.OnboardingStep;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.*;

/** Documentation only: keep controller paths, payload keys and security identifiers unchanged. */
final class OpenApiReview {
 static void apply(String path,PathItem.HttpMethod method,Operation op){
  boolean get=method==PathItem.HttpMethod.GET,write=!get;
  boolean item=path.endsWith("/{id}"),me=path.startsWith("/api/v1/me");
  String resource=resource(path);
  if(resource!=null)op.setSummary(resource+(get?(item?" 상세 조회":" 목록 조회"):method==PathItem.HttpMethod.POST?" 등록":method==PathItem.HttpMethod.DELETE?" 삭제":" 수정"));
  if(path.equals("/api/v1/me/majors"))op.setSummary(get?"내 전공 조회":"내 전공 전체 교체");
  if(path.equals("/api/v1/me/preferences"))op.setSummary(get?"학생 선호 조회":"학생 선호 전체 교체");
  if(path.equals("/api/v1/me/certifications"))op.setSummary(get?"보유 자격증 조회":"보유 자격증 전체 교체");
  if(path.equals("/api/v1/student-codes/{type}"))op.setSummary("학생 입력용 코드 목록 조회");
  if(path.equals("/api/v1/student-codes/regions/grouped"))op.setSummary("희망 지역을 그룹별로 조회");
  if(path.equals("/api/v1/recruit-codes/{type}"))op.setSummary("채용 검색용 코드 목록 조회");
  if(path.endsWith("/readme"))op.setSummary("프로젝트 설명 문서 조회");
  if(path.endsWith("/readme/collect"))op.setSummary("프로젝트 설명 문서 수집");
  if(path.startsWith("/internal/"))op.setSummary(path.endsWith("/companies")?"내부 기업 등록":"내부 채용 공고 저장");
  String description=op.getDescription();
  if(resource!=null){
   description=get?"본인이 저장한 "+resource+(item?" 한 건을 조회합니다.":" 목록을 식별자 순으로 조회합니다. 기록이 없으면 빈 배열을 반환합니다."):
    method==PathItem.HttpMethod.POST?resource+"을 등록하고 생성된 항목을 반환합니다. 성공 상태는 201입니다.":
    method==PathItem.HttpMethod.DELETE?"본인 "+resource+" 한 건을 삭제합니다. 성공 상태는 204이며 응답 본문은 없습니다.":
    "본인 "+resource+" 한 건의 입력값 전체를 수정하고 수정된 항목을 반환합니다. 목록 전체를 교체하는 API가 아닙니다. 요청 스키마의 필수 필드는 모두 보내세요.";
   if(path.contains("/projects"))description+=" 기술 스택 코드는 GET /api/v1/student-codes/tech-stacks에서 조회합니다. 코드 중복은 허용하지 않으며 저장소 주소 변경 시 기존 수집 문서를 삭제합니다.";
   if(path.contains("/internships"))description+=" 직무 코드는 GET /api/v1/student-codes/job-categories에서 조회합니다. 시작일과 종료일은 오늘 이하여야 하며 시작일이 종료일보다 늦을 수 없습니다.";
   if(path.contains("/courses"))description+=" 평가 방식 GRADE는 A+, A0, B+, B0, C+, C0, D+, D0, F, FA를 사용합니다. 평점은 생략하거나 등급과 일치해야 합니다. P/NP는 등급 P 또는 NP, 평점 null을 사용합니다.";
   description+=" 로그인 세션과 본인 소유권을 확인합니다."+(write?" 변경 요청에는 CSRF 토큰이 필요합니다.":"");
  }
  if(path.equals("/api/v1/me/preferences")){
   description="""
    본인의 희망 직무·지역·고용 형태·최소 급여를 조회하거나 전체 교체합니다.
    먼저 아래 공개 API에서 code와 name을 조회하세요. 화면에는 name을 표시하고 요청에는 code 문자열을 전달합니다.
    - 희망 직무 jobCategoryCodes: GET /api/v1/student-codes/job-categories
    - 희망 지역 regionCodes: GET /api/v1/student-codes/regions
    - 고용 형태 employmentTypeCodes: GET /api/v1/student-codes/employment-types
    - 급여 단위 salaryUnitCode: GET /api/v1/student-codes/salary-units
    PUT은 부분 수정이 아닙니다. 세 배열을 모두 보내야 하며 각각 최대 100개, 중복·미등록 코드 불가입니다.
    빈 배열은 해당 선택을 해제합니다. minAmount는 0 이상이고 salaryUnitCode와 함께 지정하거나 둘 다 null로 해제하세요.
    예시의 코드는 기본 데이터 기준입니다. 실제 조회 결과에서 선택한 코드로 바꿔 사용하세요.
    성공하면 저장된 전체 선호를 200으로 반환합니다. 로그인 세션과 PUT 요청의 CSRF 토큰이 필요합니다.
    """;
   if(!get){
    var body=op.getRequestBody().getContent().get("application/json");
    body.addExamples("선호 선택",new Example().summary("희망 직무·지역·고용 형태 선택, 급여 조건 없음").value(Map.of("jobCategoryCodes",List.of("100"),"regionCodes",List.of("101000"),"employmentTypeCodes",List.of("1"))));
    Map<String,Object> selected=new LinkedHashMap<>();selected.put("jobCategoryCodes",List.of("100"));selected.put("regionCodes",List.of("101000"));selected.put("employmentTypeCodes",List.of("1"));selected.put("minAmount",null);selected.put("salaryUnitCode",null);
    body.getExamples().get("선호 선택").setValue(selected);
    var salary=new LinkedHashMap<String,Object>(selected);salary.put("minAmount",30000000);salary.put("salaryUnitCode","ANNUAL");
    body.addExamples("급여 조건 포함",new Example().summary("희망 최소 연봉 조건과 선호 선택").value(salary));
    var clear=new LinkedHashMap<String,Object>();clear.put("jobCategoryCodes",List.of());clear.put("regionCodes",List.of());clear.put("employmentTypeCodes",List.of());clear.put("minAmount",null);clear.put("salaryUnitCode",null);
    body.addExamples("선호 해제",new Example().summary("모든 선호 선택과 급여 조건 해제").value(clear));
   }
  }
  if(path.equals("/api/v1/me/majors"))description="본인 전공 목록을 조회하거나 전체 교체합니다. 전공은 GET /api/v1/student-codes/majors, 이수 구분은 GET /api/v1/student-codes/major-completion-types에서 조회하세요. PUT의 majors 배열은 필수이며 최대 100개입니다. 빈 배열은 전체 선택 해제, 동일 전공·이수 구분 조합 중복은 400입니다.";
  if(path.equals("/api/v1/me/certifications"))description="보유 자격증 목록을 조회하거나 전체 교체합니다. GET /api/v1/student-codes/certifications의 code를 사용하세요. PUT의 certificationCodes 배열은 필수이며 최대 100개, 중복 불가입니다. 빈 배열은 전체 선택 해제입니다.";
  if(path.equals("/api/v1/recruit-codes/{type}"))description="로그인 없이 채용 검색용 코드와 표시 이름을 조회합니다. type은 sources, job-categories, regions, employment-types, specialties, tech-stacks, education-levels, experience-types, salary-units, salary-decisions, deadline-types 중 하나입니다.";
  if(path.equals("/api/v1/student-codes/{type}"))description="로그인 없이 학생 입력용 code·name 목록을 조회합니다. type은 majors(전공), major-completion-types(이수 구분), certifications(자격증), tech-stacks(기술 스택), job-categories(직무), regions(지역), employment-types(고용 형태), salary-units(급여 단위)입니다. 표시에는 name, 저장에는 code를 사용하세요.";
  if(path.equals("/api/v1/student-codes/regions/grouped"))description="로그인 없이 지역을 시·도, 전국, 해외 그룹으로 조회합니다. 그룹 이름은 name이고 regions에는 시·도 전체와 세부 지역의 code·name이 포함됩니다. 취업 목표나 학생 선호에 저장할 때는 선택한 code를 regionCodes 배열로 보냅니다.";
  if(path.endsWith("/courses/import/preview"))description+=" 지원 헤더는 년도(연도·학년도), 학기, 학수번호(과목번호), 교과목명(과목명), 평가방식(평가방법), 등급(성적등급)이며 평점·학점 열은 선택입니다. 최대 5개 시트·1000개 과목을 처리합니다. 행별 오류가 있어도 200 미리보기를 반환할 수 있습니다. wholeImport에는 전체 저장 대상 수, 동일 행 건너뛰기 수, 오류·충돌 행이 포함됩니다. completedSemesters는 계절학기를 제외한 연도·정규학기 조합의 개수이며 계산할 수 없으면 null입니다.";
  if(path.endsWith("/courses/import/confirm-all")){
   description="같은 로그인 세션에서 15분 이내에 발급된 미리보기의 previewId만 보냅니다. 정상 과목을 저장하고 동일 기록은 건너뜁니다. 오류나 충돌이 있는 행은 rowIssues로 반환하며 기존 과목을 덮어쓰지 않습니다. 200이어도 rowIssues가 비어 있지 않을 수 있으므로 행별 오류를 표시하세요. inserted는 신규 저장 건수, skipped는 기존 또는 같은 파일에 있는 동일 과목으로 건너뛴 건수입니다. 둘의 합이 0이면 과목·평점·이수학기 저장 없이 처리된 결과이므로 성적표 입력을 완료로 표시하지 마세요. 오류 행을 수정해 다시 저장하려면 새 미리보기를 발급받으세요. 저장 또는 동일 기록이 있고 파일 평점을 계산할 수 있으면 4.5 만점 평점을 프로필에 자동 저장합니다. 같은 조건에서 계산된 정규 이수학기 수도 자동 저장하고 savedCompletedSemesters로 반환합니다. 미리보기가 없거나 만료됐거나 이미 사용된 경우 409입니다. 로그인 세션과 CSRF 토큰이 필요합니다.";
   op.getResponses().remove("404");
   op.getResponses().get("409").setDescription("PREVIEW_NOT_FOUND, PREVIEW_EXPIRED, PREVIEW_CONSUMED 또는 DATA_CONFLICT");
   responseExample(op,"200","부분 성공",Map.of(
       "inserted",2,"skipped",1,"savedGpa",3.85,"savedCompletedSemesters",2,
       "rowIssues",List.of(Map.of("sheetName","성적","sourceRow",7,"issues",List.of(
           Map.of("code","COURSE_IMPORT_CONFLICT","message","같은 연도·학기·학수번호의 기존 기록과 내용이 다릅니다."))))));
  }
  if(path.startsWith("/internal/"))description="내부 입력 기능이며 import-enabled=true일 때만 등록됩니다. 기존 보안 정책상 HTTP 접근은 차단되므로 Swagger에서 실행할 수 없습니다. 공고 저장은 출처와 공고 식별값으로 갱신합니다. 접수 시작은 종료보다 늦을 수 없고 급여는 0 이상, 최소 금액은 최대 금액 이하여야 합니다. 급여 금액을 지정하면 지급 단위와 DISCLOSED 결정 코드가 필요합니다. 자세한 필드 제한은 요청 스키마를 확인하세요.";
  if(path.equals("/api/v1/me") && (get || method==PathItem.HttpMethod.PATCH)){
   description=get?"본인 프로필과 학생 기본 정보를 조회합니다. schoolEmail이 로그인 식별자이며 별도 아이디는 반환하지 않습니다. 정규 이수학기 수·전체 평점/만점은 미입력 시 null입니다.":
    "학생 정보 화면의 이름·편입 여부·정규 이수학기 수(semester, 0 이상)·전체 평점(gpa)/만점(gpaScale)을 부분 수정합니다. 생략 또는 null 필드는 유지합니다. 평점은 0 이상, 만점은 0 초과 4.5 이하이고 평점은 만점 이하여야 합니다. 최초 평점/만점은 함께 지정하세요. 학과·복수전공·부전공은 별도 PUT /api/v1/me/majors에서 전공 코드와 이수 구분 코드로 저장합니다. 로그인 세션과 CSRF 토큰이 필요합니다.";
   if(!get){
    op.getRequestBody().getContent().get("application/json").addExamples("학생 정보",new Example().summary("학생 정보 화면 저장").value(Map.of("name","홍길동","isTransfer",false,"semester",6,"gpa",3.85,"gpaScale",4.5)));
   }
  }
  if(path.equals("/api/v1/me/career-goal")){
   op.setSummary(get?"취업 목표 조회":"취업 목표 전체 저장");
   description="목표 설정·희망 직무 변경 화면에서 사용하는 본인 취업 목표입니다. GET이 404 ITEM_NOT_FOUND를 반환하면 아직 취업 목표가 등록되지 않은 상태입니다. 첫 진입에서는 빈 입력 폼을 열고, 저장 시 PUT을 호출하세요. PUT은 최초 등록과 수정 모두 200입니다. companySizes로 기업 규모를 1~4개 선택합니다. jobCategoryCode는 GET /api/v1/student-codes/job-categories의 공고 공통 코드를 사용합니다. targetDate는 취업 목표 연월 YYYY-MM(1900-01~2100-12), targetSalary는 만원 단위 연봉입니다. regionCodes는 GET /api/v1/student-codes/regions/grouped의 regions 안에 있는 code를 사용합니다. 평면 목록은 GET /api/v1/student-codes/regions에서도 조회할 수 있습니다. 지역은 기존 preferences.regionCodes와 같은 저장소를 공유하므로 어느 API로 변경해도 다른 조회에 반영됩니다. 다른 선호 항목과 성적·이력은 변경하지 않습니다. 저장과 지역 교체는 한 트랜잭션으로 처리합니다. 목표 저장만으로 진단·분석·로드맵을 생성하거나 온보딩을 자동 완료하지 않습니다. 로그인 세션과 PUT 요청의 CSRF 토큰이 필요합니다.";
   if(get){
    op.getResponses().get("404").setDescription("ITEM_NOT_FOUND: 저장된 취업 목표가 없습니다. 첫 진입에서는 빈 입력 폼을 표시하세요.");
    responseExample(op,"404","목표 미등록",Map.of("code","ITEM_NOT_FOUND","message","항목을 찾을 수 없습니다."));
   } else {op.getRequestBody().getContent().get("application/json").addExamples("취업 목표",new Example().summary("2027년 6월 취업 목표").value(Map.of("jobCategoryCode","84","companySizes",List.of("LARGE","MID"),"targetSalary",5000,"regionCodes",List.of("101000"),"targetCompany","네이버","targetDate","2027-06")));}
  }
  if(path.equals("/api/v1/me/onboarding")){
   description+=" nextStep은 정의된 순서상 첫 NOT_STARTED 단계입니다. 모든 단계가 COMPLETED 또는 SKIPPED이면 finished=true이며 nextStep은 null입니다. 신규 가입 직후에는 진행 상태가 저장되지 않아도 8개 단계 모두 NOT_STARTED로 조회됩니다.";
   if(get){
    responseExample(op,"200","가입 직후",onboardingExample(false));
    responseExample(op,"200","온보딩 완료",onboardingExample(true));
   } else {
    description+=" 입력 데이터 저장 후 별도로 호출합니다. steps에는 바꿀 단계만 넣으세요. 같은 단계 중복은 400 DUPLICATE_INPUT입니다. NOT_STARTED는 진행 상태만 초기화합니다.";
    op.getRequestBody().getContent().get("application/json").addExamples("기본 정보와 전공 완료",
        new Example().value(Map.of("steps",List.of(
            Map.of("step","BASIC_INFO","status","COMPLETED"),
            Map.of("step","MAJORS","status","COMPLETED")))));
   }
  }
  if(path.endsWith("/readme") || path.endsWith("/readme/collect")){
   description+=" lastAttemptStatus는 SUCCESS(성공), NOT_FOUND(README를 찾지 못함), RATE_LIMITED(GitHub 요청 제한), FAILED(그 외 실패)입니다. 마지막 시도가 실패해도 이전 성공의 content와 lastSuccessAt이 남을 수 있습니다.";
   if(!get)responseExample(op,"200","이전 README 보존",Map.of(
       "projectId",12,"repositoryUrl","https://github.com/example/sample","readmePath","README.md",
       "readmeSha","0123456789abcdef0123456789abcdef01234567","content","# 이전에 수집한 README",
       "lastSuccessAt","2026-11-07T03:00:00Z","lastAttemptAt","2026-11-08T03:00:00Z",
       "lastAttemptStatus","RATE_LIMITED","lastErrorCode","GITHUB_RATE_LIMITED"));
  }
  if(path.equals("/api/v1/auth/signup") || path.equals("/api/v1/auth/login")){
   var example=new LinkedHashMap<String,Object>();example.put("schoolEmail","student@sju.ac.kr");example.put("password","Example-password-123!");
   if(path.endsWith("/signup")){example.put("name","홍길동");example.put("isTransfer",false);}
   op.getRequestBody().getContent().get("application/json").addExamples("학교 이메일 인증",new Example().summary("학교 이메일로 가입 또는 로그인").value(example));
  }
  op.setDescription(description);
  // Remove generic errors which cannot arise from these operations.
  if(get && !me && !path.startsWith("/internal/"))op.getResponses().remove("403");
  if(get || path.equals("/api/v1/auth/login") || path.endsWith("/logout") || path.endsWith("/availability"))op.getResponses().remove("409");
  if(!item && !path.contains("/readme") && !path.contains("/onboarding") && !path.endsWith("/career-goal") && !path.equals("/api/v1/me") && !path.endsWith("/password") && !path.contains("/email-verification") && !path.contains("/import/") && !path.contains("-codes/"))op.getResponses().remove("404");
  if(path.equals("/api/v1/student-codes/regions/grouped"))op.getResponses().remove("400");
  if(path.endsWith("-codes/{type}"))op.getResponses().get("404").setDescription("CODE_TYPE_NOT_FOUND: 지정한 코드 종류를 지원하지 않습니다.");
  if(op.getResponses().containsKey("400") && !path.equals("/api/v1/auth/signup")){
   String error="INVALID_REQUEST: 요청 형식·필수값·검증 조건을 확인하세요.";
   if(path.endsWith("/confirm") && (path.contains("email-verification")||path.contains("/signup/email/")))error+=" EMAIL_CODE_INVALID: 번호 또는 세션 불일치, EMAIL_CODE_EXPIRED: 번호 유효기간 만료.";
   if(write && ((resource!=null && (path.contains("/projects")||path.contains("/internships"))) || path.endsWith("/career-goal") || path.equals("/api/v1/me/preferences") || path.equals("/api/v1/me/majors") || path.equals("/api/v1/me/certifications")))error+=" UNKNOWN_CODE: 미등록 코드, DUPLICATE_INPUT: 중복 입력.";
   if(path.equals("/api/v1/me/preferences") && write)error+=" 세 배열은 필수이며 최대 100개입니다. 급여는 0 이상이며 금액과 단위를 함께 지정하세요.";
   if(path.equals("/api/v1/me/onboarding") && write)error+=" DUPLICATE_INPUT: 같은 단계가 요청에 중복되었습니다.";
   if(path.endsWith("/courses/import/preview"))error+=" INVALID_TRANSCRIPT: XLSX 내용을 읽을 수 없거나 지원 형식·제한에 맞지 않습니다.";
   if(path.endsWith("/courses/import/confirm"))error+=" DUPLICATE_INPUT: 같은 연도·학기·학수번호의 과목을 중복 선택했습니다.";
   op.getResponses().get("400").setDescription(error);
  }
  op.getResponses().forEach((status,response)->{
   if(status.startsWith("2"))response.setDescription(status.equals("201")?"등록 완료":status.equals("204")?"처리 완료, 응답 본문 없음":"조회 또는 처리 성공");
   else if(response.getDescription()!=null && !response.getDescription().matches("(?s).*[가-힣].*"))
    response.setDescription(response.getDescription()+" — "+switch(status){
     case "400"->"요청 내용 또는 인증번호를 확인하세요.";
     case "401"->"로그인 상태 또는 비밀번호를 확인하세요.";
     case "403"->"요청 권한 또는 보안 토큰을 확인하세요.";
     case "404"->"요청한 항목이 없습니다.";
     case "409"->"중복값 또는 현재 처리 상태와 충돌합니다.";
     case "429"->"요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도하세요.";
     case "503"->"메일 발송을 완료하지 못했습니다. 발송 설정과 연결 상태를 확인하세요.";
     default->"요청 처리 중 오류가 발생했습니다.";
    });
  });
  if(path.endsWith("/courses/import/confirm"))op.setDescription(op.getDescription()+" saveGpa=true를 보내면 미리보기의 파일 기준 평점을 과목과 함께 프로필에 저장합니다. 저장한 평점은 savedGpa로 반환합니다.");
  if(op.getRequestBody()!=null)op.getRequestBody().setDescription(path.endsWith("/import/preview")?"file 항목에 업로드할 성적표 엑셀 파일을 첨부하세요.":"아래 스키마의 필수 필드와 검증 조건을 확인하세요.");
  if(op.getParameters()!=null)for(Parameter p:op.getParameters()){
   p.setDescription(switch(p.getName()){
    case "id"->"본인이 저장한 항목의 식별자";
    case "type"->"조회할 코드 종류. 지원값은 위 API 설명을 확인하세요.";
    case "source"->"공고 출처 코드. 채용 코드 조회의 sources를 사용하세요.";
    case "sourcePostingId"->"출처 서비스가 부여한 공고 식별값";
    default->"요청에 사용할 "+p.getName()+" 값";
   });
  }
 }
 private static void responseExample(Operation op,String status,String name,Map<String,Object> value){
  var content=op.getResponses().get(status).getContent();
  var media=content.values().iterator().next();
  media.addExamples(name,new Example().summary(name).value(value));
 }
 private static Map<String,Object> onboardingExample(boolean finished){
  var steps=Arrays.stream(OnboardingStep.values())
      .map(step->Map.of("step",step.name(),"status",finished
          ? (step.ordinal()<3?"COMPLETED":"SKIPPED") : "NOT_STARTED")).toList();
  Map<String,Object> example=new LinkedHashMap<>();
  example.put("steps",steps);
  example.put("nextStep",finished?null:"BASIC_INFO");
  example.put("finished",finished);
  example.put("totalSteps",steps.size());
  example.put("completedSteps",finished?3:0);
  example.put("skippedSteps",finished?steps.size()-3:0);
  return example;
 }
 private static String resource(String path){
  if(!path.matches("/api/v1/me/(projects|internships|awards|courses)(/\\{id\\})?"))return null;
  if(path.contains("/projects"))return "프로젝트";
  if(path.contains("/internships"))return "인턴 이력";
  if(path.contains("/awards"))return "수상 이력";
  return "수강 과목";
 }
}
