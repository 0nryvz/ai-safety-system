# Prints local IPv4 candidates for physical-device / wireless demo.
# Does not write .env, change adapters, or touch firewall.

$ErrorActionPreference = "Stop"

Write-Host "Physical-device demo network candidates"
Write-Host "This script only prints values. It does not update .env."
Write-Host ""

function Test-PrivateIPv4 {
    param(
        [string]$IpAddress
    )

    $octets = $IpAddress.Split(".")

    if ($octets.Count -ne 4) {
        return $false
    }

    $first = [int]$octets[0]
    $second = [int]$octets[1]

    if ($first -eq 10) {
        return $true
    }

    if ($first -eq 192 -and $second -eq 168) {
        return $true
    }

    if ($first -eq 172 -and $second -ge 16 -and $second -le 31) {
        return $true
    }

    # Tailscale / CGNAT
    if ($first -eq 100 -and $second -ge 64 -and $second -le 127) {
        return $true
    }

    return $false
}

$candidates = @()

$interfaces = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()

foreach ($networkInterface in $interfaces) {
    if (
        $networkInterface.OperationalStatus -ne
        [System.Net.NetworkInformation.OperationalStatus]::Up
    ) {
        continue
    }

    $properties = $networkInterface.GetIPProperties()

    foreach ($unicast in $properties.UnicastAddresses) {
        $address = $unicast.Address

        if (
            $address.AddressFamily -ne
            [System.Net.Sockets.AddressFamily]::InterNetwork
        ) {
            continue
        }

        $ip = $address.ToString()

        if (
            $ip -eq "127.0.0.1" -or
            $ip.StartsWith("169.254.")
        ) {
            continue
        }

        if (-not (Test-PrivateIPv4 -IpAddress $ip)) {
            continue
        }

        $name = $networkInterface.Name
        $description = $networkInterface.Description

        $hotspotHint = $false

        if (
            $description -match "Wi-Fi Direct" -or
            $description -match "Hosted Network" -or
            $description -match "Mobile Hotspot" -or
            $ip.StartsWith("192.168.137.")
        ) {
            $hotspotHint = $true
        }

        $candidates += [pscustomobject]@{
            IP          = $ip
            Name        = $name
            Description = $description
            HotspotHint = $hotspotHint
        }
    }
}

if ($candidates.Count -eq 0) {
    Write-Host "No private/LAN IPv4 candidates found besides localhost."
    Write-Host "PC-only or adb reverse can keep:"
    Write-Host "  MINIO_PUBLIC_ENDPOINT=http://localhost:9000"
    exit 0
}

foreach ($candidate in $candidates) {
    $label = $candidate.Name

    if ($candidate.HotspotHint) {
        $label = "$label  [possible Windows Mobile Hotspot]"
    }

    Write-Host ("Adapter : {0}" -f $label)
    Write-Host ("Desc    : {0}" -f $candidate.Description)
    Write-Host ("IPv4    : {0}" -f $candidate.IP)
    Write-Host "Example runtime values:"
    Write-Host ("  MINIO_PUBLIC_ENDPOINT=http://{0}:9000" -f $candidate.IP)
    Write-Host ("  BACKEND_URL=http://{0}:8080" -f $candidate.IP)
    Write-Host ("  GATEWAY_URL=http://{0}:8000" -f $candidate.IP)
    Write-Host ""
}

Write-Host "Pick the address the phone can reach (LAN, hotspot, or Tailscale)."
Write-Host "Set MINIO_PUBLIC_ENDPOINT in your local .env only. Do not commit a personal IP."
Write-Host "MINIO_ENDPOINT should stay http://localhost:9000 for local backend/gateway access."
