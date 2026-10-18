<#
.SYNOPSIS
  Installs a new version of the Guards of Atlantis hub on Windows, or publishes the Android app.

.EXAMPLE
  .\update.ps1 C:\Users\me\Downloads\goa-hub-1.2.0.zip    # install that hub version
  .\update.ps1 C:\Users\me\Downloads\app-release.apk      # offer this APK at https://<hub>/app

.NOTES
  Run in PowerShell as Administrator. A hub update stops the hub, backs up its data, unpacks the
  new version next to the old one, points C:\goa-hub\current at it, starts it and checks
  /api/v1/health. If it does not come up within 90 seconds, the old version and the data from
  just before come back.
#>
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Path)

$ErrorActionPreference = 'Stop'
$Root = 'C:\goa-hub'
$Releases = Join-Path $Root 'releases'
$Current = Join-Path $Root 'current'
$Data = 'C:\ProgramData\goa-hub'
$Backups = Join-Path $Root 'backups'
$TaskName = 'GoA Hub'
$HealthUrl = 'http://127.0.0.1:8080/api/v1/health'
$KeepReleases = 3

function Say($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this in PowerShell opened with "Run as administrator".'
}
$File = (Resolve-Path -LiteralPath $Path).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem

# --- The Android app ---------------------------------------------------------------------------
if ($File -like '*.apk') {
    $zip = [IO.Compression.ZipFile]::OpenRead($File)
    try { $isApp = [bool]($zip.Entries | Where-Object { $_.FullName -eq 'AndroidManifest.xml' }) } finally { $zip.Dispose() }
    if (-not $isApp) { throw "$Path is not an Android app (APK)." }
    $appDir = Join-Path $Data 'app'
    New-Item -ItemType Directory -Force -Path $appDir | Out-Null
    Copy-Item -LiteralPath $File -Destination (Join-Path $appDir 'goa.apk.new') -Force
    Move-Item -LiteralPath (Join-Path $appDir 'goa.apk.new') -Destination (Join-Path $appDir 'goa.apk') -Force
    Say 'Published. Signed-in friends can download it from https://<your hub>/app'
    (Get-FileHash (Join-Path $appDir 'goa.apk') -Algorithm SHA256).Hash
    return
}
if ($File -notlike '*.zip') { throw 'Expected a goa-hub-*.zip (the hub) or an .apk (the Android app).' }
if (-not (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue)) {
    throw "The scheduled task '$TaskName' does not exist yet. Run install.ps1 first."
}

function Stop-Hub {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    # Stopping the task ends cmd.exe; make sure its java.exe goes too.
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*$Root*" } |
        ForEach-Object { Invoke-CimMethod -InputObject $_ -MethodName Terminate | Out-Null }
    Start-Sleep -Seconds 2
}

function Test-Healthy {
    for ($i = 0; $i -lt 45; $i++) {
        try {
            $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3
            if ($r.Content -match '"status"\s*:\s*"ok"') { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Switch-Current($target) {
    if (Test-Path $Current) { cmd /c rmdir "$Current" | Out-Null }   # removes the junction only
    New-Item -ItemType Junction -Path $Current -Target $target | Out-Null
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$name = ([IO.Path]::GetFileNameWithoutExtension($File)) -replace '[^A-Za-z0-9._-]', '_'
$newRelease = Join-Path $Releases "$name-$stamp"
New-Item -ItemType Directory -Force -Path $Releases, $Backups | Out-Null

Say "Unpacking $(Split-Path $File -Leaf)"
$staging = Join-Path $Releases ".staging-$stamp"
Expand-Archive -LiteralPath $File -DestinationPath $staging
$launchers = @(Get-ChildItem -Path $staging -Recurse -Depth 2 -Filter 'goa-hub.bat' | Where-Object { $_.Directory.Name -eq 'bin' })
if ($launchers.Count -ne 1) {
    Remove-Item -Recurse -Force $staging
    throw "Expected exactly one bin\goa-hub.bat inside the zip, found $($launchers.Count). Is this the hub's distZip?"
}
Move-Item -LiteralPath $launchers[0].Directory.Parent.FullName -Destination $newRelease
Remove-Item -Recurse -Force $staging

$previous = $null
if (Test-Path $Current) { $previous = (Get-Item $Current).Target | Select-Object -First 1 }

Say 'Stopping the hub'
Stop-Hub

$snapshot = $null
if (Get-ChildItem -Path $Data -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1) {
    $snapshot = Join-Path $Backups "goa-hub-before-update-$stamp.zip"
    Say "Backing up the data to $snapshot"
    Compress-Archive -Path (Join-Path $Data '*') -DestinationPath $snapshot
}

Say "Starting $(Split-Path $newRelease -Leaf)"
Switch-Current $newRelease
Start-ScheduledTask -TaskName $TaskName
if (Test-Healthy) {
    Say "The hub is up: $((Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing).Content)"
    Get-ChildItem -Path $Releases -Directory | Where-Object { $_.Name -notlike '.*' -and $_.FullName -ne $newRelease } |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip ($KeepReleases - 1) |
        ForEach-Object { Write-Host "Removing old version $($_.Name)"; Remove-Item -Recurse -Force $_.FullName }
    return
}

Write-Host "`nThe new version did not come up. The end of its log:" -ForegroundColor Yellow
Get-Content (Join-Path $Root 'logs\hub.log') -Tail 25 -ErrorAction SilentlyContinue
if (-not $previous) {
    Stop-Hub
    throw 'This was the first install, so there is nothing to roll back to. Fix the error above and run it again.'
}
Say "Rolling back to $(Split-Path $previous -Leaf)"
Stop-Hub
if ($snapshot) {
    $failed = "$Data.failed-$stamp"
    Move-Item -LiteralPath $Data -Destination $failed
    New-Item -ItemType Directory -Path $Data | Out-Null
    # Only SYSTEM and Administrators may read the data (it holds the group key).
    icacls $Data /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q | Out-Null
    Expand-Archive -LiteralPath $snapshot -DestinationPath $Data
    Write-Host "Data put back from $snapshot. What the new version left is in $failed (delete it when done)."
}
Switch-Current $previous
Start-ScheduledTask -TaskName $TaskName
if (Test-Healthy) { throw "Update failed; the previous version is running again. The broken version stays in $newRelease." }
throw 'Update failed and the previous version did not come back either. See C:\goa-hub\logs\hub.log.'
