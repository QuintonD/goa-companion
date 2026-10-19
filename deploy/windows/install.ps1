<#
.SYNOPSIS
  Sets up the Guards of Atlantis group hub on a Windows PC (ADR-030 §10).

.DESCRIPTION
  The usual way publishes the hub with Tailscale Funnel at https://<machine>.<tailnet>.ts.net.
  -WithCaddy is the alternative: Caddy + a DuckDNS name + ports 80 and 443 forwarded on the router.
  Both ways: Java 21 (winget), the hub in C:\goa-hub, its data in C:\ProgramData\goa-hub (SYSTEM
  and Administrators only), a "GoA Hub" scheduled task that starts it at boot on 127.0.0.1:8080,
  a daily backup task, and sleep turned off on mains power.
  Safe to run again. Run in PowerShell opened with "Run as administrator":
    Set-ExecutionPolicy -Scope Process Bypass
    .\install.ps1 -Zip C:\Users\me\Downloads\goa-hub-1.0.0.zip

.EXAMPLE
  .\install.ps1 -Zip .\goa-hub-1.0.0.zip
  .\install.ps1 -Zip .\goa-hub-1.0.0.zip -WithCaddy -Domain ourgroup -DuckDnsToken xxxxxxxx-...
#>
[CmdletBinding()]
param(
    [string]$Zip,
    [switch]$WithCaddy,
    [string]$Domain,
    [string]$DuckDnsToken,
    [string]$Invite,
    [string]$AndroidCertSha256,
    [string]$TailscaleHostname
)

$ErrorActionPreference = 'Stop'
$Kit = Split-Path -Parent $PSScriptRoot
$Root = 'C:\goa-hub'
$Data = 'C:\ProgramData\goa-hub'
$Current = Join-Path $Root 'current'
$Logs = Join-Path $Root 'logs'
$Backups = Join-Path $Root 'backups'
$Config = Join-Path $Root 'config'
$TaskName = 'GoA Hub'
$HealthUrl = 'http://127.0.0.1:8080/api/v1/health'

function Say($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Note($text) { Write-Host "    $text" }

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this in PowerShell opened with "Run as administrator".'
}
if (-not $Zip -and -not (Test-Path "$Current\bin\goa-hub.bat")) {
    throw 'Usage: .\install.ps1 -Zip goa-hub-x.y.z.zip   (build it with: .\gradlew.bat :server:hub:distZip)'
}
if ($Zip) { $Zip = (Resolve-Path -LiteralPath $Zip).Path }

# Folder permissions: C:\ lets every signed-in user change new subfolders, and SYSTEM runs what is
# in C:\goa-hub, so lock it down. Secrets (data, backups, config) are SYSTEM + Administrators only.
function Protect-Folder($path, [switch]$Private) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    $grants = @('*S-1-5-18:(OI)(CI)F', '*S-1-5-32-544:(OI)(CI)F')
    if (-not $Private) { $grants += '*S-1-5-32-545:(OI)(CI)RX' }
    icacls $path /inheritance:r /grant:r @grants /Q | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "icacls failed on $path" }
}

# --- Questions ---------------------------------------------------------------------------------
if ($WithCaddy) {
    if (-not $Domain) { $Domain = Read-Host 'Your DuckDNS name (e.g. ourgroup, for ourgroup.duckdns.org)' }
    $Domain = ($Domain.ToLower() -replace '^https?://', '' -replace '/.*$', '' -replace '\.duckdns\.org$', '').Trim()
    if ($Domain -notmatch '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$') { throw "'$Domain' is not a DuckDNS name." }
    if (-not $DuckDnsToken) {
        $envFile = Join-Path $Config 'duckdns.env'
        if (Test-Path $envFile) {
            $DuckDnsToken = ((Get-Content $envFile) -match '^DUCKDNS_TOKEN=' | Select-Object -Last 1) -replace '^DUCKDNS_TOKEN=', ''
        }
    }
    if (-not $DuckDnsToken) { $DuckDnsToken = Read-Host 'Your DuckDNS token (top of duckdns.org after signing in)' }
    $DuckDnsToken = $DuckDnsToken.Trim()
    if ($DuckDnsToken -notmatch '^[A-Za-z0-9-]+$') { throw 'That does not look like a DuckDNS token.' }
}
if (-not $PSBoundParameters.ContainsKey('Invite')) {
    Write-Host "`nJoin the hub to your group now? On an Android phone in the group: Settings -> Group sync ->"
    Write-Host 'Invite a friend -> Share as text, send it to yourself, and paste the last line here'
    Write-Host '(it starts with goa-sync: or https://). Press Enter to skip if the hub already joined.'
    $Invite = Read-Host 'Invite'
}

# --- Java 21 -----------------------------------------------------------------------------------
function Find-JavaHome {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += Get-ChildItem 'C:\Program Files\Microsoft\jdk-*', 'C:\Program Files\Eclipse Adoptium\*', 'C:\Program Files\Java\*' `
        -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName
    foreach ($candidate in $candidates) {
        $release = Join-Path $candidate 'release'
        if ((Test-Path (Join-Path $candidate 'bin\java.exe')) -and (Test-Path $release)) {
            $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
            if ($line -and [int]$line.Matches[0].Groups[1].Value -ge 21) { return $candidate }
        }
    }
    return $null
}

Say 'Java'
$javaHome = Find-JavaHome
if (-not $javaHome) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw 'Java 21 is missing and winget is not available. Install "Microsoft Build of OpenJDK 21" from https://learn.microsoft.com/java/openjdk/download and run this again.'
    }
    winget install --id Microsoft.OpenJDK.21 --exact --silent --accept-package-agreements --accept-source-agreements
    $javaHome = Find-JavaHome
    if (-not $javaHome) { throw 'Java 21 was installed but not found. Run this script again in a new PowerShell window.' }
}
$env:JAVA_HOME = $javaHome
Note "Java: $javaHome"

# --- Folders and the hub -------------------------------------------------------------------------
Say 'Folders'
Protect-Folder $Root
Protect-Folder $Logs
Protect-Folder $Data -Private
Protect-Folder $Backups -Private
Protect-Folder $Config -Private
New-Item -ItemType Directory -Force -Path (Join-Path $Root 'releases'), (Join-Path $Data 'app') | Out-Null
if ($Kit -ne (Join-Path $Root 'deploy')) {
    Remove-Item -Recurse -Force (Join-Path $Root 'deploy') -ErrorAction SilentlyContinue
    Copy-Item -Recurse -Path $Kit -Destination (Join-Path $Root 'deploy')
}
Note "$Root (the hub), $Data (its data; SYSTEM and Administrators only)"

$runHub = @"
@echo off
rem Started at boot by the "GoA Hub" scheduled task (install.ps1). The hub listens on this PC only.
set "JAVA_HOME=$javaHome"
set "GOA_HUB_DATA=$Data"
set "JAVA_OPTS=-Xms32m -Xmx384m -XX:+ExitOnOutOfMemoryError"
if exist "$Logs\hub.log" for %%F in ("$Logs\hub.log") do if %%~zF GTR 10485760 move /y "$Logs\hub.log" "$Logs\hub.log.1" >nul
call "$Current\bin\goa-hub.bat" serve --data "$Data" --host 127.0.0.1 --port 8080 >> "$Logs\hub.log" 2>&1
"@
Set-Content -Path (Join-Path $Root 'run-hub.cmd') -Value $runHub -Encoding Ascii

Say "Scheduled task '$TaskName'"
$always = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -MultipleInstances IgnoreNew
$system = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $TaskName -Force -Principal $system -Settings $always `
    -Trigger (New-ScheduledTaskTrigger -AtStartup) `
    -Action (New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$Root\run-hub.cmd`"") | Out-Null
$backupSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries
Register-ScheduledTask -TaskName 'GoA Hub backup' -Force -Principal $system -Settings $backupSettings `
    -Trigger (New-ScheduledTaskTrigger -Daily -At 4am) `
    -Action (New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$Root\deploy\windows\backup.ps1`"") | Out-Null

if ($Zip) {
    Say "Installing the hub from $(Split-Path $Zip -Leaf)"
    & (Join-Path $PSScriptRoot 'update.ps1') -Path $Zip
}

function Test-Healthy {
    for ($i = 0; $i -lt 45; $i++) {
        try { if ((Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3).Content -match '"status"\s*:\s*"ok"') { return $true } } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

# Runs the hub's command line. Every argument is quoted for cmd.exe. Never pass an invite here:
# cmd.exe splits it at '&' whatever the quoting (goa-hub.bat re-expands its arguments); use Join-Hub.
function Invoke-Hub([string[]]$arguments) {
    $quoted = ($arguments | ForEach-Object { '"' + ($_ -replace '"', '') + '"' }) -join ' '
    $p = Start-Process -FilePath 'cmd.exe' -ArgumentList "/d /c `"`"$Current\bin\goa-hub.bat`" $quoted`"" `
        -NoNewWindow -Wait -PassThru
    if ($p.ExitCode -ne 0) { throw "goa-hub $($arguments[0]) failed (exit $($p.ExitCode))." }
}

# Joins the group: the invite goes in on standard input (goa-hub join reads it there when no text
# is given), so cmd.exe never sees its '&' and it never shows in a process list.
function Join-Hub([string]$invite) {
    $invite | & "$Current\bin\goa-hub.bat" join --data $Data
    if ($LASTEXITCODE -ne 0) { throw "goa-hub join failed (exit $LASTEXITCODE)." }
}

function Get-TailscaleStatus { (& $ts status --json) -join "`n" | ConvertFrom-Json }

# --- Publishing: Tailscale Funnel (default) ----------------------------------------------------
if (-not $WithCaddy) {
    Say 'Tailscale'
    $ts = 'C:\Program Files\Tailscale\tailscale.exe'
    if (-not (Test-Path $ts)) {
        winget install --id Tailscale.Tailscale --exact --silent --accept-package-agreements --accept-source-agreements
        if (-not (Test-Path $ts)) { throw 'Tailscale did not install. Get it from https://tailscale.com/download/windows and run this again.' }
    }
    $state = (Get-TailscaleStatus).BackendState
    if ($state -ne 'Running') {
        Note 'Log this PC in to your tailnet: a browser window opens (or follow the link below).'
        & $ts up
        $state = (Get-TailscaleStatus).BackendState
        if ($state -ne 'Running') { throw 'Tailscale is not connected. Log in from the Tailscale icon, then run this again.' }
    }
    # Keep running when nobody is signed in to Windows (after a reboot, for instance).
    & $ts set --unattended=true
    & $ts set --auto-update=true
    if ($TailscaleHostname) { & $ts set --hostname=$TailscaleHostname }
    $tsName = ((Get-TailscaleStatus).Self.DNSName).TrimEnd('.')
    if ($tsName -notlike '*.ts.net') { throw "Could not read this PC's tailnet name ('$tsName'). Is MagicDNS on? See deploy\README.md." }
    $PublicUrl = "https://$tsName"
} else {
    $PublicUrl = "https://$Domain.duckdns.org"
}

# --- Join and configure ------------------------------------------------------------------------
Say "Configuring the hub for $PublicUrl"
Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like "*$Root*" } |
    ForEach-Object { Invoke-CimMethod -InputObject $_ -MethodName Terminate | Out-Null }
if ($Invite) {
    try { Join-Hub $Invite.Trim(); Note 'Joined the group.' }
    catch { Write-Warning "Joining failed: $($_.Exception.Message). Run this again with a fresh invite." }
}
$configArgs = @('config', '--public-url', $PublicUrl, '--data', $Data)
if ($AndroidCertSha256) { $configArgs += @('--android-cert-sha256', $AndroidCertSha256) }
Invoke-Hub $configArgs
Start-ScheduledTask -TaskName $TaskName
if (-not (Test-Healthy)) { throw "The hub did not answer on $HealthUrl. See $Logs\hub.log" }
Note "The hub answers on $HealthUrl (this PC only)."

if (-not $WithCaddy) {
    Say 'Publishing the hub with Tailscale Funnel'
    Note 'If Funnel is not allowed for this PC yet, Tailscale prints a link: open it, approve,'
    Note 'and this continues by itself. (Or set it up first: deploy\README.md, step 3.)'
    # --bg: Tailscale keeps this setting and restores it after restarts.
    & $ts funnel --bg --https=443 http://127.0.0.1:8080
    if ($LASTEXITCODE -ne 0) { throw 'tailscale funnel failed (see above).' }
    & $ts funnel status
}

# --- Alternative: Caddy + DuckDNS --------------------------------------------------------------
if ($WithCaddy) {
    Say "DuckDNS for $Domain.duckdns.org"
    $envFile = Join-Path $Config 'duckdns.env'
    Set-Content -Path $envFile -Encoding Ascii -Value "DUCKDNS_DOMAIN=$Domain", "DUCKDNS_TOKEN=$DuckDnsToken"
    $every5 = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 5)
    Register-ScheduledTask -TaskName 'GoA DuckDNS' -Force -Principal $system -Settings $backupSettings -Trigger $every5, (New-ScheduledTaskTrigger -AtStartup) `
        -Action (New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$Root\deploy\windows\alt-caddy\duckdns-update.ps1`"") | Out-Null
    Start-ScheduledTask -TaskName 'GoA DuckDNS'
    Start-Sleep -Seconds 10
    Get-Content (Join-Path $Logs 'duckdns.log') -ErrorAction SilentlyContinue

    Say 'Caddy'
    $caddyDir = Join-Path $Root 'caddy'
    New-Item -ItemType Directory -Force -Path $caddyDir | Out-Null
    $caddyExe = Join-Path $caddyDir 'caddy.exe'
    if (-not (Test-Path $caddyExe)) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $release = Invoke-RestMethod 'https://api.github.com/repos/caddyserver/caddy/releases/latest'
        $asset = $release.assets | Where-Object { $_.name -like 'caddy_*_windows_amd64.zip' } | Select-Object -First 1
        $sums = $release.assets | Where-Object { $_.name -like 'caddy_*_checksums.txt' } | Select-Object -First 1
        $download = Join-Path $env:TEMP $asset.name
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $download -UseBasicParsing
        $expected = ((Invoke-WebRequest -Uri $sums.browser_download_url -UseBasicParsing).Content -split "`n" |
            Where-Object { $_ -like "*  $($asset.name)" }) -replace '\s.*$', ''
        if ((Get-FileHash $download -Algorithm SHA512).Hash -ne $expected.ToUpper()) { throw 'The Caddy download does not match its checksum.' }
        Expand-Archive -LiteralPath $download -DestinationPath $caddyDir -Force
        Remove-Item $download
    }
    $caddyfile = Join-Path $caddyDir 'Caddyfile'
    (Get-Content (Join-Path $Kit 'linux\alt-caddy\Caddyfile') -Raw).
        Replace('{$GOA_HUB_DOMAIN}', "$Domain.duckdns.org").
        Replace('{$GOA_HUB_ACCESS_LOG}', 'C:/goa-hub/logs/caddy-access.log') |
        Set-Content -Path $caddyfile -Encoding Ascii
    & $caddyExe validate --config $caddyfile --adapter caddyfile
    if ($LASTEXITCODE -ne 0) { throw "Caddy does not accept $caddyfile (see above)." }
    if (-not (Get-Service goa-caddy -ErrorAction SilentlyContinue)) {
        New-Service -Name goa-caddy -DisplayName 'GoA hub - Caddy' -StartupType Automatic `
            -BinaryPathName "`"$caddyExe`" run --config `"$caddyfile`" --adapter caddyfile" | Out-Null
        sc.exe failure goa-caddy reset= 86400 actions= restart/5000/restart/5000/restart/5000 | Out-Null
    }
    Restart-Service goa-caddy

    Say 'Windows Firewall'
    Get-NetFirewallRule -DisplayName 'GoA hub - Caddy*' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName 'GoA hub - Caddy (TCP 80, 443)' -Direction Inbound -Action Allow `
        -Protocol TCP -LocalPort 80, 443 -Program $caddyExe | Out-Null
    Note 'Opened TCP 80 and 443 for Caddy only.'
}

# --- Power --------------------------------------------------------------------------------------
Say 'Power: no sleep or hibernate on mains power'
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0

Write-Host @"

=====================================================================================
 The hub is running and published at:

     $PublicUrl

 Check it on a phone using mobile data (Tailscale off on that phone):
     $PublicUrl/api/v1/health   ->  {"status":"ok"}
 Then on Android: Settings -> Group sync -> Hub: $PublicUrl

 Log:      $Logs\hub.log
 Update:   .\update.ps1 goa-hub-x.y.z.zip     New APK:  .\update.ps1 app-release.apk
 (both in $Root\deploy\windows, in PowerShell as Administrator)
=====================================================================================
"@
