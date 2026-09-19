[CmdletBinding()]
param(
    [string] $PostgresImage = 'pgvector/pgvector:pg17',
    [switch] $Reset,
    [switch] $KeepContainer
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$containerName = 'og-shop-database-tests'
$databaseName = 'ogshop_test'
$databaseUser = 'ogshop_test'
$databasePassword = 'ogshop_test'

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]] $Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code ${LASTEXITCODE}: docker $($Arguments -join ' ')"
    }
}

function Invoke-SqlFile {
    param(
        [Parameter(Mandatory)][string] $Database,
        [Parameter(Mandatory)][string] $ContainerPath
    )

    Invoke-Docker @(
        'exec', $containerName,
        'psql', '-v', 'ON_ERROR_STOP=1',
        '-U', $databaseUser,
        '-d', $Database,
        '-f', $ContainerPath
    )
}

$existingContainer = (@(& docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}') -join '').Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'Docker is not available. Start Docker Desktop and retry.'
}

if ($existingContainer) {
    if (-not $Reset) {
        throw "Test container '$containerName' already exists. Re-run with -Reset to replace only this test container."
    }
    Invoke-Docker @('rm', '-f', $containerName)
}

try {
    Invoke-Docker @(
        'run', '--name', $containerName,
        '-e', "POSTGRES_DB=$databaseName",
        '-e', "POSTGRES_USER=$databaseUser",
        '-e', "POSTGRES_PASSWORD=$databasePassword",
        '-d', $PostgresImage
    )

    $ready = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        & docker exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw 'PostgreSQL test container did not become ready within 30 seconds.'
    }

    $files = @{
        (Join-Path $repoRoot 'backend/src/main/resources/db/migration/V1__initial_schema.sql') = '/tmp/V1.sql'
        (Join-Path $repoRoot 'backend/src/main/resources/db/migration/V2__identity_and_ekyc_security_baseline.sql') = '/tmp/V2.sql'
        (Join-Path $repoRoot 'backend/src/main/resources/db/migration/V3__pricing_negotiation_and_chat_baseline.sql') = '/tmp/V3.sql'
        (Join-Path $repoRoot 'database/tests/database_tests.sql') = '/tmp/database_tests.sql'
        (Join-Path $repoRoot 'database/tests/v3_legacy_fixture.sql') = '/tmp/v3_legacy_fixture.sql'
        (Join-Path $repoRoot 'database/tests/v3_legacy_assertions.sql') = '/tmp/v3_legacy_assertions.sql'
    }

    foreach ($entry in $files.GetEnumerator()) {
        Invoke-Docker @('cp', $entry.Key, "${containerName}:$($entry.Value)")
    }

    Write-Host 'Running clean V1 -> V2 -> V3 migration and invariant tests...'
    Invoke-SqlFile -Database $databaseName -ContainerPath '/tmp/V1.sql'
    Invoke-SqlFile -Database $databaseName -ContainerPath '/tmp/V2.sql'
    Invoke-SqlFile -Database $databaseName -ContainerPath '/tmp/V3.sql'
    Invoke-SqlFile -Database $databaseName -ContainerPath '/tmp/database_tests.sql'

    Write-Host 'Running V1/V2 legacy-data -> V3 backfill test...'
    Invoke-Docker @('exec', $containerName, 'createdb', '-U', $databaseUser, 'ogshop_legacy')
    Invoke-SqlFile -Database 'ogshop_legacy' -ContainerPath '/tmp/V1.sql'
    Invoke-SqlFile -Database 'ogshop_legacy' -ContainerPath '/tmp/V2.sql'
    Invoke-SqlFile -Database 'ogshop_legacy' -ContainerPath '/tmp/v3_legacy_fixture.sql'
    Invoke-SqlFile -Database 'ogshop_legacy' -ContainerPath '/tmp/V3.sql'
    Invoke-SqlFile -Database 'ogshop_legacy' -ContainerPath '/tmp/v3_legacy_assertions.sql'

    Write-Host 'PASS: clean migration, database invariants and legacy V3 backfill completed.'
}
finally {
    if (-not $KeepContainer) {
        $containerStillExists = (@(& docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}') -join '').Trim()
        if ($containerStillExists) {
            & docker rm -f $containerName *> $null
        }
    }
}
