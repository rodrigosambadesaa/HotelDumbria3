[CmdletBinding()]
param(
    [ValidateSet("Start", "Stop", "Status")]
    [string]$Action = "Start"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$drive = $repo.Substring(0, 1).ToLowerInvariant()
$linuxRepo = "/mnt/$drive/" + $repo.Substring(3).Replace("\", "/")
if (-not $linuxRepo) { throw "No se pudo resolver el repositorio dentro de WSL." }

$compose = "cd '$linuxRepo' && /usr/bin/docker compose --profile backend"
switch ($Action) {
    "Start" {
        $keepAlive = Get-CimInstance Win32_Process | Where-Object {
            $_.Name -eq "wsl.exe" -and $_.CommandLine -like "*hotel-dumbria-keepalive*"
        }
        if (-not $keepAlive) {
            Start-Process -FilePath "$env:SystemRoot\System32\wsl.exe" `
                -ArgumentList @("-d", "Ubuntu", "--", "bash", "-lc", "`"exec -a hotel-dumbria-keepalive sleep infinity`"") `
                -WindowStyle Hidden
            Start-Sleep -Seconds 2
        }
        wsl -d Ubuntu -- bash -lc "$compose up -d postgres mailpit api admin-web"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar el backend." }
        Write-Host "API:        http://localhost:8080"
        Write-Host "Panel:      http://localhost:3000"
        Write-Host "Correos:    http://localhost:8025"
    }
    "Stop" {
        wsl -d Ubuntu -- bash -lc "$compose down"
    }
    "Status" {
        wsl -d Ubuntu -- bash -lc "$compose ps"
    }
}
