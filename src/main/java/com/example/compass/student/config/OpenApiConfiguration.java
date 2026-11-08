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

            ### 별도 프론트엔드에서 세션 로그인 연동하기
            프론트와 백엔드 주소가 다르면 CSRF 발급, 로그인, 로그인 후 조회·저장 요청 모두에 `fetch`의 `credentials: "include"`를 지정하세요. 서버는 `COMPASS_SECURITY_ALLOWED_ORIGINS`에 프론트의 정확한 출처(스킴·호스트·포트)를 등록해야 합니다. 여러 출처는 쉼표로 구분하며 경로와 와일드카드는 지원하지 않습니다.

            1. `GET /api/v1/auth/csrf`를 호출해 세션 쿠키와 JSON의 `headerName`, `token`을 받습니다.
            2. 같은 세션으로 `POST /api/v1/auth/login`을 호출합니다. JSON 본문은 `{"schoolEmail":"student@sju.ac.kr","password":"..."}`이고, `Content-Type: application/json`과 응답에서 받은 이름·값으로 CSRF 헤더를 설정합니다.
            3. 로그인 성공 후 세션 ID와 CSRF 토큰이 바뀝니다. 이후 변경 요청(POST·PUT·PATCH·DELETE) 전에 `GET /api/v1/auth/csrf`를 다시 호출해 현재 세션의 토큰을 사용하세요. `GET /api/v1/me` 같은 조회에는 세션 쿠키만 필요합니다. 가입·인증번호 발송·확인도 로그인 전 세션에서 CSRF 토큰이 필요합니다.
            4. `401 UNAUTHENTICATED`·`SESSION_EXPIRED`는 로그인 화면으로 돌아갈 신호이고, 로그인 요청의 `401 INVALID_CREDENTIALS`는 입력값을 확인할 신호입니다. `403 CSRF_INVALID`이면 현재 세션에서 토큰을 다시 발급받으세요.

            `JSESSIONID`는 HttpOnly 쿠키이며 브라우저가 관리합니다. 서로 다른 사이트의 HTTPS 프론트와 백엔드가 쿠키를 공유하려면 백엔드에 `SESSION_COOKIE_SAME_SITE=none`과 `SESSION_COOKIE_SECURE=true`를 설정해야 합니다. 기본값 `lax`에서는 크로스사이트 fetch에 세션 쿠키가 전송되지 않습니다. 브라우저가 서드파티 쿠키를 차단하는 환경에서는 동일 출처 프록시를 통해 백엔드를 호출할 수 있습니다.

            ### Render 개발 서버에서 테스트하기
            현재 Render 개발 서버는 `MAIL_ENABLED=false`로 실행합니다. 가입 이메일 중복 확인은 가능하지만, 가입·기존 계정의 인증번호 발송은 `503 EMAIL_DELIVERY_FAILED`를 반환합니다.

            1. 신규 테스트 계정은 같은 `sejong-compass` DB에 연결된 로컬 Compass에서 SMTP를 켜고 학교 이메일 인증과 가입을 완료합니다.
            2. Render의 Compass Swagger UI를 열고 그 계정의 `schoolEmail`과 비밀번호로 `POST /api/v1/auth/login`을 실행합니다. `GET /api/v1/me`에서 프로필과 `emailVerified: true`를 확인합니다.
            3. 같은 브라우저에서 아래 온보딩 흐름을 테스트합니다. 로그인 이후 학생 정보 입력과 저장에는 SMTP가 필요 없습니다.

            ### 화면별 회원가입·온보딩 흐름

            | 화면 | 호출할 API | 다음 단계로 넘어가는 조건 |
            | --- | --- | --- |
            | 학교 이메일 중복 확인 | `POST /api/v1/auth/signup/email/availability` | `available: true`이면 인증번호를 발송합니다. |
            | 인증번호 발송·확인 | `POST /api/v1/auth/signup/email/send` → `POST /api/v1/auth/signup/email/confirm` | 같은 브라우저 세션에서 번호 확인이 성공하면, 같은 이메일로 10분 안에 가입합니다. |
            | 회원가입 | `POST /api/v1/auth/signup` | `201 Created`이면 로그인 화면으로 이동합니다. |
            | 로그인·이어하기 | `POST /api/v1/auth/login` → `GET /api/v1/me` → `GET /api/v1/me/onboarding` | 로그인과 조회에 성공하면 `nextStep`으로 이동합니다. 신규 계정은 모든 단계가 `NOT_STARTED`입니다. |
            | 학생 기본 정보·전공·성적표 | `GET /api/v1/me/majors`로 기존 전공을 불러옵니다. 첨부 시 `POST /api/v1/me/courses/import/preview`로 XLSX의 과목·평점·정규 이수학기를 확인합니다. 다음 버튼에서 `PATCH /api/v1/me` → `PUT /api/v1/me/majors` → 첨부한 경우 `POST /api/v1/me/courses/import/confirm-all`에 같은 세션의 `previewId`를 보냅니다. | 기본 정보·전공과 첨부한 성적표 저장이 모두 성공한 뒤, `BASIC_INFO`·`MAJORS`와 성적표를 처리한 경우 `COURSES`를 `COMPLETED`로 저장합니다. 성적표는 신규 저장 또는 기존과 동일한 과목이 한 건 이상 있어야 합니다. 상태 갱신까지 성공하면 취업 목표로 이동합니다. |
            | 취업 목표 | `GET /api/v1/me/career-goal`로 기존 목표를 불러옵니다. 첫 진입의 `404 ITEM_NOT_FOUND`는 미등록 상태이므로 빈 입력 폼을 열고, 입력 후 `PUT /api/v1/me/career-goal`로 저장합니다. 직무·지역 선택지는 `GET /api/v1/student-codes/job-categories`, `GET /api/v1/student-codes/regions/grouped`에서 조회합니다. | 목표 저장 후 `PREFERENCES`를 `COMPLETED`로 갱신합니다. |
            | 수강 과목 (선택) | `GET /api/v1/me/courses`로 조회하고 직접 입력 시 `POST /api/v1/me/courses`로 저장합니다. | 성적표로 이미 완료했다면 이어서 진행합니다. 저장된 과목이 있으면 `COURSES`를 `COMPLETED`, 없고 건너뛰면 `SKIPPED`로 갱신합니다. |
            | 프로젝트 (선택) | `GET /api/v1/me/projects`로 조회, `POST /api/v1/me/projects`로 추가 | `PROJECTS`를 완료하거나 건너뜁니다. |
            | 인턴 (선택) | `GET /api/v1/me/internships`로 조회, `POST /api/v1/me/internships`로 추가 | `INTERNSHIPS`를 완료하거나 건너뜁니다. |
            | 수상 (선택) | `GET /api/v1/me/awards`로 조회, `POST /api/v1/me/awards`로 추가 | `AWARDS`를 완료하거나 건너뜁니다. |
            | 자격증 (선택) | `GET /api/v1/student-codes/certifications`로 코드, `GET /api/v1/me/certifications`로 기존 선택을 조회합니다. `PUT /api/v1/me/certifications`에 유지할 기존 코드까지 포함해 전체 목록을 보냅니다. | `CERTIFICATIONS`를 완료하거나 건너뜁니다. |
            | 온보딩 완료 | `GET /api/v1/me/onboarding` | `finished: true`이면 완료입니다. 남은 단계는 `nextStep`으로 확인합니다. |

            데이터 저장과 단계 상태 저장은 별도입니다. 각 화면에서 저장이 성공하면 `PATCH /api/v1/me/onboarding`의 `steps`에 `{"step":"BASIC_INFO","status":"COMPLETED"}`처럼 변경할 상태를 담으세요. 선택 항목은 저장된 기록이 있으면 `COMPLETED`, 기록 없이 건너뛰면 `SKIPPED`를 보냅니다. 저장이나 상태 갱신에 실패하면 해당 항목의 오류를 표시하고 현재 화면에 머무르세요. 데이터 저장 후 상태 갱신만 실패했다면 상태 갱신을 다시 시도하세요.

            가입 API는 자동 로그인하지 않습니다. 같은 세션의 이메일 인증 증명은 가입 시 한 번만 사용됩니다. 로그인하면 세션 ID와 CSRF 토큰이 갱신됩니다. 기존 미인증 계정은 SMTP를 켠 환경에서 로그인한 뒤 `POST /api/v1/me/email-verification` → `POST /api/v1/me/email-verification/confirm`을 호출하세요.

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
                    description="로그인 없이 실행합니다. schoolEmail은 소문자 sju.ac.kr 또는 sejong.co.kr 주소입니다. ";
                    if(path.endsWith("/availability")){
                        description+="available=true이면 가입에 사용할 수 있고 false이면 이미 가입된 주소입니다. 중복 확인은 주소를 예약하지 않습니다.";
                    } else if(path.endsWith("/send")){
                        description+="6자리 번호를 발송합니다. expiresAt은 번호 만료 시각(10분 후), resendAvailableAt은 재발송 가능 시각(60초 후)입니다. 발송 한도는 실패를 포함해 이메일과 세션 각각 24시간당 10회입니다. 재발송하면 이전 번호와 인증 완료 상태가 무효화됩니다. 이미 가입된 주소는 409 EMAIL_EXISTS입니다.";
                        error(op,"429","EMAIL_RESEND_COOLDOWN: 재발송 간격 60초 / EMAIL_SEND_LIMIT: 이메일 또는 세션 발송 한도 초과");
                        error(op,"503","EMAIL_DELIVERY_FAILED: SMTP 비활성화, 미설정 또는 발송 실패");
                    } else {
                        description+="번호를 발송한 세션에서 같은 schoolEmail과 숫자 6자리 code 문자열을 보냅니다. 성공하면 emailVerified=true이며 verifiedUntil까지 같은 세션과 이메일로 가입할 수 있습니다(확인 성공 후 10분). 번호 오입력은 최대 5회입니다. 이미 가입된 주소는 409 EMAIL_EXISTS입니다.";
                        error(op,"429","EMAIL_ATTEMPTS_EXCEEDED: 번호 오입력 한도 초과. 새 번호를 발송하세요.");
                    }
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
                    error(op,"429",path.endsWith("/confirm")
                        ?"EMAIL_ATTEMPTS_EXCEEDED: 번호 오입력 한도 초과. 새 번호를 발송하세요."
                        :"EMAIL_RESEND_COOLDOWN: 재발송 간격 60초 / EMAIL_SEND_LIMIT: 24시간당 10회 초과");
                    if(path.endsWith("/confirm"))error(op,"400","INVALID_REQUEST: 숫자 6자리 필요 / EMAIL_CODE_INVALID / EMAIL_CODE_EXPIRED");
                    else error(op,"503","EMAIL_DELIVERY_FAILED: SMTP 미설정 또는 발송 실패. 비밀값과 번호는 응답에 포함하지 않습니다.");
                }
                if(path.endsWith("/import/preview")) error(op,"413","UPLOAD_TOO_LARGE: 비어 있거나 5MiB 초과 파일 / 요청 크기 초과");
                if(path.endsWith("/import/confirm"))error(op,"409","PREVIEW_NOT_FOUND / PREVIEW_EXPIRED / PREVIEW_CONSUMED / COURSE_IMPORT_CONFLICT / DATA_CONFLICT");
                if(path.endsWith("/readme"))error(op,"404","README_NOT_COLLECTED: 현재 저장소에 수집 이력 없음 / ITEM_NOT_FOUND: 프로젝트 없음 또는 다른 학생 소유");
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
        if(path.equals("/api/v1/auth/login"))return "schoolEmail과 password로 인증합니다. 소문자 학교 이메일 전체 주소를 입력하세요. 별도 아이디나 학번으로 로그인할 수 없습니다. Render 개발 서버에서는 SMTP가 꺼져 있으므로 학교 이메일 인증을 마친 기존 테스트 계정으로 로그인하세요. 성공 시 JSESSIONID 세션 쿠키를 설정하고 프로필을 반환합니다. 기존 CSRF 토큰은 폐기되므로 새 토큰을 받으세요.";
        if(path.equals("/api/v1/me/password"))return "현재 비밀번호 확인 후 새 비밀번호(10~64자, UTF-8 72바이트 이하)를 설정합니다. 기존 모든 세션을 만료하고 현재 세션에서 로그아웃합니다.";
        if(path.equals("/api/v1/student-codes/{type}"))return "type: majors, major-completion-types, certifications, tech-stacks, job-categories, regions, employment-types, salary-units.";
        if(method==PathItem.HttpMethod.PUT)return "해당 리소스 또는 선택 목록 전체를 교체합니다. 목록은 최대 100개이며 중복 코드 불가, 코드 사전에 존재해야 합니다. 빈 목록은 선택 해제.";
        if(method==PathItem.HttpMethod.POST)return "요청 스키마의 필수값·길이·범위 조건을 검증하여 생성합니다.";
        if(method==PathItem.HttpMethod.DELETE)return "지정한 본인 항목을 삭제합니다. 성공 204, 응답 본문 없음.";
        return "요청·응답 구조와 필수값, 길이, 범위는 아래 Schema를 확인하세요.";
    }
}
