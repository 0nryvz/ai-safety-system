param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

$resolvedPath = Resolve-Path $EnvFile -ErrorAction Stop

Write-Host "Loading environment from:" $resolvedPath

Get-Content $resolvedPath | ForEach-Object {

    $line = $_.Trim()

    if (
        [string]::IsNullOrWhiteSpace($line) -or
        $line.StartsWith("#")
    ) {
        return
    }

    $separatorIndex = $line.IndexOf("=")

    if ($separatorIndex -lt 1) {
        return
    }

    $name = $line.Substring(
        0,
        $separatorIndex
    ).Trim()

    $value = $line.Substring(
        $separatorIndex + 1
    ).Trim()

    if (
        $value.StartsWith('"') -and
        $value.EndsWith('"')
    ) {
        $value = $value.Substring(
            1,
            $value.Length - 2
        )
    }

    [Environment]::SetEnvironmentVariable(
        $name,
        $value,
        "Process"
    )

    Write-Host "Loaded:" $name
}

Write-Host ""
Write-Host "Environment loaded into current PowerShell process."