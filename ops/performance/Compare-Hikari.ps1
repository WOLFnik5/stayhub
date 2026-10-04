param(
    [ValidateRange(1, 300)]
    [int]$Seconds = 15,
    [ValidatePattern('^\d+(,\d+)*$')]
    [string]$Rates = '1200,1800,2400',
    [ValidateRange(1, 300)]
    [int]$WarmupSeconds = 10,
    [ValidateRange(1, 10000)]
    [int]$WarmupRate = 600
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$reports = Join-Path $workspace 'target/performance'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$trials = @(
    @{ Pool = 10; Name = 'hikari-10-a' },
    @{ Pool = 20; Name = 'hikari-20-a' },
    @{ Pool = 20; Name = 'hikari-20-b' },
    @{ Pool = 10; Name = 'hikari-10-b' }
)

Push-Location $workspace
try {
    foreach ($trial in $trials) {
        Write-Host "Starting $($trial.Name): pool=$($trial.Pool), rates=$Rates, stage=${Seconds}s"
        $arguments = @(
            '-Dtest=BookingStressTest', '-Dstayhub.stress=true',
            "-Dstayhub.stress.pool-size=$($trial.Pool)",
            "-Dstayhub.stress.report-name=$($trial.Name)",
            "-Dstayhub.stress.seconds=$Seconds", "-Dstayhub.stress.rates=$Rates",
            "-Dstayhub.stress.warmup-seconds=$WarmupSeconds", "-Dstayhub.stress.warmup-rate=$WarmupRate",
            'test'
        )
        $log = Join-Path $reports "$($trial.Name).log"
        & mvn @arguments *> $log
        if ($LASTEXITCODE -ne 0) {
            Get-Content -LiteralPath $log -Tail 35
            throw "Trial $($trial.Name) failed; inspect $log"
        }
        $report = Get-Content -LiteralPath (Join-Path $reports "$($trial.Name).json") -Raw | ConvertFrom-Json
        if ($report.hikariMaximumPoolSize -ne $trial.Pool) {
            throw "Pool size did not match trial $($trial.Name)"
        }
        foreach ($stage in $report.stages) {
            $create = $stage.operations | Where-Object operation -eq 'create'
            Write-Host ("{0}: offered={1}, actual={2:N1}, create p95={3:N2}ms, acquire={4:N2}ms, pending={5}, drops={6}" -f
                $trial.Name, $stage.targetRequestsPerSecond,
                $stage.submittedRequestsPerSecondDuringArrivalWindow, $create.p95ArrivalMs,
                $stage.meanHikariAcquireMs, $stage.peakHikariPending, $stage.clientInFlightLimitDrops)
        }
    }
    $comparison = [ordered]@{
        order = @($trials | ForEach-Object Name)
        methodology = 'Sequential ABBA; fresh JVM and disposable database per trial; same fixture, warm-up and offered rates'
        reports = @($trials | ForEach-Object {
            Get-Content -LiteralPath (Join-Path $reports "$($_.Name).json") -Raw | ConvertFrom-Json
        })
    }
    $comparison | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath (Join-Path $reports 'hikari-comparison.json') -Encoding utf8
    Write-Host "Comparison saved to $reports/hikari-comparison.json"
} finally {
    Pop-Location
}
