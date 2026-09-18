[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$skills = @(
    'repo-onboarding',
    'requirement-impact-analysis',
    'backend-module-change',
    'frontend-feature-change',
    'marketplace-ui-implementation',
    'database-migration',
    'api-contract',
    'secure-change-review',
    'test-verification',
    'session-handoff'
)
$workflows = @(
    'session-start.md',
    'requirement-impact-analysis.md',
    'feature-delivery.md',
    'bug-fix.md',
    'database-change.md',
    'api-contract-change.md',
    'security-review.md',
    'documentation-sync.md',
    'code-review.md',
    'session-close.md'
)

$missing = [System.Collections.Generic.List[string]]::new()
foreach ($skill in $skills) {
    foreach ($path in @(
        ".agents/skills/$skill/SKILL.md",
        ".claude/skills/$skill/SKILL.md"
    )) {
        if (-not (Test-Path (Join-Path $repoRoot $path))) {
            $missing.Add($path)
        }
    }
}

foreach ($workflow in $workflows) {
    $path = ".agents/workflows/$workflow"
    if (-not (Test-Path (Join-Path $repoRoot $path))) {
        $missing.Add($path)
    }
}

foreach ($path in @('AGENTS.md', 'CLAUDE.md', 'GEMINI.md')) {
    if (-not (Test-Path (Join-Path $repoRoot $path))) {
        $missing.Add($path)
    }
}

if ($missing.Count -gt 0) {
    throw "Missing agent assets:`n - $($missing -join "`n - ")"
}

Write-Host "Agent assets valid: $($skills.Count) canonical skills, $($skills.Count) Claude adapters, $($workflows.Count) workflows."
