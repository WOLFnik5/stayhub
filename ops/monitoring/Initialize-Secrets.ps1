$ErrorActionPreference = 'Stop'
$directory = Join-Path $PSScriptRoot 'secrets'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
foreach ($name in @('metrics-password', 'grafana-password')) {
    $path = Join-Path $directory $name
    if (-not (Test-Path -LiteralPath $path)) {
        $bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        [System.IO.File]::WriteAllText($path, [Convert]::ToBase64String($bytes), [System.Text.UTF8Encoding]::new($false))
        Write-Host "Created $path"
    } else {
        Write-Host "Keeping existing $path"
    }
}
