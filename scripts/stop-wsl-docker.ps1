$ErrorActionPreference = "Stop"

& "$PSScriptRoot\stop-emulator.ps1"
& "$PSScriptRoot\backend.ps1" -Action Stop

$keepAlive = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq "wsl.exe" -and $_.CommandLine -like "*hotel-dumbria-keepalive*"
}
foreach ($process in $keepAlive) {
    Stop-Process -Id $process.ProcessId
}

Write-Host "Servicios detenidos. Ubuntu podrá apagarse cuando quede inactiva."
