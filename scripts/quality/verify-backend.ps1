[CmdletBinding()]
param(
    [string[]] $MavenArguments = @('--batch-mode', '--no-transfer-progress', 'verify')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$isWindowsPlatform = ($env:OS -eq 'Windows_NT')
$wrapperExecutable = if ($isWindowsPlatform) {
    Join-Path $backendRoot 'mvnw.cmd'
}
else {
    Join-Path $backendRoot 'mvnw'
}

if (-not (Test-Path $wrapperExecutable)) {
    throw "Maven Wrapper script is missing: $wrapperExecutable"
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaHomeLine = java -XshowSettings:properties -version 2>&1 |
        Select-String '^\s*java.home\s*=' |
        Select-Object -First 1
    if ($null -eq $javaHomeLine) {
        throw 'JAVA_HOME is not set and java.home could not be detected.'
    }
    $env:JAVA_HOME = ($javaHomeLine.Line -split '=', 2)[1].Trim()
}

Push-Location $backendRoot
try {
    & $wrapperExecutable @MavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Backend verification failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
