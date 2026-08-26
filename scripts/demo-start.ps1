$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"

function Write-Step($Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Wait-Http {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest `
                -Uri $Url `
                -UseBasicParsing `
                -TimeoutSec 3

            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Host "[OK] $Name" -ForegroundColor Green
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name health check failed: $Url"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Blue
Write-Host " SafeSight Demo Runtime" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue

# ---------------------------------------------------------
# Repo / env
# ---------------------------------------------------------

if (-not (Test-Path $EnvFile)) {
    throw ".env not found: $EnvFile"
}

Set-Location $Root

Write-Step "Loading .env"
. "$Root\scripts\import-env.ps1" -EnvFile $EnvFile

# ---------------------------------------------------------
# Tailscale
# ---------------------------------------------------------

Write-Step "Checking Tailscale"

$tailscale = Get-Command tailscale -ErrorAction SilentlyContinue

if ($tailscale) {
    try {
        $tailscaleIp = (tailscale ip -4 | Select-Object -First 1).Trim()

        if ($tailscaleIp) {
            Write-Host "[OK] Tailscale IP: $tailscaleIp" -ForegroundColor Green

            if ($env:MINIO_PUBLIC_ENDPOINT -and
                $env:MINIO_PUBLIC_ENDPOINT -notmatch [regex]::Escape($tailscaleIp)) {

                Write-Warning "MINIO_PUBLIC_ENDPOINT does not contain current Tailscale IP."
                Write-Warning "Current IP: $tailscaleIp"
            }
        }
    }
    catch {
        Write-Warning "Tailscale command exists but IP could not be read."
    }
}
else {
    Write-Warning "tailscale command not found in PATH."
}

# ---------------------------------------------------------
# Docker
# ---------------------------------------------------------

Write-Step "Checking Docker"

try {
    docker info *> $null
}
catch {
    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"

    if (-not (Test-Path $dockerDesktop)) {
        throw "Docker Desktop is not running and executable was not found."
    }

    Write-Host "Starting Docker Desktop..."
    Start-Process $dockerDesktop

    $dockerDeadline = (Get-Date).AddSeconds(90)

    while ((Get-Date) -lt $dockerDeadline) {
        Start-Sleep -Seconds 3

        try {
            docker info *> $null
            break
        }
        catch {}
    }

    try {
        docker info *> $null
    }
    catch {
        throw "Docker Desktop did not become ready."
    }
}

Write-Host "[OK] Docker" -ForegroundColor Green

# ---------------------------------------------------------
# MinIO
# ---------------------------------------------------------

Write-Step "Starting MinIO"

$minioExists = docker ps -a `
    --filter "name=^/isg-minio$" `
    --format "{{.Names}}"

if ($minioExists -eq "isg-minio") {
    docker start isg-minio *> $null
}
else {
    docker compose up -d minio minio-init
}

Wait-Http `
    -Name "MinIO" `
    -Url "http://localhost:9000/minio/health/live"

# ---------------------------------------------------------
# AI
# ---------------------------------------------------------

Write-Step "Starting AI Worker"

docker compose up -d ai-service

Wait-Http `
    -Name "AI Worker" `
    -Url "http://localhost:8001/health" `
    -TimeoutSeconds 90

# ---------------------------------------------------------
# Backend
# ---------------------------------------------------------

Write-Step "Starting Backend"

$backendCommand = @"
Set-Location '$Root'
. '$Root\scripts\import-env.ps1' -EnvFile '$EnvFile'
& '$Root\backend\mvnw.cmd' -f '$Root\backend\pom.xml' spring-boot:run
"@

Start-Process powershell.exe `
    -ArgumentList "-NoExit", "-Command", $backendCommand

Wait-Http `
    -Name "Backend" `
    -Url "http://localhost:8080/actuator/health" `
    -TimeoutSeconds 90

# ---------------------------------------------------------
# Gateway
# ---------------------------------------------------------

Write-Step "Starting Gateway"

$gatewayCommand = @"
Set-Location '$Root'
. '$Root\scripts\import-env.ps1' -EnvFile '$EnvFile'
Set-Location '$Root\gateway'
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
"@

Start-Process powershell.exe `
    -ArgumentList "-NoExit", "-Command", $gatewayCommand

Wait-Http `
    -Name "Gateway" `
    -Url "http://localhost:8000/health" `
    -TimeoutSeconds 60

# ---------------------------------------------------------
# Web
# ---------------------------------------------------------

Write-Step "Starting Web"

$webCommand = @"
Set-Location '$Root\web'
npm run dev
"@

Start-Process powershell.exe `
    -ArgumentList "-NoExit", "-Command", $webCommand

Wait-Http `
    -Name "Web Dashboard" `
    -Url "http://localhost:5173" `
    -TimeoutSeconds 60

# ---------------------------------------------------------
# Tailscale-facing checks
# ---------------------------------------------------------

if ($tailscaleIp) {
    Write-Step "Checking wireless demo endpoints"

    Wait-Http `
        -Name "Backend via Tailscale" `
        -Url "http://${tailscaleIp}:8080/actuator/health"

    Wait-Http `
        -Name "Gateway via Tailscale" `
        -Url "http://${tailscaleIp}:8000/health"

    Wait-Http `
        -Name "MinIO via Tailscale" `
        -Url "http://${tailscaleIp}:9000/minio/health/live"
}

# ---------------------------------------------------------
# Done
# ---------------------------------------------------------

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " SafeSight demo environment is READY" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Web:      http://localhost:5173"
Write-Host "Backend:  http://localhost:8080"
Write-Host "Gateway:  http://localhost:8000"
Write-Host "AI:       http://localhost:8001"
Write-Host "MinIO:    http://localhost:9000"

if ($tailscaleIp) {
    Write-Host ""
    Write-Host "Phone/Tailscale target: $tailscaleIp"
}

Start-Process "http://localhost:5173"

Write-Host ""
Write-Host "Onur can now open SafeSight without USB." -ForegroundColor Yellow