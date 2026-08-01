[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot ".env"

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    Write-Host ".env ya existe. No se ha sobrescrito."
    exit 0
}

function New-RandomSecret([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer).Replace("+", "-").Replace("/", "_").TrimEnd("=")
}

$databasePassword = New-RandomSecret 32
$legacyPassword = New-RandomSecret 32
$legacyRootPassword = New-RandomSecret 32
$jwtSecret = New-RandomSecret 48
$auditHmacSecret = New-RandomSecret 48
$adminPassword = "$(New-RandomSecret 18)!aA7"

$content = @"
POSTGRES_DB=hotel_dumbria
POSTGRES_USER=hotel_app
POSTGRES_PASSWORD=$databasePassword
LEGACY_MYSQL_PASSWORD=$legacyPassword
LEGACY_MYSQL_ROOT_PASSWORD=$legacyRootPassword
JWT_SECRET=$jwtSecret
AUDIT_HMAC_SECRET=$auditHmacSecret
ADMIN_BOOTSTRAP_EMAIL=admin@hotel.local
ADMIN_BOOTSTRAP_PASSWORD=$adminPassword
PUBLIC_BASE_URL=http://localhost:8080
ADMIN_WEB_ORIGIN=http://localhost:3000
CORS_ALLOWED_ORIGINS=http://localhost:3000
TRUSTED_PROXIES=
COOKIE_SECURE=false
SMTP_USER=
SMTP_PASSWORD=
MAIL_FROM=reservas@hotel.local
MAIL_FROM_NAME=Hotel Dumbría
NODE_ENV=development
PORT=8080
ACCESS_TOKEN_MINUTES=15
REFRESH_TOKEN_DAYS=30
HOTEL_TIME_ZONE=Europe/Madrid
MAX_RESERVATION_NIGHTS=30
BOOKING_HORIZON_MONTHS=18
CANCELLATION_NOTICE_HOURS=48
CHECK_IN_HOUR_LOCAL=15
LOGIN_FAILURE_THRESHOLD=5
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCK_MINUTES=15
OUTBOX_BATCH_SIZE=20
OUTBOX_MAX_ATTEMPTS=6
SHUTDOWN_TIMEOUT_MS=10000
LOG_LEVEL=info
"@

[System.IO.File]::WriteAllText($envPath, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Configuración local creada en .env"
Write-Host "Usuario administrador: admin@hotel.local"
Write-Host "Contraseña administrador: $adminPassword"
Write-Host "Guárdala ahora; el archivo .env no se incorpora a Git."
