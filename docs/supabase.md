# Supabase connection

The `supabase` profile connects to sejong-compass through the Seoul Session pooler on port 5432 with SSL (`sslmode=require`). The JDBC URL contains no password. This profile is opt-in; the existing default/local settings are unchanged. Do not combine `local` and `supabase` profiles.

Required process environment:

- `SPRING_PROFILES_ACTIVE=supabase`
- `SUPABASE_DB_PASSWORD`: Supabase database password, supplied at runtime. No default or local-password fallback.

Use a deployment secret manager or a hidden PowerShell prompt. Never put the password in source, a URL, a command argument, a committed file, or a transcript. In a PowerShell window without transcription, prepare the environment as follows (this does not start the server):

```powershell
$taskSecret = Read-Host 'Supabase DB password' -AsSecureString
$taskPtr = [IntPtr]::Zero
try {
    $taskPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
    $env:SUPABASE_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPtr)
    $env:SPRING_PROFILES_ACTIVE = 'supabase'
} finally {
    if ($taskPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPtr)
    }
    $taskSecret.Dispose()
}
```

Only launch the application later when explicitly intended, from that same terminal. Environment variables exist in process memory; the application must receive the password to connect. Clear both variables or close the terminal after use:

```powershell
Remove-Item Env:SUPABASE_DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
```

Existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` variables are reserved for the previous connection configuration; this profile specifies its own datasource properties. Avoid conflicting `SPRING_DATASOURCE_*` variables or command-line datasource overrides, which take precedence over profile files.

JPA uses validate, not schema creation. Flyway remains enabled and will validate the restored V1-V8 history and apply any future pending migrations on application startup. Do not add/edit migrations just to connect. Hikari uses up to 3 connections with no minimum idle connections. Internal import endpoints and the collector are disabled by this profile. Listen address remains loopback.

Database owner `postgres.<project-ref>` is used for compatibility with the restored objects and Flyway; this is not a least-privilege production application role. Supabase RLS does not replace application authorization for this privileged connection. Role separation is a future deployment design decision.

The application was not started against Supabase as part of configuring this profile. Local automated tests do not prove remote authentication, Flyway runtime validation or JPA remote validation.

Connection guidance: https://supabase.com/docs/guides/database/connecting-to-postgres
