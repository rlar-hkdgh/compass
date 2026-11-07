param(
    [ValidateRange(1,2147483647)][int]$StartPage = 1,
    [ValidateRange(0,2147483647)][int]$MaxPages = 0,
    [switch]$DiscoverOnly
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'build/libs/compass-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw '실행 파일이 없습니다. 먼저 프로젝트에서 gradlew.bat bootJar를 실행하세요.'
}
# No bundled local PostgreSQL service; require an explicit collection target.
if (-not $env:DB_URL) { throw '수집 대상 DB_URL을 명시하세요. 기본 Compose는 Supabase 백엔드 실행용이며 로컬 DB를 생성하지 않습니다.' }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'compass' }
if (-not $env:DB_PASSWORD) {
    $dbSecret = Read-Host '수집 대상 PostgreSQL 비밀번호' -AsSecureString
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
}
$javaCommand = (Get-Command java -ErrorAction Stop).Source
$javaArguments = @(
    '-Dfile.encoding=UTF-8', '-jar', $jarPath,
    '--spring.main.web-application-type=none',
    '--compass.recruit.crawl.enabled=true',
    "--compass.recruit.crawl.start-page=$StartPage",
    "--compass.recruit.crawl.max-pages=$MaxPages"
)
if ($DiscoverOnly) { $javaArguments += '--compass.recruit.crawl.discover-only=true' }
& $javaCommand @javaArguments
if ($LASTEXITCODE -ne 0) { throw '수집이 중단됐습니다. 마지막 CRAWL 로그와 오류를 확인하세요.' }
