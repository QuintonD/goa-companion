<#
.SYNOPSIS
  Alternative setup only (Caddy + DuckDNS): tells DuckDNS this line's public IPv4 address so
  <name>.duckdns.org keeps pointing home. Run every 5 minutes by the "GoA DuckDNS" scheduled task (install.ps1 -WithCaddy).
  Settings in C:\goa-hub\config\duckdns.env (SYSTEM and Administrators only):
    DUCKDNS_DOMAIN=ourgroup
    DUCKDNS_TOKEN=xxxxxxxx-xxxx-...
  The last result is in C:\goa-hub\logs\duckdns.log.
#>
$ErrorActionPreference = 'Stop'
$EnvFile = 'C:\goa-hub\config\duckdns.env'
$Log = 'C:\goa-hub\logs\duckdns.log'

$settings = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^\s*(DUCKDNS_[A-Z]+)\s*=\s*"?([^"]*)"?\s*$') { $settings[$Matches[1]] = $Matches[2] }
}
$domain = $settings['DUCKDNS_DOMAIN']
$token = $settings['DUCKDNS_TOKEN']
if ($domain -notmatch '^[a-z0-9-]+$' -or $token -notmatch '^[A-Za-z0-9-]+$') {
    throw "DUCKDNS_DOMAIN / DUCKDNS_TOKEN missing or malformed in $EnvFile"
}

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
# An empty ip= lets DuckDNS use the address this request comes from (IPv4).
$url = "https://www.duckdns.org/update?domains=$domain&token=$token&ip=&verbose=true"
$stamp = Get-Date -Format 's'
try {
    $answer = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30).Content -split "`n"
} catch {
    "$stamp duckdns: could not reach www.duckdns.org ($($_.Exception.Message))" | Set-Content -LiteralPath $Log
    exit 1
}
if ($answer[0].Trim() -ne 'OK') {
    "$stamp duckdns: update refused ($($answer[0].Trim())): check the name and the token in $EnvFile" | Set-Content -LiteralPath $Log
    exit 1
}
"$stamp duckdns: $domain.duckdns.org -> $($answer[1].Trim()) ($($answer[3].Trim()))" | Set-Content -LiteralPath $Log
