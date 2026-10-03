[CmdletBinding()]
param(
    [string] $ContainerName = 'og-shop-postgres-1',
    [string] $DatabaseName = 'og_shop',
    [string] $DatabaseUser = 'og_shop',
    [switch] $VerifyOnly,
    [switch] $BuyerVerified,
    [switch] $MultipleCategories
)

$ErrorActionPreference = 'Stop'
# Invoke explicitly against a development/test database. Does not read or print .env.
# Docker cp preserves UTF-8 and literal SQL $ delimiters better than a shell pipe.
function Invoke-SeedSql {
    param([Parameter(Mandatory)][string] $FileName)
    $seedLocalPath = Join-Path $PSScriptRoot $FileName
    if (-not (Test-Path -LiteralPath $seedLocalPath -PathType Leaf)) {
        throw "SQL file missing: $seedLocalPath"
    }
    $seedContainerPath = '/tmp/ogshop-seed-' + [guid]::NewGuid().ToString('N') + '.sql'
    & docker cp $seedLocalPath "${ContainerName}:$seedContainerPath"
    if ($LASTEXITCODE -ne 0) { throw "Cannot copy SQL to container '$ContainerName'." }
    try {
        & docker exec $ContainerName psql -X -v ON_ERROR_STOP=1 -U $DatabaseUser -d $DatabaseName -f $seedContainerPath
        if ($LASTEXITCODE -ne 0) { throw "SQL failed in '$DatabaseName'; inspect the error above." }
    }
    finally {
        # Delete only this generated /tmp SQL file, never database data or a volume.
        & docker exec $ContainerName rm -- $seedContainerPath
        if ($LASTEXITCODE -ne 0) { Write-Warning "Temporary container SQL file was not removed: $seedContainerPath" }
    }
}

if ($VerifyOnly -and ($BuyerVerified -or $MultipleCategories)) {
    throw '-VerifyOnly cannot be combined with fixture-changing switches.'
}
Write-Host "Target: container=$ContainerName database=$DatabaseName user=$DatabaseUser"
if (-not $VerifyOnly) {
    Invoke-SeedSql -FileName 'development_test_data.sql'
    if ($BuyerVerified) { Invoke-SeedSql -FileName 'set_test_buyer_verified.sql' }
    if ($MultipleCategories) { Invoke-SeedSql -FileName 'add_test_product_categories.sql' }
}
Invoke-SeedSql -FileName 'verify_test_data.sql'
