
# ==========================================
# DRIVEGUARD IOT - LOCAL DEVELOPMENT RUNNER
# ==========================================

$ErrorActionPreference = "Stop"

# Thu muc Backend
Set-Location $PSScriptRoot

# File .env cua du an DriveGuard
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

if (-not (Test-Path $envFile)) {
    throw "Khong tim thay file .env: $envFile"
}

# Doc gia tri theo ten bien, khong hien thi mat khau
function Get-EnvValue {
    param([string]$Name)

    $line = Get-Content $envFile |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1

    if (-not $line) {
        throw "Thieu bien $Name trong file .env"
    }

    $value = $line.Substring($Name.Length + 1).Trim()

    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Bien $Name dang rong"
    }

    return $value
}

# Cau hinh rieng cho DriveGuard
$dbName = Get-EnvValue "MARIADB_DATABASE"
$dbUser = Get-EnvValue "MARIADB_USER"

$env:DB_URL = "jdbc:mariadb://localhost:3308/$dbName"
$env:DB_USERNAME = $dbUser
$env:DB_PASSWORD = Get-EnvValue "MARIADB_PASSWORD"
$env:JWT_SECRET = Get-EnvValue "JWT_SECRET"

Write-Host "======================================"
Write-Host " DRIVEGUARD IOT - SPRING BOOT"
Write-Host "======================================"
Write-Host "DB_URL: $env:DB_URL"
Write-Host "DB_USERNAME: $env:DB_USERNAME"
Write-Host "DB_PASSWORD: DA NAP"
Write-Host "JWT_SECRET: DA NAP"
Write-Host "BACKEND PORT: 8081"
Write-Host "======================================"

# Khoi dong Spring Boot
& "$PSScriptRoot\gradlew.bat" bootRun
