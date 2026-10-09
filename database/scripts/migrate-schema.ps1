[CmdletBinding()]
param(
    [ValidateSet('validate', 'migrate')][string] $Action = 'validate',
    [string] $BackupPath,
    [switch] $SkipBuild
)
$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskBackend = Join-Path $taskRepo 'backend'
foreach ($taskName in @('OGSHOP_MIGRATION_DB_URL', 'OGSHOP_MIGRATION_DB_USER', 'OGSHOP_MIGRATION_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskName))) {
        throw "Missing environment variable: $taskName"
    }
}
if ($Action -eq 'migrate') {
    if ([string]::IsNullOrWhiteSpace($BackupPath) -or -not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) {
        throw 'For an existing database, pass the verified pg_dump custom backup with -BackupPath.'
    }
    $taskStream = [IO.File]::OpenRead((Resolve-Path -LiteralPath $BackupPath).Path)
    try {
        $taskHeader = [byte[]]::new(5)
        if ($taskStream.Read($taskHeader, 0, 5) -ne 5 -or [Text.Encoding]::ASCII.GetString($taskHeader) -ne 'PGDMP') {
            throw 'Expected a PostgreSQL custom archive (PGDMP).'
        }
    } finally { $taskStream.Dispose() }
}
if (-not $SkipBuild) {
    & (Join-Path $taskRepo 'scripts/quality/verify-backend.ps1') -MavenArguments @(
        '--batch-mode', '--no-transfer-progress', '-DskipTests', 'compile',
        'dependency:build-classpath', '-Dmdep.outputFile=target/migration.classpath')
}
$taskClasspathFile = Join-Path $taskBackend 'target/migration.classpath'
if (-not (Test-Path -LiteralPath $taskClasspathFile)) { throw 'Build the migration classpath first.' }
foreach ($taskSource in Get-ChildItem (Join-Path $taskBackend 'src/main/resources/db/migration') -Filter 'V*__*.sql') {
    $taskCompiled = Join-Path $taskBackend ('target/classes/db/migration/' + $taskSource.Name)
    if (-not (Test-Path -LiteralPath $taskCompiled) -or
        (Get-FileHash $taskSource.FullName).Hash -ne (Get-FileHash $taskCompiled).Hash) {
        throw "Compiled migration differs from source: $($taskSource.Name). Rebuild before running."
    }
}
$taskClasspath = (Join-Path $taskBackend 'target/classes') + [IO.Path]::PathSeparator +
    (Get-Content -Raw -LiteralPath $taskClasspathFile).Trim()
$taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
& $taskJava '-cp' $taskClasspath 'com.oldbutgold.shop.modules.platform.infrastructure.SchemaMigrationCli' $Action
if ($LASTEXITCODE -ne 0) { throw "Schema $Action failed; do not repair checksums or rerun business callbacks." }
