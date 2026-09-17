[CmdletBinding()]
param(
    [string[]] $MavenArguments = @('--batch-mode', '--no-transfer-progress', 'verify')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$wrapperJar = Join-Path $backendRoot '.mvn/wrapper/maven-wrapper.jar'

if (-not (Test-Path $wrapperJar)) {
    throw 'Maven Wrapper JAR is missing.'
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

$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (-not (Test-Path $javaExecutable)) {
    $javaExecutable = Join-Path $env:JAVA_HOME 'bin/java'
}
if (-not (Test-Path $javaExecutable)) {
    throw "Java executable not found under JAVA_HOME: $env:JAVA_HOME"
}

$env:MAVEN_USER_HOME = Join-Path $backendRoot '.mvn-user-home'

Push-Location $backendRoot
try {
    & $javaExecutable `
        -classpath '.mvn/wrapper/maven-wrapper.jar' `
        '-Dmaven.multiModuleProjectDirectory=.' `
        org.apache.maven.wrapper.MavenWrapperMain `
        @MavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Backend verification failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
