$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$drive = $repo.Substring(0, 1).ToLowerInvariant()
$linuxRepo = "/mnt/$drive/" + $repo.Substring(3).Replace("\", "/")
wsl -d Ubuntu -- bash -lc "cd '$linuxRepo' && /usr/bin/docker compose stop emulator-api16 emulator-api29 emulator-api36"
Write-Host "Emuladores detenidos. Los volúmenes AVD se conservan."
