package com.example.compass.student.config;


import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.util.*;

@Configuration
@ConditionalOnProperty(name="compass.docs.enabled",havingValue="true")
public class OpenApiConfiguration {
    @Bean OpenAPI compassOpenApi() {
        return new OpenAPI().info(new Info().title("Compass API").version("v1").description("""
            ### 인증과 Swagger 사용
            Compass는 Spring Security 세션으로 로그인 상태를 확인합니다. Compass 서버가 제공하는 Swagger UI에서 로그인하면 브라우저가 같은 출처의 `JSESSIONID` 쿠키를 자동으로 전송합니다. Authorize에 쿠키 값을 입력할 필요가 없습니다.

            Compass Swagger UI의 인터셉터는 변경 요청마다 `GET /api/v1/auth/csrf`를 호출해 토큰을 받고 `X-CSRF-TOKEN` 헤더에 넣습니다. OpenAPI JSON을 외부 Swagger Editor에서 열면 이 인터셉터는 함께 로드되지 않습니다. 프론트엔드에서 직접 연동할 때는 같은 세션의 쿠키를 보내고, 변경 요청 전에 CSRF 응답의 `headerName`에 `token`을 설정하세요.

            ### Render 개발 서버에서 테스트하기
            현재 Render 개발 서버는 `MAIL_ENABLED=false`로 실행합니다. 가입 이메일 중복 확인은 가능하지만, 가입·기존 계정의 인증번호 발송은 `503 EMAIL_DELIVERY_FAILED`를 반환합니다.

            1. 신규 테스트 계정은 같은 `sejong-compass` DB에 연결된 로컬 Compass에서 SMTP를 켜고 학교 이메일 인증과 가입을 완료합니다.
            2. Render의 Compass Swagger UI를 열고 그 계정의 `schoolEmail`과 비밀번호로 `POST /api/v1/auth/login`을 실행합니다. `GET /api/v1/me`에서 프로필과 `emailVerified: true`를 확인합니다.
            3. 같은 브라우저에서 아래 온보딩 흐름을 테스트합니다. 로그인 이후 학생 정보 입력과 저장에는 SMTP가 필요 없습니다.

            ### 학교 이메일 인증과 로그인 (SMTP를 켠 환경)
            신규 가입은 `POST /api/v1/auth/signup/email/availability` → `POST /api/v1/auth/signup/email/send` → `POST /api/v1/auth/signup/email/confirm` → `POST /api/v1/auth/signup` 순서로 진행합니다. 같은 브라우저 세션에서 이메일 인증을 마치고 10분 안에 가입하세요. 가입 증명은 한 번만 사용됩니다.

            가입 후 `POST /api/v1/auth/login`으로 로그인하고 `GET /api/v1/me`로 프로필을 확인합니다. 가입과 로그인에는 `schoolEmail`을 사용합니다. 로그인하면 세션 ID와 CSRF 토큰이 갱신됩니다.

            기존 미인증 계정은 로그인한 뒤 `POST /api/v1/me/email-verification`으로 번호를 발송하고 `POST /api/v1/me/email-verification/confirm`으로 인증을 완료합니다.

            ### 학생 온보딩
            1. `PATCH /api/v1/me`로 학생 기본 정보를, `PUT /api/v1/me/majors`로 전공 목록을 저장합니다.
            2. 성적표를 첨부한 경우 `POST /api/v1/me/courses/import/preview`에 XLSX 파일을 업로드합니다. 미리보기에서 과목과 평점, 정규 이수학기를 확인한 뒤 같은 세션에서 `POST /api/v1/me/courses/import/confirm-all`에 `previewId`를 보내 전체 저장합니다.
            3. 기본 정보, 전공, 첨부한 성적표의 저장이 모두 성공하면 `PATCH /api/v1/me/onboarding`으로 해당 단계 상태를 갱신합니다.
            4. `PUT /api/v1/me/career-goal`로 취업 목표를 저장한 뒤 프로젝트·인턴·수상·자격증을 입력하거나 건너뜁니다.

            ### 학생 기본 정보 다시 테스트하기
            `STUDENT_BASIC_RESET_ENABLED=true`인 개발 환경에서는 로그인 후 `DELETE /api/v1/me/basic-info`를 요청 본문 없이 호출할 수 있습니다. 성공 응답은 `204 No Content`입니다. Swagger UI가 변경 요청에 CSRF 토큰을 자동으로 첨부합니다.

            초기화하면 전공·수강 과목·평점·정규 이수학기와 `BASIC_INFO`, `MAJORS`, `COURSES` 상태가 지워집니다. 현재 브라우저의 성적표 미리보기도 폐기됩니다. 계정, 학교 이메일 인증, 로그인 세션, 이름·편입 여부, 취업 목표와 선택 이력은 보존됩니다.

            초기화 후 `GET /api/v1/me`, `GET /api/v1/me/majors`, `GET /api/v1/me/onboarding`에서 초기화 결과를 확인합니다. 같은 계정으로 기본 정보와 전공을 저장하고, 성적표가 있으면 새 미리보기부터 다시 실행합니다.

            비밀번호를 변경한 뒤에는 다시 로그인해야 합니다. 회원탈퇴는 계정과 연결된 개인 데이터를 영구 삭제합니다.

            문서는 `API_DOCS_ENABLED=true`인 신뢰할 수 있는 개발 환경에서 사용하세요.
            """)).components(new Components()
                .addSecuritySchemes("sessionCookie",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID").description("로그인 응답으로 브라우저에 설정되는 HttpOnly 세션 쿠키"))
                .addSecuritySchemes("csrfToken",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN").description("GET /api/v1/auth/csrf 응답의 token. Compass 서버가 제공하는 Swagger UI에서 자동으로 갱신합니다."))
                .addSchemas("ApiError",new ObjectSchema().addProperty("code",new StringSchema()).addProperty("message",new StringSchema()).required(List.of("code","message"))));
    }

    @Bean OpenApiCustomizer compassContract() {
        return api->{
            // springdoc may prune an initially unused schema before this customizer adds its references.
            // Register the schema in the same final stage as the error responses that reference it.
            if(api.getComponents()==null)api.setComponents(new Components());
            api.getComponents().addSchemas("ApiError",new ObjectSchema()
                .description("공통 오류 응답")
                .addProperty("code",new StringSchema().description("오류 식별 코드"))
                .addProperty("message",new StringSchema().description("오류 안내 메시지"))
                .required(List.of("code","message")));
            var logout=new Operation().operationId("logout").summary("로그아웃").description("현재 세션을 무효화하고 JSESSIONID 쿠키를 삭제합니다. CSRF 토큰이 필요합니다.")
                .tags(List.of("인증")).responses(new ApiResponses().addApiResponse("204",new ApiResponse().description("로그아웃 완료")));
            api.getPaths().addPathItem("/api/v1/auth/logout",new PathItem().post(logout));
            api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,op)->{
                boolean write=method!=PathItem.HttpMethod.GET && method!=PathItem.HttpMethod.HEAD && method!=PathItem.HttpMethod.OPTIONS;
                boolean owned=path.startsWith("/api/v1/me");
                var security=new SecurityRequirement();
                if(owned)security.addList("sessionCookie");
                if(write)security.addList("csrfToken");
                op.setSecurity(security.isEmpty()?List.of():List.of(security));
                op.setTags(List.of(path.startsWith("/internal/")?"내부 입력 (HTTP 차단)":path.contains("/auth/")?"인증":path.contains("email-verification")?"학교 이메일 인증":path.contains("/onboarding")?"온보딩":path.contains("/courses/import")?"성적표 XLSX":path.contains("/readme")?"GitHub README":owned?"학생 정보·이력":"공개 채용·코드"));
                if(op.getSummary()==null) op.setSummary(summary(path,method));
                String description=description(path,method);
                if(path.startsWith("/api/v1/auth/signup/email/")){
                    op.setSummary(path.endsWith("/availability")?"가입 전 학교 이메일 중복 확인":path.endsWith("/send")?"가입 전 인증번호 발송":"가입 전 인증번호 확인");
                    description="로그인 없이 같은 브라우저 세션에서 실행합니다. schoolEmail은 소문자 sju.ac.kr 또는 sejong.co.kr 주소. availability는 available=true/false를 반환하며 주소를 예약하지 않습니다. send/confirm은 이미 가입된 주소에 409 EMAIL_EXISTS. 발송 번호 6자리·10분 유효, 재발송 60초, 이메일/세션당 24시간 10회(발송 실패 포함), 번호당 오입력 5회 제한. 재발송은 이전 번호와 인증 완료를 무효화합니다. confirm 성공 후 10분 안에 같은 세션과 동일 이메일로 signup을 호출하세요. 가입 시 재검증하여 한 번만 소비하며 실패한 가입은 증명을 소비하지 않습니다.";
                    error(op,"409","EMAIL_EXISTS / DATA_CONFLICT");
                    if(!path.endsWith("/availability"))error(op,"429","EMAIL_RESEND_COOLDOWN / EMAIL_SEND_LIMIT / EMAIL_ATTEMPTS_EXCEEDED");
                    if(path.endsWith("/send"))error(op,"503","EMAIL_DELIVERY_FAILED: SMTP 비활성화, 미설정 또는 발송 실패");
                    if(path.endsWith("/confirm"))error(op,"400","EMAIL_CODE_INVALID / EMAIL_CODE_EXPIRED / INVALID_REQUEST");
                }
                if(path.equals("/api/v1/auth/signup"))error(op,"400","EMAIL_VERIFICATION_REQUIRED: 같은 세션에서 동일 학교 이메일 인증 후 10분 내 가입 필요 / INVALID_REQUEST");
                if(owned)description+=" 본인 세션의 데이터만 처리하며 다른 학생의 항목은 404입니다.";
                if(write)description+=" CSRF 누락·만료는 403 CSRF_INVALID입니다.";
                if(path.startsWith("/internal/"))description+=" import-enabled=true에서만 등록됩니다. 기존 보안 정책상 HTTP 접근은 항상 차단되며 Swagger에서도 실행할 수 없습니다.";
                op.setDescription((op.getDescription()==null?"":op.getDescription()+"\n")+description);
                if(op.getResponses()==null)op.setResponses(new ApiResponses());
                if(owned)error(op,"401","UNAUTHENTICATED / SESSION_EXPIRED: 로그인 필요 또는 만료된 세션");
                error(op,"403","CSRF_INVALID: CSRF 누락·불일치 / FORBIDDEN: 허용되지 않은 경로");
                error(op,"400",path.equals("/api/v1/auth/signup")?"EMAIL_VERIFICATION_REQUIRED: 같은 세션에서 동일 학교 이메일 인증 후 10분 내 가입 필요 / INVALID_REQUEST":path.startsWith("/api/v1/auth/signup/email/")?"INVALID_REQUEST / EMAIL_CODE_INVALID / EMAIL_CODE_EXPIRED":"INVALID_REQUEST: 요청 형식·필수값·범위·교차 필드 검증 실패; UNKNOWN_CODE / DUPLICATE_INPUT");
                if(owned || path.contains("{"))error(op,"404","ITEM_NOT_FOUND / CODE_TYPE_NOT_FOUND / NOT_FOUND: 없거나 본인 소유가 아닌 항목");
                if(write)error(op,"409",path.startsWith("/api/v1/auth/signup/email/")?"EMAIL_EXISTS / DATA_CONFLICT":"DATA_CONFLICT: 중복값 또는 참조 관계 충돌");
                if(path.equals("/api/v1/auth/signup"))error(op,"409","EMAIL_EXISTS / DATA_CONFLICT: 중복 학교 이메일");
                if(path.equals("/api/v1/auth/login") || path.equals("/api/v1/me/password") || (path.equals("/api/v1/me") && method==PathItem.HttpMethod.DELETE))
                    error(op,"401","INVALID_CREDENTIALS: 비밀번호 불일치 / UNAUTHENTICATED / SESSION_EXPIRED");
                if(path.contains("email-verification") && write) {
                    error(op,"409","EMAIL_ALREADY_VERIFIED / DATA_CONFLICT");
                    error(op,"429","EMAIL_RESEND_COOLDOWN (60초), EMAIL_SEND_LIMIT (24시간 10회), EMAIL_ATTEMPTS_EXCEEDED (번호당 5회)");
                    if(path.endsWith("/confirm"))error(op,"400","INVALID_REQUEST: 숫자 6자리 필요 / EMAIL_CODE_INVALID / EMAIL_CODE_EXPIRED");
                    else error(op,"503","EMAIL_DELIVERY_FAILED: SMTP 미설정 또는 발송 실패. 비밀값과 번호는 응답에 포함하지 않습니다.");
                }
                if(path.endsWith("/import/preview")) error(op,"413","UPLOAD_TOO_LARGE: 비어 있거나 5MiB 초과 파일 / 요청 크기 초과");
                if(path.endsWith("/import/confirm"))error(op,"409","PREVIEW_NOT_FOUND / PREVIEW_EXPIRED / PREVIEW_CONSUMED / COURSE_IMPORT_CONFLICT / DATA_CONFLICT");
                if(path.endsWith("/readme"))error(op,"404","README_NOT_COLLECTED / ITEM_NOT_FOUND");
                if(path.endsWith("/readme/collect")) {
                    error(op,"409","README_COLLECTION_IN_PROGRESS / PROJECT_CHANGED / DATA_CONFLICT");
                    error(op,"429","README_COLLECTOR_BUSY / README_COLLECTION_COOLDOWN (30초)");
                }
                // A CSRF endpoint has a fixed contract, not an unconstrained string map.
                if(path.equals("/api/v1/auth/csrf"))op.getResponses().get("200").content(new Content().addMediaType("application/json",new MediaType().schema(
                    new ObjectSchema().addProperty("token",new StringSchema()).addProperty("headerName",new StringSchema().example("X-CSRF-TOKEN")).required(List.of("token","headerName")))));
                // ResponseStatus methods really return no body.
                if(op.getResponses().containsKey("204"))op.getResponses().get("204").setContent(null);
                OpenApiReview.apply(path,method,op);
            }));
            describe(api,"Signup","schoolEmail","소문자 학교 이메일만 허용: sju.ac.kr 또는 sejong.co.kr. 같은 세션에서 이 주소의 인증을 먼저 완료해야 합니다. 신규 가입 성공 시 emailVerified=true입니다.");
            describe(api,"Signup","password","10~64자, UTF-8 72바이트 이하.");
            describe(api,"PasswordChange","newPassword","10~64자, UTF-8 72바이트 이하. 성공하면 전체 기존 세션을 만료합니다.");
            describe(api,"Profile","emailVerified","등록된 현재 학교 이메일의 인증 완료 여부. 가입 전 인증을 마친 신규 계정은 true이며, 기존 계정은 실제 인증 상태를 반환합니다.");
            describe(api,"ProfilePatch","name","생략 또는 null이면 유지. 제공하면 공백 불가, 최대 100자.");
            describe(api,"Login","schoolEmail","로그인 식별자. 소문자 sju.ac.kr 또는 sejong.co.kr 전체 이메일 주소, 최대 254자. 가입 당시 인증한 이메일을 사용하세요.");
            describe(api,"ProfilePatch","semester","정규 이수학기 수, 0 이상의 정수. 성적표 전체 저장에서 자동 계산됩니다. 생략/null이면 유지.");
            describe(api,"ProfilePatch","gpa","전체 평점. 0 이상, 소수점 최대 2자리, gpaScale 이하. 성적표 과목 데이터와 별도로 관리합니다. 최초에는 gpaScale과 함께 지정하세요. 생략/null이면 유지.");
            describe(api,"ProfilePatch","gpaScale","평점 만점. 0 초과 4.5 이하, 소수점 최대 2자리. 전체학점은 이수학점 합계가 아니라 화면의 평점 만점입니다. 생략/null이면 유지.");
            describe(api,"CareerGoalInput","jobCategoryCode","공고와 같은 희망 직무 코드. GET /api/v1/student-codes/job-categories의 code를 사용합니다.");
            describe(api,"CareerGoal","jobCategoryCode","공고와 공통으로 사용하는 희망 직무 코드입니다.");
            describe(api,"CareerGoalInput","companySizes","목표 기업 규모 1~4개. LARGE(대기업), MID(중견기업), STARTUP(스타트업), SMALL(중소기업). 중복 불가.");
            describe(api,"CareerGoalInput","targetSalary","목표 연봉, 만원 단위의 0 이상 정수. 예: 5000은 연봉 5천만원. 선호 API의 원 단위 최소 급여와 별도 항목입니다.");
            describe(api,"CareerGoalInput","regionCodes","희망 지역 코드 1~100개, 중복·미등록 불가. GET /api/v1/student-codes/regions에서 조회합니다. 기존 preferences.regionCodes와 같은 선택을 공유합니다.");
            describe(api,"CareerGoalInput","targetCompany","목표 기업명. 필수, 공백 불가, 최대 255자. 채용 공고의 회사 등록 여부와 무관한 자유 입력입니다.");
            describe(api,"CareerGoalInput","targetDate","취업 목표 연월. YYYY-MM 형식, 1900-01~2100-12, 월 01~12. 예: 2027-06. 반기(2027-2H)·날짜(2027-06-01)는 허용하지 않습니다. 지난 연월도 보존·수정할 수 있습니다.");
            describe(api,"Preferences","minAmount","희망 최소 급여, 0 이상. salaryUnitCode와 함께 지정하거나 둘 다 null로 해제.");
            describe(api,"Preferences","jobCategoryCodes","희망 직무 코드 배열. GET /api/v1/student-codes/job-categories에서 조회. 필수, 최대 100개, 중복 불가. 빈 배열은 선택 해제.");
            describe(api,"Preferences","regionCodes","희망 지역 코드 배열. GET /api/v1/student-codes/regions에서 조회. 필수, 최대 100개, 중복 불가. 빈 배열은 선택 해제.");
            describe(api,"Preferences","employmentTypeCodes","희망 고용 형태 코드 배열. GET /api/v1/student-codes/employment-types에서 조회. 필수, 최대 100개, 중복 불가. 빈 배열은 선택 해제.");
            describe(api,"Preferences","salaryUnitCode","희망 급여 단위 코드. GET /api/v1/student-codes/salary-units에서 조회. minAmount와 함께 지정하거나 둘 다 null로 해제.");
            describe(api,"ProjectInput","githubUrl","선택값. 공개 GitHub 저장소 HTTPS URL https://github.com/owner/repo만 허용. 공백은 해제.");
            describe(api,"InternshipInput","startedOn","오늘 이전 또는 오늘. endedOn보다 늦을 수 없습니다.");
            describe(api,"CourseInput","grade","GRADE: A+, A0, B+, B0, C+, C0, D+, D0, F, FA. P/NP: P 또는 NP.");
            describe(api,"CourseInput","gradePoint","GRADE는 생략하거나 등급에 대응하는 4.5/4.0/3.5/3.0/2.5/2.0/1.5/1.0/0.0 평점. P/NP는 null.");
            describe(api,"ConfirmTranscript","rows","1~1000행. 같은 미리보기에서 선택한 행만 가능. sheetIndex/sourceRow 및 년도·학기·학수번호 중복 불가.");
            describe(api,"Patch","steps","1~8개 단계. 동일 단계 중복 불가. 생략한 단계 유지; NOT_STARTED는 진행 상태만 초기화하며 입력 데이터는 보존.");
            if(api.getComponents().getSchemas()!=null)api.getComponents().getSchemas().values().forEach(schema->{
                if(schema.getProperties()!=null)for(String field:List.of("password","currentPassword","newPassword","code")) {
                    Object property=schema.getProperties().get(field);
                    if(property instanceof Schema<?> value && (field.contains("assword") || schema.getProperties().size()==1))
                        value.setWriteOnly(true);
                }
            });
        };
    }
    private static void describe(OpenAPI api,String schema,String property,String description) {
        var s=api.getComponents().getSchemas().get(schema);
        if(s!=null && s.getProperties()!=null && s.getProperties().get(property) instanceof Schema<?> p)p.setDescription(description);
    }
    private static void error(Operation op,String status,String description) {
        op.getResponses().addApiResponse(status,new ApiResponse().description(description)
            .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
    }
    private static String summary(String path,PathItem.HttpMethod method) {
        String name=path.substring(path.lastIndexOf('/')+1);
        return switch(path) {
            case "/api/v1/auth/csrf" -> "세션 CSRF 토큰 발급";
            case "/api/v1/auth/signup" -> "회원가입";
            case "/api/v1/auth/login" -> "세션 로그인";
            case "/api/v1/me" -> method==PathItem.HttpMethod.DELETE?"회원탈퇴":method==PathItem.HttpMethod.GET?"내 프로필 조회":"내 프로필 수정";
            case "/api/v1/me/email-verification" -> method==PathItem.HttpMethod.GET?"학교 이메일 인증 상태 조회":"학교 이메일 인증번호 발송";
            case "/api/v1/me/email-verification/confirm" -> "학교 이메일 인증번호 확인";
            case "/api/v1/me/password" -> "비밀번호 변경";
            case "/api/v1/me/basic-info" -> "학생 기본 정보 테스트 초기화";
            case "/api/v1/me/onboarding" -> method==PathItem.HttpMethod.GET?"온보딩 진행 조회":"온보딩 진행 수정";
            case "/api/v1/me/courses/import/preview" -> "XLSX 성적표 미리보기";
            case "/api/v1/me/courses/import/confirm" -> "선택한 성적표 행 저장";
            case "/api/v1/me/courses/import/confirm-all" -> "성적표 정상 과목 전체 저장";
            default -> method+" "+path.replace("/api/v1/","");
        };
    }
    private static String description(String path,PathItem.HttpMethod method) {
        if(path.equals("/api/v1/me/basic-info"))return "STUDENT_BASIC_RESET_ENABLED=true인 개발 환경에서 본인의 전공, 수강 과목, 평점과 이수학기를 삭제하고 BASIC_INFO·MAJORS·COURSES 진행 상태를 초기화합니다. 현재 세션의 성적표 미리보기도 폐기합니다. 계정, 이름, 편입 여부, 이메일 인증, 취업 목표와 선택 이력은 보존됩니다. 요청 본문 없이 호출하며 성공 시 204입니다.";
        if(path.equals("/api/v1/me") && method==PathItem.HttpMethod.DELETE)return "currentPassword 재확인 후 계정·학생 입력·프로젝트 README·온보딩·이메일 인증·개인 분석/추천을 영구 삭제합니다. 공용 공고/공고 분석·활동·코드 사전은 보존합니다. 전체 세션과 성적표 미리보기를 폐기합니다. 성공 204, 삭제 중 DB 오류는 전체 롤백.";
        if(path.contains("email-verification"))return "가입·로그인 후 사용합니다. 수신 주소는 서버에 저장된 본인 학교 이메일로 고정됩니다. 인증번호는 숫자 6자리, 10분 유효, 재발송 간격 60초, 24시간 최대 10회(실패 포함), 번호당 오입력 5회 제한. 재발송 시 이전 번호 무효화. 성공한 번호는 재사용 불가.";
        if(path.endsWith("/import/preview"))return "multipart/form-data의 필수 file 파트에 비어 있지 않은 .xlsx 파일(최대 5MiB)을 첨부합니다. 원본 파일은 저장하지 않으며 세션당 미리보기 1개를 15분 보관합니다. 응답의 errors/warnings와 existingCourseIds를 확인하세요. 충분한 학점·등급 데이터가 있는 경우 파일 기준 4.5 만점 가중평점 gpa를 반환합니다.";
        if(path.endsWith("/import/confirm"))return "같은 로그인 세션의 previewId와 선택·수정한 rows를 전송합니다. 전체 요청을 원자적으로 저장합니다. 완전히 같은 기존 과목은 skipped, 내용이 다른 기존 과목은 409. 성공한 미리보기는 1회만 사용 가능합니다.";
        if(path.contains("/readme"))return "본인 프로젝트의 공개 GitHub README만 처리합니다. 수집 실패도 200 응답의 lastAttemptStatus/lastErrorCode로 표시될 수 있습니다. 기존 성공 내용은 실패 시 보존합니다. HTML은 신뢰하지 말고 화면에 안전하게 렌더링하세요.";
        if(path.contains("/onboarding"))return "8개 단계 BASIC_INFO, MAJORS, PREFERENCES, COURSES, PROJECTS, INTERNSHIPS, AWARDS, CERTIFICATIONS. 상태 NOT_STARTED, COMPLETED, SKIPPED. 순서 강제나 기존 입력으로부터 자동 완료 추론은 하지 않습니다.";
        if(path.equals("/api/v1/auth/signup"))return "별도 아이디 없이 schoolEmail을 로그인 식별자로 사용합니다. 비밀번호 10~64자 및 UTF-8 72바이트 이하, 이름 필수 최대 100자, isTransfer 필수. 학교 이메일은 소문자 sju.ac.kr/sejong.co.kr. 가입 전 schoolEmail 인증 필수. 같은 세션의 인증 완료가 10분 이내여야 하며 가입 트랜잭션에서 한 번만 소비합니다. 성공 201, emailVerified=true; 자동 로그인·자동 메일 발송 없음.";
        if(path.equals("/api/v1/auth/login"))return "schoolEmail과 password로 인증합니다. 소문자 학교 이메일 전체 주소를 입력하세요. 별도 아이디나 학번으로 로그인할 수 없습니다. 성공 시 JSESSIONID 세션 쿠키를 설정하고 프로필을 반환합니다. 기존 CSRF 토큰은 폐기되므로 새 토큰을 받으세요.";
        if(path.equals("/api/v1/me/password"))return "현재 비밀번호 확인 후 새 비밀번호(10~64자, UTF-8 72바이트 이하)를 설정합니다. 기존 모든 세션을 만료하고 현재 세션에서 로그아웃합니다.";
        if(path.equals("/api/v1/student-codes/{type}"))return "type: majors, major-completion-types, certifications, tech-stacks, job-categories, regions, employment-types, salary-units.";
        if(method==PathItem.HttpMethod.PUT)return "해당 리소스 또는 선택 목록 전체를 교체합니다. 목록은 최대 100개이며 중복 코드 불가, 코드 사전에 존재해야 합니다. 빈 목록은 선택 해제.";
        if(method==PathItem.HttpMethod.POST)return "요청 스키마의 필수값·길이·범위 조건을 검증하여 생성합니다.";
        if(method==PathItem.HttpMethod.DELETE)return "지정한 본인 항목을 삭제합니다. 성공 204, 응답 본문 없음.";
        return "요청·응답 구조와 필수값, 길이, 범위는 아래 Schema를 확인하세요.";
    }
}
