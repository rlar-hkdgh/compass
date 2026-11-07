# Compass 백엔드

## 로컬 실행

JDK 17과 Git이 필요합니다. IntelliJ에서 프로젝트를 열 때 Gradle JVM도 JDK 17로 설정합니다.

### 1. 개발 브랜치 받기

```powershell
git clone --branch dev https://github.com/rlar-hkdgh/compass.git
cd compass
Copy-Item .env.example .env
```

이미 저장소가 있다면 `dev`로 전환해 최신 코드를 받고, 기존 `.env`를 사용합니다.

### 2. 환경 설정

프로젝트 루트의 `.env`를 열고 다음 값을 설정합니다.

| 항목 | 값 |
| --- | --- |
| `SUPABASE_DB_PASSWORD` | `sejong-compass` 프로젝트의 PostgreSQL 접속 비밀번호 |
| `API_DOCS_ENABLED` | Swagger를 열려면 `true` |
| `SESSION_COOKIE_SECURE` | 로컬 HTTP에서 로그인하려면 `false` |
| `FLYWAY_ENABLED` | 공유 DB에 자동 마이그레이션을 실행하지 않도록 `false` |

비밀값은 각자 로컬 `.env`에만 넣습니다. 실제 학교 이메일 가입을 테스트할 때는 `MAIL_ENABLED=true`와 `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`도 설정합니다. 이메일 인증을 마친 계정으로 로그인하는 데는 SMTP 설정이 필요 없습니다.

### 3. 백엔드 실행

IntelliJ에서 공유 실행 설정 **CompassApplication**을 선택하고 실행합니다. 터미널에서는 프로젝트 루트에서 아래 명령을 실행합니다. 터미널 실행에서도 데모를 열려면 `.env`의 `DEMO_ENABLED=true`를 설정합니다.

```powershell
.\gradlew.bat bootRun
```

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)
- [온보딩 데모](http://localhost:8080/demo/)
