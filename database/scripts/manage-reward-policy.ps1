[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('provision', 'verify')]
    [string] $Action,
    [long] $AdminId
)
$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskBackend = Join-Path $taskRepo 'backend'

foreach ($taskName in @('OGSHOP_MIGRATION_DB_URL', 'OGSHOP_MIGRATION_DB_USER', 'OGSHOP_MIGRATION_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskName))) {
        throw "Missing required environment variable: $taskName"
    }
}

if ($Action -eq 'provision' -and -not $AdminId) {
    $envAdmin = [Environment]::GetEnvironmentVariable('OGSHOP_REWARD_POLICY_ADMIN_ID')
    if (-not [string]::IsNullOrWhiteSpace($envAdmin)) {
        $AdminId = [long]$envAdmin
    } else {
        throw "For action 'provision', pass -AdminId <Long> or set OGSHOP_REWARD_POLICY_ADMIN_ID environment variable."
    }
}

$taskClasspathFile = Join-Path $taskBackend 'target/migration.classpath'
if (-not (Test-Path -LiteralPath $taskClasspathFile)) {
    Write-Host "Building migration classpath..."
    & (Join-Path $taskRepo 'scripts/quality/verify-backend.ps1') -MavenArguments @(
        '--batch-mode', '--no-transfer-progress', '-DskipTests', 'compile',
        'dependency:build-classpath', '-Dmdep.outputFile=target/migration.classpath')
}

$taskClasspath = (Join-Path $taskBackend 'target/classes') + [IO.Path]::PathSeparator +
    (Get-Content -Raw -LiteralPath $taskClasspathFile).Trim()
$taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }

$cliAction = if ($Action -eq 'provision') { 'provision-reward-policy' } else { 'verify-reward-policy' }
$cliArgs = @('-cp', $taskClasspath, 'com.oldbutgold.shop.modules.platform.infrastructure.SchemaMigrationCli', $cliAction)
if ($Action -eq 'provision') {
    $cliArgs += $AdminId.ToString()
}

& $taskJava $cliArgs
if ($LASTEXITCODE -ne 0) {
    throw "Reward policy $Action failed with exit code $LASTEXITCODE."
}

