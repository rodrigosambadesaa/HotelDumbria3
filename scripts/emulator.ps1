[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(16, 29, 36)]
    [int]$Api,
    [switch]$NoInstall,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$drive = $repo.Substring(0, 1).ToLowerInvariant()
$linuxRepo = "/mnt/$drive/" + $repo.Substring(3).Replace("\", "/")
if (-not $linuxRepo) { throw "No se pudo resolver el repositorio dentro de WSL." }

$service = "emulator-api$Api"
$allEmulators = @("emulator-api16", "emulator-api29", "emulator-api36")
$compose = "cd '$linuxRepo' && /usr/bin/docker compose"

$keepAlive = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq "wsl.exe" -and $_.CommandLine -like "*hotel-dumbria-keepalive*"
}
if (-not $keepAlive) {
    Start-Process -FilePath "$env:SystemRoot\System32\wsl.exe" `
        -ArgumentList @("-d", "Ubuntu", "--", "bash", "-lc", "`"exec -a hotel-dumbria-keepalive sleep infinity`"") `
        -WindowStyle Hidden
    Start-Sleep -Seconds 2
}

$running = wsl -d Ubuntu -- bash -lc "$compose ps --services --filter status=running"
$other = $allEmulators | Where-Object { $_ -ne $service -and $running -contains $_ }
if ($other) {
    throw "Ya está activo $($other -join ', '). Deténlo antes con scripts/stop-emulator.ps1."
}

wsl -d Ubuntu -- bash -lc "$compose --profile api$Api up -d --build $service"
if ($LASTEXITCODE -ne 0) { throw "No se pudo arrancar $service." }

Write-Host "Esperando a que Android API $Api termine de arrancar..."
wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb wait-for-device"
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $bootOutput = wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb shell getprop sys.boot_completed 2>/dev/null"
    $booted = if ($null -eq $bootOutput) { "" } else { ($bootOutput | Out-String).Trim() }
    if ($booted -eq "1") { break }
    Start-Sleep -Seconds 3
}
if ($booted -ne "1") { throw "El emulador no terminó de arrancar en seis minutos." }

$screenUrl = "http://localhost:6080/vnc.html?autoconnect=1&resize=scale"
if (-not $NoBrowser) {
    Start-Process $screenUrl
}

if (-not $NoInstall) {
    $apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "No existe $apk. Ejecuta primero docker compose run --rm android-build."
    }
    wsl -d Ubuntu -- bash -lc "$compose cp app/build/outputs/apk/debug/app-debug.apk ${service}:/tmp/app.apk"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el APK al emulador." }

    $installOutput = (wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb install -r /tmp/app.apk" 2>&1) | Out-String
    Write-Host $installOutput.Trim()
    if ($installOutput -match "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES|INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "La firma debug cambió; se reinstalará la app conservando el AVD."
        wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb uninstall org.hoteldumbria >/dev/null 2>&1 || true"
        $installOutput = (wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb install /tmp/app.apk" 2>&1) | Out-String
        Write-Host $installOutput.Trim()
    }
    if ($installOutput -notmatch "(?m)^Success\s*$") {
        throw "El APK no se pudo instalar: $($installOutput.Trim())"
    }

    wsl -d Ubuntu -- bash -lc "$compose exec -T $service adb shell am start -n org.hoteldumbria/.ActivityLoginRegistro"
    if ($LASTEXITCODE -ne 0) { throw "La actividad de login no se pudo iniciar." }
}

Write-Host "Android API $Api está listo y visible en $screenUrl"
Write-Host "No inicies otro emulador simultáneamente."
