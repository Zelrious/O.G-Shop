[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Show-CommandVersion {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [scriptblock] $VersionCommand
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Write-Error "Missing required command: $Name"
        return
    }

    $version = & $VersionCommand 2>&1 | Select-Object -First 1
    Write-Host ("{0}: {1}" -f $Name, $version)
}

Show-CommandVersion -Name 'java' -VersionCommand { java -version }
Show-CommandVersion -Name 'node' -VersionCommand { node --version }
Show-CommandVersion -Name 'npm' -VersionCommand { npm --version }
Show-CommandVersion -Name 'docker' -VersionCommand { docker --version }
Show-CommandVersion -Name 'git' -VersionCommand { git --version }

Write-Host 'Prerequisite commands are available.'
