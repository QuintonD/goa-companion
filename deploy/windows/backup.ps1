<#
.SYNOPSIS
  Daily backup of the hub's data (C:\ProgramData\goa-hub) to C:\goa-hub\backups, kept 14 days.
  Run by the "GoA Hub backup" scheduled task that install.ps1 creates. The zips hold the group
  key: the backups folder is readable by SYSTEM and Administrators only.
#>
$ErrorActionPreference = 'Stop'
$Data = 'C:\ProgramData\goa-hub'
$Backups = 'C:\goa-hub\backups'
$KeepDays = 14

if (-not (Get-ChildItem -Path $Data -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1)) { return }
New-Item -ItemType Directory -Force -Path $Backups | Out-Null
$target = Join-Path $Backups ("goa-hub-{0}.zip" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
# The hub writes each file atomically (temp file, then rename); if one moved under us, try again.
try {
    Compress-Archive -Path (Join-Path $Data '*') -DestinationPath $target
} catch {
    Start-Sleep -Seconds 5
    Remove-Item -LiteralPath $target -ErrorAction SilentlyContinue
    Compress-Archive -Path (Join-Path $Data '*') -DestinationPath $target
}
Get-ChildItem -Path $Backups -Filter 'goa-hub-*.zip' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
    Remove-Item -Force
