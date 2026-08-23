###çalıştırmak için    powershell -ExecutionPolicy Bypass -File .\mvp-smoke.ps1                   #####



$ErrorActionPreference = "Stop"

$Root    = $PSScriptRoot
$Backend = "http://localhost:8080"
$Gateway = "http://localhost:8000"
$AI      = "http://localhost:8001"

$SessionToken = "dev-session-token"
$Image = "$Root\test-images\welding-test1.jpeg"

$PassCount = 0
$FailCount = 0
$WarnCount = 0

$Opened = $false
$Before = $null

function PASS($msg) {
    $script:PassCount++
    Write-Host "[PASS] $msg" -ForegroundColor Green
}

function FAIL($msg) {
    $script:FailCount++
    Write-Host "[FAIL] $msg" -ForegroundColor Red
}

function WARN($msg) {
    $script:WarnCount++
    Write-Host "[WARN] $msg" -ForegroundColor Yellow
}

function TITLE($msg) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host $msg -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
}

function ConvertFrom-JdbcPostgresqlUrl([string]$JdbcUrl) {
    if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
        throw "SPRING_DATASOURCE_URL is missing"
    }

    if (-not $JdbcUrl.StartsWith("jdbc:", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "SPRING_DATASOURCE_URL must start with jdbc:"
    }

    $uriText = $JdbcUrl.Substring(5)

    try {
        $uri = [Uri]$uriText
    }
    catch {
        throw "SPRING_DATASOURCE_URL is not a valid PostgreSQL JDBC URL"
    }

    if ([string]::IsNullOrWhiteSpace($uri.Host)) {
        throw "SPRING_DATASOURCE_URL host is missing"
    }

    $port = $uri.Port
    if ($port -le 0) {
        $port = 5432
    }

    $database = $uri.AbsolutePath.Trim().TrimStart('/')
    if ($database.Contains('/')) {
        $database = $database.Split('/')[0]
    }

    if ([string]::IsNullOrWhiteSpace($database)) {
        throw "SPRING_DATASOURCE_URL database name is missing"
    }

    $sslMode = $null
    $query = $uri.Query.TrimStart('?')

    if (-not [string]::IsNullOrWhiteSpace($query)) {
        foreach ($pair in $query.Split('&')) {
            if ([string]::IsNullOrWhiteSpace($pair)) {
                continue
            }

            $separatorIndex = $pair.IndexOf('=')
            if ($separatorIndex -lt 1) {
                continue
            }

            $name = [Uri]::UnescapeDataString(
                $pair.Substring(0, $separatorIndex)
            )

            if ($name -ne 'sslmode') {
                continue
            }

            $sslMode = [Uri]::UnescapeDataString(
                $pair.Substring($separatorIndex + 1)
            )
        }
    }

    if ([string]::IsNullOrWhiteSpace($sslMode)) {
        $sslMode = 'require'
    }

    return [pscustomobject]@{
        Host     = $uri.Host
        Port     = $port
        Database = $database
        SslMode  = $sslMode
    }
}

function Initialize-SmokeDatabase {
    $envFile = Join-Path $Root '.env'
    $importScript = Join-Path $Root 'scripts\import-env.ps1'

    if (-not (Test-Path -LiteralPath $envFile)) {
        throw ".env not found at $envFile"
    }

    if (-not (Test-Path -LiteralPath $importScript)) {
        throw "import-env.ps1 not found at $importScript"
    }

    & $importScript -EnvFile $envFile

    $jdbcUrl = [Environment]::GetEnvironmentVariable(
        'SPRING_DATASOURCE_URL',
        'Process'
    )
    $username = [Environment]::GetEnvironmentVariable(
        'SPRING_DATASOURCE_USERNAME',
        'Process'
    )
    $password = [Environment]::GetEnvironmentVariable(
        'SPRING_DATASOURCE_PASSWORD',
        'Process'
    )

    if ([string]::IsNullOrWhiteSpace($username)) {
        throw "SPRING_DATASOURCE_USERNAME is missing"
    }

    if ([string]::IsNullOrEmpty($password)) {
        throw "SPRING_DATASOURCE_PASSWORD is missing"
    }

    $parsed = ConvertFrom-JdbcPostgresqlUrl $jdbcUrl

    $script:DbHost = $parsed.Host
    $script:DbPort = [string]$parsed.Port
    $script:DbName = $parsed.Database
    $script:DbUser = $username
    $script:DbPassword = $password
    $script:DbSslMode = $parsed.SslMode

    $nativePsql = Get-Command psql -CommandType Application -ErrorAction SilentlyContinue
    if ($null -ne $nativePsql) {
        $script:PsqlMode = 'native'
        $script:PsqlExecutable = $nativePsql.Source
        return
    }

    if ($null -eq (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
        throw "psql is not on PATH and docker is unavailable"
    }

    $script:PsqlMode = 'docker'
}

function Invoke-SmokePsql {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql,

        [switch]$TuplesOnly
    )

    $psqlArgs = @(
        '-h', $script:DbHost
        '-p', $script:DbPort
        '-U', $script:DbUser
        '-d', $script:DbName
        '-w'
    )

    if ($TuplesOnly) {
        $psqlArgs += @(
            '-t'
            '-A'
            '-F'
            '|'
        )
    }

    $psqlArgs += @(
        '-c'
        $Sql
    )

    $previousPassword = $env:PGPASSWORD
    $previousSslMode = $env:PGSSLMODE

    try {
        $env:PGPASSWORD = $script:DbPassword
        $env:PGSSLMODE = $script:DbSslMode

        if ($script:PsqlMode -eq 'native') {
            $output = & $script:PsqlExecutable @psqlArgs
        }
        else {
            $output = & docker run --rm `
                -e PGPASSWORD `
                -e PGSSLMODE `
                postgres:16 `
                psql @psqlArgs
        }

        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL query failed"
        }

        return $output
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:PGPASSWORD = $previousPassword
        }

        if ($null -eq $previousSslMode) {
            Remove-Item Env:PGSSLMODE -ErrorAction SilentlyContinue
        }
        else {
            $env:PGSSLMODE = $previousSslMode
        }
    }
}

function PSQL([string]$Sql) {
    $lines = @(
        Invoke-SmokePsql -Sql $Sql -TuplesOnly
    )

    $text = [string]::Join(
        "`n",
        $lines
    )

    return $text.Trim()
}

function PSQLDisplay([string]$Sql) {
    $output = Invoke-SmokePsql -Sql $Sql

    foreach ($line in @($output)) {
        Write-Host $line
    }
}


Set-Location $Root
Initialize-SmokeDatabase


# ============================================================
# 0. CAMERA + SESSION
# ============================================================

$CameraId = PSQL @"
SELECT c.id
FROM cameras c
WHERE c.active = true
  AND NOT EXISTS (
      SELECT 1
      FROM camera_sessions cs
      WHERE cs.camera_id = c.id
        AND cs.status = 'ACTIVE'
  )
ORDER BY c.id
LIMIT 1;
"@

if ([string]::IsNullOrWhiteSpace($CameraId)) {
    FAIL "ACTIVE session'i olmayan aktif kamera bulunamadi"
    exit 1
}

$SessionId = [guid]::NewGuid().ToString()

$OpenBody = @{
    cameraId     = $CameraId
    sessionId    = $SessionId
    sessionToken = $SessionToken
} | ConvertTo-Json

$ActionBody = @{
    cameraId = $CameraId
} | ConvertTo-Json

Write-Host ""
Write-Host "CameraId  = $CameraId" -ForegroundColor Magenta
Write-Host "SessionId = $SessionId" -ForegroundColor Magenta


try {

    # ========================================================
    # 1. HEALTH
    # ========================================================

    TITLE "1. SERVICE HEALTH"

    $bh = Invoke-RestMethod "$Backend/actuator/health"

    if ($bh.status -eq "UP") {
        PASS "Backend UP"
    } else {
        FAIL "Backend health != UP"
        throw "Backend unhealthy"
    }


    $gh = Invoke-RestMethod "$Gateway/health"

    Write-Host "Gateway status     = $($gh.status)"
    Write-Host "AI configured      = $($gh.ai_dispatch_configured)"
    Write-Host "AI dispatch status = $($gh.ai_dispatch_status)"
    Write-Host "Circuit open       = $($gh.ai_dispatch_circuit_open)"

    if ($gh.status -eq "UP") {
        PASS "Gateway UP"
    } else {
        FAIL "Gateway DEGRADED"
        throw "Gateway unhealthy"
    }

    if ($gh.ai_dispatch_configured -eq $true) {
        PASS "Gateway -> AI HTTP enabled"
    } else {
        FAIL "Gateway -> AI HTTP disabled"
        throw "Gateway AI dispatch disabled"
    }

    if ($gh.ai_dispatch_circuit_open -eq $false) {
        PASS "AI circuit breaker closed"
    } else {
        FAIL "AI circuit breaker OPEN"
        throw "AI circuit open"
    }


    $ah = Invoke-RestMethod "$AI/health"

    if ($ah.status -eq "ok") {
        PASS "AI Worker UP"
    } else {
        FAIL "AI Worker unhealthy"
        throw "AI unhealthy"
    }

    if ($ah.model.loaded -eq $true) {
        PASS "AI model loaded: $($ah.model.version)"
    } else {
        FAIL "AI model not loaded"
        throw "AI model unavailable"
    }


    if (Test-Path $Image) {
        PASS "Smoke JPEG bulundu"
    } else {
        FAIL "Smoke JPEG bulunamadi"
        throw "Smoke image missing"
    }


    # ========================================================
    # 2. BASELINE
    # ========================================================

    TITLE "2. GATEWAY BASELINE"

    $Before = Invoke-RestMethod "$Gateway/metrics"

    Write-Host "active_sessions            = $($Before.active_sessions)"
    Write-Host "active_frame_queues        = $($Before.active_frame_queues)"
    Write-Host "active_ring_buffers        = $($Before.active_ring_buffers)"
    Write-Host "active_ingestion_workers   = $($Before.active_ingestion_workers)"
    Write-Host "active_ai_dispatch_workers = $($Before.active_ai_dispatch_workers)"
    Write-Host "ai_sampled_frames          = $($Before.ai_sampled_frames)"
    Write-Host "ai_dispatched_frames       = $($Before.ai_dispatched_frames)"

    PASS "Baseline metrics alindi"


    # ========================================================
    # 3. SESSION OPEN
    # Gateway -> Backend lifecycle OPEN
    # ========================================================

    TITLE "3. SESSION OPEN: GATEWAY -> BACKEND"

    $Open = Invoke-RestMethod `
        -Uri "$Gateway/api/v1/sessions/open" `
        -Method POST `
        -ContentType "application/json" `
        -Body $OpenBody

    $Opened = $true

    PASS "Gateway session OPEN"

    Write-Host "created   = $($Open.created)"
    Write-Host "cameraId  = $($Open.session.cameraId)"
    Write-Host "sessionId = $($Open.session.sessionId)"


    # ========================================================
    # 4. DUPLICATE OPEN
    # ========================================================

    TITLE "4. DUPLICATE OPEN / RECONNECT"

    $Duplicate = Invoke-RestMethod `
        -Uri "$Gateway/api/v1/sessions/open" `
        -Method POST `
        -ContentType "application/json" `
        -Body $OpenBody

    if ($Duplicate.created -eq $false) {
        PASS "Duplicate OPEN idempotent"
    } else {
        FAIL "Duplicate OPEN yeni session olusturdu"
    }


    # ========================================================
    # 5. HEARTBEAT
    # ========================================================

    TITLE "5. HEARTBEAT: GATEWAY -> BACKEND"

    $Hb = Invoke-RestMethod `
        -Uri "$Gateway/api/v1/sessions/$SessionId/heartbeat" `
        -Method POST `
        -ContentType "application/json" `
        -Body $ActionBody

    PASS "Gateway -> Backend heartbeat"

    Write-Host "lastHeartbeatAt = $($Hb.lastHeartbeatAt)"


    # ========================================================
    # 6. DIRECT REAL AI -> BACKEND
    # ========================================================

    TITLE "6. REAL AI -> BACKEND DETECTION"

    $EventId = [guid]::NewGuid().ToString()

    $Timestamp = (
        Get-Date
    ).ToUniversalTime().ToString(
        "yyyy-MM-ddTHH:mm:ss.fffZ"
    )

    $Probe = Invoke-WebRequest `
        -Uri "$AI/internal/v1/inference/frames" `
        -Method POST `
        -ContentType "image/jpeg" `
        -Headers @{
            "X-Camera-Id"       = $CameraId
            "X-Session-Id"      = $SessionId
            "X-Frame-Timestamp" = $Timestamp
            "X-Frame-Event-Id"  = $EventId
        } `
        -InFile $Image `
        -UseBasicParsing

    if ($Probe.StatusCode -eq 202) {
        PASS "Real AI inference -> Backend detection 202"
    } else {
        FAIL "AI inference HTTP $($Probe.StatusCode)"
        throw "Direct AI detection failed"
    }


    # ========================================================
    # 7. GERCEK ~15 FPS STREAM
    #
    # PowerShell Invoke-RestMethod yerine gateway venv Python
    # kullaniyoruz. Böylece gercekten ~15 FPS üretiyoruz.
    # ========================================================

    TITLE "7. JPEG STREAM -> GATEWAY (~15 FPS)"

    $PythonScript = "$Root\.smoke-stream.py"

    $PythonCode = @"
import asyncio
import json
import sys
import time
from datetime import datetime, timezone

import httpx

gateway = sys.argv[1].rstrip("/")
camera_id = sys.argv[2]
session_id = sys.argv[3]
image_path = sys.argv[4]

TOTAL = 90
FPS = 15.0
INTERVAL = 1.0 / FPS

with open(image_path, "rb") as f:
    jpeg = f.read()


async def main():
    accepted = 0
    rejected = 0
    heartbeat_ok = 0
    errors = []

    limits = httpx.Limits(
        max_connections=20,
        max_keepalive_connections=10,
    )

    start = time.perf_counter()

    async with httpx.AsyncClient(
        timeout=5.0,
        limits=limits,
    ) as client:

        for i in range(TOTAL):

            target = start + (i * INTERVAL)

            delay = target - time.perf_counter()

            if delay > 0:
                await asyncio.sleep(delay)

            timestamp = (
                datetime.now(timezone.utc)
                .isoformat()
                .replace("+00:00", "Z")
            )

            try:
                response = await client.post(
                    f"{gateway}/api/v1/sessions/{session_id}/frames",
                    content=jpeg,
                    headers={
                        "Content-Type": "image/jpeg",
                        "X-Camera-Id": camera_id,
                        "X-Frame-Timestamp": timestamp,
                    },
                )

                if response.status_code == 202:
                    body = response.json()

                    if body.get("accepted") is True:
                        accepted += 1
                    else:
                        rejected += 1
                        errors.append(
                            f"frame={i+1} accepted=false"
                        )
                else:
                    rejected += 1
                    errors.append(
                        f"frame={i+1} status={response.status_code} body={response.text[:200]}"
                    )

            except Exception as exc:
                rejected += 1
                errors.append(
                    f"frame={i+1} exception={exc}"
                )

            if (i + 1) % 30 == 0:
                try:
                    heartbeat = await client.post(
                        f"{gateway}/api/v1/sessions/{session_id}/heartbeat",
                        json={
                            "cameraId": camera_id,
                        },
                    )

                    if heartbeat.status_code == 200:
                        heartbeat_ok += 1
                    else:
                        errors.append(
                            f"heartbeat frame={i+1} status={heartbeat.status_code}"
                        )

                except Exception as exc:
                    errors.append(
                        f"heartbeat frame={i+1} exception={exc}"
                    )

    duration = time.perf_counter() - start

    fps = (
        accepted / duration
        if duration > 0
        else 0.0
    )

    print(
        json.dumps(
            {
                "accepted": accepted,
                "rejected": rejected,
                "heartbeat_ok": heartbeat_ok,
                "duration_seconds": duration,
                "actual_fps": fps,
                "errors": errors[:10],
            }
        )
    )


asyncio.run(main())
"@

    Set-Content `
        -Path $PythonScript `
        -Value $PythonCode `
        -Encoding UTF8


    $GatewayPython = "$Root\gateway\.venv\Scripts\python.exe"

    if (-not (Test-Path $GatewayPython)) {
        throw "Gateway venv python bulunamadi: $GatewayPython"
    }


    $StreamOutput = & $GatewayPython `
        $PythonScript `
        $Gateway `
        $CameraId `
        $SessionId `
        $Image

    if ($LASTEXITCODE -ne 0) {
        throw "15 FPS uploader failed"
    }


    $StreamLine = (
        @($StreamOutput) |
        Select-Object -Last 1
    )

    $Stream = $StreamLine | ConvertFrom-Json


    Write-Host "Accepted        = $($Stream.accepted) / 90"
    Write-Host "Rejected        = $($Stream.rejected)"
    Write-Host "Heartbeats      = $($Stream.heartbeat_ok) / 3"
    Write-Host "Duration        = $([math]::Round($Stream.duration_seconds, 2)) sec"
    Write-Host "Actual input FPS= $([math]::Round($Stream.actual_fps, 2))"

    if ($Stream.accepted -eq 90) {
        PASS "90/90 JPEG frame accepted"
    } else {
        FAIL "Frame acceptance $($Stream.accepted)/90"

        foreach ($error in $Stream.errors) {
            Write-Host "  $error" -ForegroundColor DarkYellow
        }
    }


    if ($Stream.heartbeat_ok -eq 3) {
        PASS "Stream sirasinda 3/3 heartbeat"
    } else {
        FAIL "Stream heartbeat $($Stream.heartbeat_ok)/3"
    }


    if ($Stream.actual_fps -ge 12.0) {
        PASS "Gercek mobile-like input >= 12 FPS ($([math]::Round($Stream.actual_fps, 2)))"
    } else {
        FAIL "Input hizi yetersiz: $([math]::Round($Stream.actual_fps, 2)) FPS"
    }


    # ========================================================
    # 8. 15 FPS -> ~3 FPS AI SAMPLING
    # ========================================================

    TITLE "8. 15 FPS -> ~3 FPS AI SAMPLING / DISPATCH"

    $During = $null

    for ($i = 1; $i -le 10; $i++) {

        Start-Sleep -Milliseconds 500

        $During = Invoke-RestMethod "$Gateway/metrics"

        $SampleDelta = [int]$During.ai_sampled_frames - [int]$Before.ai_sampled_frames

        $DispatchDelta = [int]$During.ai_dispatched_frames - [int]$Before.ai_dispatched_frames

        if (
            $SampleDelta -gt 0 -and
            $DispatchDelta -ge ($SampleDelta - 1)
        ) {
            break
        }
    }


    $SampleDelta = [int]$During.ai_sampled_frames - [int]$Before.ai_sampled_frames

    $DispatchDelta = [int]$During.ai_dispatched_frames - [int]$Before.ai_dispatched_frames

    $DropDelta = [int]$During.ai_dropped_stale_frames - [int]$Before.ai_dropped_stale_frames

    $FailureDelta = [int]$During.ai_dispatch_failures - [int]$Before.ai_dispatch_failures

    $TimeoutDelta = [int]$During.ai_dispatch_timeouts - [int]$Before.ai_dispatch_timeouts


    Write-Host "Input frames           = 90"
    Write-Host "AI sampled delta       = $SampleDelta"
    Write-Host "AI dispatched delta    = $DispatchDelta"
    Write-Host "AI stale drop delta    = $DropDelta"
    Write-Host "AI failures delta      = $FailureDelta"
    Write-Host "AI timeout delta       = $TimeoutDelta"
    Write-Host "AI avg latency ms      = $($During.ai_dispatch_latency_avg_ms)"
    Write-Host "Queue depth            = $($During.queued_frames)"
    Write-Host "Buffered frames        = $($During.buffered_frames)"


    # 90 frame / ~6 saniye / target 3 FPS
    # Beklenen yaklaşık 18 sample.
    if (
        $SampleDelta -ge 15 -and
        $SampleDelta -le 22
    ) {
        PASS "15 FPS input -> ~3 FPS sampling ($SampleDelta sample)"
    } else {
        FAIL "Sampling beklenen aralikta degil: $SampleDelta"
    }


    if (
        $DispatchDelta -ge ($SampleDelta - 1)
    ) {
        PASS "Sample edilen frameler AI Worker'a dispatch edildi"
    } else {
        FAIL "AI dispatch samplingin gerisinde: sample=$SampleDelta dispatch=$DispatchDelta"
    }


    if ($FailureDelta -eq 0) {
        PASS "AI dispatch failure yok"
    } else {
        FAIL "AI dispatch failure=$FailureDelta"
    }


    if ($TimeoutDelta -eq 0) {
        PASS "AI dispatch timeout yok"
    } else {
        FAIL "AI dispatch timeout=$TimeoutDelta"
    }


    if ($During.ai_dispatch_circuit_open -eq $false) {
        PASS "AI circuit breaker closed"
    } else {
        FAIL "AI circuit breaker OPEN"
    }


    if ($During.queued_frames -eq 0) {
        PASS "Frame queue backlog yok"
    } else {
        WARN "Frame queue depth=$($During.queued_frames)"
    }


    # ========================================================
    # 9. VIOLATION -> RECORDING -> READY
    # ========================================================

    TITLE "9. DETECTION -> VIOLATION -> RECORDING -> READY"

    Write-Host "Detection stream durdu."
    Write-Host "Silence watchdog + post-roll + FFmpeg + MinIO icin max 45 saniye bekleniyor..."

    $FinalDb = ""
    $E2EReady = $false

    for ($poll = 1; $poll -le 45; $poll++) {

        Start-Sleep -Seconds 1


        $Sql = @"
SELECT
    COUNT(v.id),
    COUNT(v.id) FILTER (
        WHERE v.lifecycle_status = 'COMPLETED'
    ),
    COUNT(v.id) FILTER (
        WHERE v.lifecycle_status = 'ERROR'
    ),
    COUNT(r.id),
    COUNT(r.id) FILTER (
        WHERE r.status = 'READY'
    ),
    COUNT(r.id) FILTER (
        WHERE r.status = 'ERROR'
    ),
    COUNT(v.id) FILTER (
        WHERE v.cover_image_key IS NOT NULL
    ),
    COALESCE(
        string_agg(
            v.violation_type
            || ':'
            || v.lifecycle_status
            || ':'
            || COALESCE(r.status, 'NO_RECORDING')
            || ':'
            || COALESCE(r.object_key, 'NULL'),
            ';'
        ),
        ''
    )
FROM violations v
LEFT JOIN recordings r
    ON r.violation_id = v.id
WHERE v.camera_session_id = (
    SELECT id
    FROM camera_sessions
    WHERE session_id = '$SessionId'
    LIMIT 1
);
"@

        $FinalDb = PSQL $Sql

        $parts = $FinalDb -split '\|', 8

        if ($parts.Count -lt 8) {
            continue
        }

        $ViolationCount      = [int]$parts[0]
        $CompletedCount      = [int]$parts[1]
        $ViolationErrorCount = [int]$parts[2]
        $RecordingCount      = [int]$parts[3]
        $ReadyCount          = [int]$parts[4]
        $RecordingErrorCount = [int]$parts[5]
        $CoverCount          = [int]$parts[6]
        $Details             = $parts[7]


        Write-Host (
            "poll {0:00}: violations={1} completed={2} recordings={3} ready={4} covers={5}" `
            -f $poll,
            $ViolationCount,
            $CompletedCount,
            $RecordingCount,
            $ReadyCount,
            $CoverCount
        )


        if (
            $ViolationCount -gt 0 -and
            $RecordingCount -eq $ViolationCount -and
            $ReadyCount -eq $RecordingCount -and
            $CompletedCount -eq $ViolationCount -and
            $ViolationErrorCount -eq 0 -and
            $RecordingErrorCount -eq 0
        ) {
            $E2EReady = $true
            break
        }
    }


    if ([string]::IsNullOrWhiteSpace($FinalDb)) {
        FAIL "DB recording sonucu okunamadi"
        throw "DB poll failed"
    }


    $parts = $FinalDb -split '\|', 8

    $ViolationCount      = [int]$parts[0]
    $CompletedCount      = [int]$parts[1]
    $ViolationErrorCount = [int]$parts[2]
    $RecordingCount      = [int]$parts[3]
    $ReadyCount          = [int]$parts[4]
    $RecordingErrorCount = [int]$parts[5]
    $CoverCount          = [int]$parts[6]
    $Details             = $parts[7]


    Write-Host ""
    Write-Host "Violation count = $ViolationCount"
    Write-Host "Completed       = $CompletedCount"
    Write-Host "Violation ERROR = $ViolationErrorCount"
    Write-Host "Recording count = $RecordingCount"
    Write-Host "READY           = $ReadyCount"
    Write-Host "Recording ERROR = $RecordingErrorCount"
    Write-Host "Cover count     = $CoverCount"
    Write-Host "Details         = $Details"


    if ($ViolationCount -gt 0) {
        PASS "AI detection -> temporal violation olustu ($ViolationCount)"
    } else {
        FAIL "Violation olusmadi"
    }


    if (
        $RecordingCount -eq $ViolationCount -and
        $RecordingCount -gt 0
    ) {
        PASS "Her violation icin recording olustu"
    } else {
        FAIL "Violation/recording sayisi uyusmuyor"
    }


    if (
        $ReadyCount -eq $RecordingCount -and
        $ReadyCount -gt 0
    ) {
        PASS "Tum recordingler READY"
    } else {
        FAIL "Tum recordingler READY degil"
    }


    if (
        $CompletedCount -eq $ViolationCount -and
        $CompletedCount -gt 0
    ) {
        PASS "Tum violationlar COMPLETED"
    } else {
        FAIL "Tum violationlar COMPLETED degil"
    }


    if (
        $ViolationErrorCount -eq 0 -and
        $RecordingErrorCount -eq 0
    ) {
        PASS "Violation/recording ERROR yok"
    } else {
        FAIL "Terminal ERROR mevcut"
    }


    if ($CoverCount -gt 0) {
        PASS "Cover image key olustu ($CoverCount)"
    } else {
        FAIL "Cover image key olusmadi"
    }


    if ($Details -match "\.mp4") {
        PASS "MinIO MP4 objectKey DB'ye yazildi"
    } else {
        FAIL "MP4 objectKey gorunmedi"
    }


    if ($E2EReady) {
        PASS "FULL recorder E2E READY"
    } else {
        FAIL "45 saniye icinde full READY olmadi"
    }


    # ========================================================
    # 10. DB DETAIL
    # ========================================================

    TITLE "10. FINAL DB DETAIL"

    PSQLDisplay @"
SELECT
    v.id AS violation_id,
    v.violation_type,
    v.lifecycle_status,
    v.started_at,
    v.ended_at,
    v.cover_image_key,
    r.id AS recording_id,
    r.status AS recording_status,
    r.duration_ms,
    r.size_bytes,
    r.object_key,
    r.error_code
FROM violations v
LEFT JOIN recordings r
    ON r.violation_id = v.id
WHERE v.camera_session_id = (
    SELECT id
    FROM camera_sessions
    WHERE session_id = '$SessionId'
    LIMIT 1
)
ORDER BY v.created_at;
"@


}
catch {

    FAIL "SMOKE DURDURULDU: $($_.Exception.Message)"

    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message -ForegroundColor Yellow
    }

}
finally {

    # ========================================================
    # 11. SESSION CLOSE
    # ========================================================

    TITLE "11. SESSION CLOSE"

    if ($Opened) {

        try {

            Invoke-WebRequest `
                -Uri "$Gateway/api/v1/sessions/$SessionId/close" `
                -Method POST `
                -ContentType "application/json" `
                -Body $ActionBody `
                -UseBasicParsing |
                Out-Null

            PASS "Gateway session CLOSED"

        }
        catch {

            FAIL "Gateway session close basarisiz"

        }
    }


    # ========================================================
    # 12. RESOURCE CLEANUP
    # ========================================================

    TITLE "12. RESOURCE CLEANUP"

    if ($null -ne $Before) {

        Start-Sleep -Seconds 2

        try {

            $After = Invoke-RestMethod "$Gateway/metrics"

            Write-Host "active_sessions            = $($After.active_sessions)"
            Write-Host "active_recordings          = $($After.active_recordings)"
            Write-Host "active_frame_queues        = $($After.active_frame_queues)"
            Write-Host "queued_frames              = $($After.queued_frames)"
            Write-Host "active_ring_buffers        = $($After.active_ring_buffers)"
            Write-Host "buffered_frames            = $($After.buffered_frames)"
            Write-Host "active_ingestion_workers   = $($After.active_ingestion_workers)"
            Write-Host "active_ai_dispatch_workers = $($After.active_ai_dispatch_workers)"


            if ($After.active_sessions -eq $Before.active_sessions) {
                PASS "Session baseline'a dondu"
            } else {
                FAIL "Ghost session var"
            }


            if ($After.active_frame_queues -eq $Before.active_frame_queues) {
                PASS "Frame queue cleanup"
            } else {
                FAIL "Frame queue leak"
            }


            if ($After.active_ring_buffers -eq $Before.active_ring_buffers) {
                PASS "Ring buffer cleanup"
            } else {
                FAIL "Ring buffer leak"
            }


            if ($After.active_ingestion_workers -eq $Before.active_ingestion_workers) {
                PASS "Ingestion worker cleanup"
            } else {
                FAIL "Ingestion worker leak"
            }


            if ($After.active_ai_dispatch_workers -eq $Before.active_ai_dispatch_workers) {
                PASS "AI dispatch worker cleanup"
            } else {
                FAIL "AI dispatch worker leak"
            }


            if ($After.active_recordings -eq $Before.active_recordings) {
                PASS "Recorder cleanup"
            } else {
                FAIL "Active recording leak"
            }

        }
        catch {

            FAIL "Final Gateway metrics alinamadi"

        }
    }


    # ========================================================
    # 13. BACKEND SESSION STATUS
    # ========================================================

    TITLE "13. BACKEND SESSION STATUS"

    try {

        PSQLDisplay @"
SELECT
    session_id,
    camera_id,
    status,
    started_at,
    ended_at,
    last_frame_at
FROM camera_sessions
WHERE session_id = '$SessionId';
"@

    }
    catch {

        WARN "Camera session DB query failed"

    }


    # Temp python temizle
    if (Test-Path "$Root\.smoke-stream.py") {
        Remove-Item "$Root\.smoke-stream.py" -Force
    }


    # ========================================================
    # FINAL
    # ========================================================

    TITLE "FINAL RESULT"

    Write-Host "PASS = $PassCount" -ForegroundColor Green
    Write-Host "FAIL = $FailCount" -ForegroundColor Red
    Write-Host "WARN = $WarnCount" -ForegroundColor Yellow

    Write-Host ""
    Write-Host "CameraId  = $CameraId"
    Write-Host "SessionId = $SessionId"
    Write-Host ""

    if ($FailCount -eq 0) {

        Write-Host "==============================================" -ForegroundColor Green
        Write-Host "       MVP FULL SMOKE: PASS" -ForegroundColor Green
        Write-Host "==============================================" -ForegroundColor Green

    } else {

        Write-Host "==============================================" -ForegroundColor Red
        Write-Host "       MVP FULL SMOKE: FAIL" -ForegroundColor Red
        Write-Host "==============================================" -ForegroundColor Red

    }
}



