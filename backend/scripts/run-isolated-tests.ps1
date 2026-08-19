$ErrorActionPreference = "Stop"

$containerName = "isg-backend-test-$PID"
$dbName = "isg_test"
$dbUser = "isg_test_user"
$dbPassword = "isg_test_password"

$previousProfile = $env:SPRING_PROFILES_ACTIVE
$previousUrl = $env:SPRING_DATASOURCE_URL
$previousUsername = $env:SPRING_DATASOURCE_USERNAME
$previousPassword = $env:SPRING_DATASOURCE_PASSWORD

try {
    Write-Host "Starting isolated PostgreSQL test container..."

    docker run -d --rm `
        --name $containerName `
        -e POSTGRES_DB=$dbName `
        -e POSTGRES_USER=$dbUser `
        -e POSTGRES_PASSWORD=$dbPassword `
        -P `
        postgres:16 | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start isolated PostgreSQL container."
    }

    $port = $null

    for ($i = 0; $i -lt 30; $i++) {
        $portOutput = docker port $containerName 5432/tcp 2>$null

        if ($portOutput -match ':(\d+)$') {
            $port = $Matches[1]
        }

        $ready = docker exec $containerName `
            pg_isready -U $dbUser -d $dbName 2>$null

        if ($LASTEXITCODE -eq 0 -and $port) {
            break
        }

        Start-Sleep -Seconds 1
    }

    if (-not $port) {
        throw "Could not determine isolated PostgreSQL port."
    }

    docker exec $containerName pg_isready -U $dbUser -d $dbName | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Isolated PostgreSQL did not become ready."
    }

    $env:SPRING_PROFILES_ACTIVE = "test"
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$port/$dbName"
    $env:SPRING_DATASOURCE_USERNAME = $dbUser
    $env:SPRING_DATASOURCE_PASSWORD = $dbPassword

    Write-Host "Running backend tests against isolated PostgreSQL on port $port..."

    Push-Location (Join-Path $PSScriptRoot "..")

    try {
        & .\mvnw.cmd test
        $testExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($testExitCode -ne 0) {
        throw "Backend tests failed with exit code $testExitCode."
    }

    Write-Host "Isolated backend tests completed successfully."
}
finally {
    Write-Host "Cleaning up isolated PostgreSQL container..."
    docker rm -f $containerName 2>$null | Out-Null

    $env:SPRING_PROFILES_ACTIVE = $previousProfile
    $env:SPRING_DATASOURCE_URL = $previousUrl
    $env:SPRING_DATASOURCE_USERNAME = $previousUsername
    $env:SPRING_DATASOURCE_PASSWORD = $previousPassword
}
