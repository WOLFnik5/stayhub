#Requires -Version 7.4
param(
    [Parameter(Mandatory)] [uri]$BaseUrl,
    [ValidateRange(1, 1800)] [int]$DurationSeconds = 480,
    [ValidateRange(100, 5000)] [int]$IntervalMilliseconds = 500
)

$ErrorActionPreference = 'Stop'
if (-not $BaseUrl.IsAbsoluteUri -or $BaseUrl.Scheme -notin @('http', 'https')) {
    throw 'BaseUrl must be an absolute HTTP or HTTPS URL.'
}

$catalog = [uri]::new($BaseUrl, '/accommodations?page=0&size=1')
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(3)
$client.DefaultRequestHeaders.ConnectionClose = $true
$counts = @{}
$successfulTimings = [System.Collections.Generic.List[double]]::new()
Write-Host "Sampling $catalog for $DurationSeconds seconds."
$startedAt = [DateTimeOffset]::UtcNow
$duration = [System.Diagnostics.Stopwatch]::StartNew()
try {
    while ($duration.Elapsed.TotalSeconds -lt $DurationSeconds) {
        $request = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $response = $client.GetAsync($catalog).GetAwaiter().GetResult()
            $status = [string][int]$response.StatusCode
            $response.Dispose()
        } catch {
            $status = 'transport-error'
            if (-not $counts.ContainsKey($status)) {
                Write-Warning $_.Exception.Message
            }
        }
        $counts[$status]++
        if ($status -eq '200') {
            $successfulTimings.Add($request.Elapsed.TotalMilliseconds)
        }
        Start-Sleep -Milliseconds $IntervalMilliseconds
    }
} finally {
    $client.Dispose()
}

$successfulTimings.Sort()
$p95 = if ($successfulTimings.Count -gt 0) {
    $rank = [int][Math]::Ceiling(0.95 * $successfulTimings.Count)
    [Math]::Round($successfulTimings[$rank - 1], 1)
}

$counts.GetEnumerator() | Sort-Object Name |
    Format-Table Name, @{Label='Count'; Expression={$_.Value}} -AutoSize
[pscustomobject]@{
    StartedUtc = $startedAt.ToString('o')
    EndedUtc = [DateTimeOffset]::UtcNow.ToString('o')
    SuccessfulP95Milliseconds = $p95
} | Format-List

if ($counts.Count -ne 1 -or -not $counts.ContainsKey('200')) {
    throw 'Rollout smoke check received a failed request.'
}
