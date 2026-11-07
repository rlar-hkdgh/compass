param(
    [Parameter(Mandatory)][string]$Plan,
    [Parameter(Mandatory)][string]$PreviewFile,
    [ValidateSet('Preview','Apply')][string]$Mode = 'Preview',
    [string]$Workbook,
    [string]$CourseSourceUrl,
    [switch]$JobCenter,
    [switch]$Dodream,
    [switch]$Bootcamps
)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskJar = Join-Path $taskProject 'build/libs/compass-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Build bootJar before collecting.' }
if (-not (Test-Path -LiteralPath $Plan)) { throw 'A reviewed official URL plan is required.' }
if (Test-Path -LiteralPath $PreviewFile) { throw 'Preview output already exists; use a new path.' }
if ($Workbook -and (-not $CourseSourceUrl -or -not (Test-Path -LiteralPath $Workbook))) { throw 'Workbook and its official source URL must both be supplied.' }
$taskPreviousProfile = $env:SPRING_PROFILES_ACTIVE
$taskPreviousSecret = $env:SUPABASE_DB_PASSWORD
$taskSecret = $null
try {
    $env:SPRING_PROFILES_ACTIVE = 'supabase'
    if (-not $env:SUPABASE_DB_PASSWORD) {
        $taskSecret = Read-Host 'Supabase DB password (hidden, not saved)' -AsSecureString
        $env:SUPABASE_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $taskSecret).Password
    }
    $taskJava = (Get-Command java -ErrorAction Stop).Source
    $taskArgs = @('-Dfile.encoding=UTF-8','-jar',$taskJar,
        '--spring.main.web-application-type=none', '--spring.flyway.enabled=false',
        '--spring.jpa.hibernate.ddl-auto=validate',
        '--spring.datasource.url=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require',
        '--spring.datasource.username=postgres.rqecthphhcvmnvxrbubg',
        '--compass.recruit.crawl.enabled=false', '--compass.activity.collect.enabled=true',
        "--compass.activity.collect.plan=$([IO.Path]::GetFullPath($Plan))",
        "--compass.activity.collect.preview=$([IO.Path]::GetFullPath($PreviewFile))",
        "--compass.activity.collect.apply=$($Mode -eq 'Apply')")
    if ($Bootcamps) { $taskArgs += '--compass.activity.collect.bootcamps.enabled=true' }
    if ($Dodream) {
        $taskArgs += '--compass.activity.collect.dodream.enabled=true'
    }
    if ($JobCenter) {
        $taskArgs += '--compass.activity.collect.job-center.enabled=true'
    }
    if ($Workbook) {
        $taskArgs += "--compass.activity.collect.workbook=$([IO.Path]::GetFullPath($Workbook))"
        $taskArgs += "--compass.activity.collect.course-source-url=$CourseSourceUrl"
    }
    & $taskJava @taskArgs
    if ($LASTEXITCODE -ne 0) { throw 'Collection stopped. Earlier per-activity commits may exist in Apply mode; inspect ACTIVITY logs before any retry.' }
} finally {
    if ($null -eq $taskPreviousProfile) { Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue } else { $env:SPRING_PROFILES_ACTIVE=$taskPreviousProfile }
    if ($null -eq $taskPreviousSecret) { Remove-Item Env:SUPABASE_DB_PASSWORD -ErrorAction SilentlyContinue } else { $env:SUPABASE_DB_PASSWORD=$taskPreviousSecret }
    if ($taskSecret) { $taskSecret.Dispose() }
}
