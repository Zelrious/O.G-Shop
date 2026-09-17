[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$required = @(
    'README.md',
    'docs/PROJECT_STRUCTURE.md',
    'docs/modules/INDEX.md',
    'docs/progress/STATUS.md',
    'docs/progress/BACKLOG.md',
    'docs/progress/DECISIONS_PENDING.md',
    'docs/requirements/REQUIREMENTS_INDEX.md',
    'docs/requirements/TRACEABILITY_MATRIX.md',
    'reference/SOURCE_REGISTER.md'
)
$modules = @('identity', 'catalog', 'communication', 'commerce', 'payment', 'fulfillment', 'trust-safety', 'platform')
$requiredHeadings = @('Purpose and scope', 'Owned paths', 'Completed tasks', 'Remaining tasks')
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($path in $required) {
    if (-not (Test-Path (Join-Path $repoRoot $path))) {
        $errors.Add("Missing required document: $path")
    }
}

foreach ($module in $modules) {
    $relativePath = "docs/modules/$module.md"
    $fullPath = Join-Path $repoRoot $relativePath
    if (-not (Test-Path $fullPath)) {
        $errors.Add("Missing module document: $relativePath")
        continue
    }

    $content = Get-Content -Raw $fullPath
    foreach ($heading in $requiredHeadings) {
        if ($content -notmatch "(?m)^## $([regex]::Escape($heading))") {
            $errors.Add("$relativePath is missing heading: $heading")
        }
    }
}

if ($errors.Count -gt 0) {
    throw "Documentation validation failed:`n - $($errors -join "`n - ")"
}

Write-Host "Documentation structure valid: $($required.Count) core documents and $($modules.Count) module documents."
