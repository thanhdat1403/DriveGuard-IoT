
Add-Type -AssemblyName System.Net.Http

$token = Read-Host "Nhap JWT ADMIN (khong nhap Bearer)"
$token = $token.Trim()

$url = "http://localhost:8081/api/v1/device-assignments"

$body = @{
    deviceId  = 1
    driverId  = 1
    startedAt = "2026-11-06T14:40:00"
    endedAt   = "2026-11-06T14:50:00"
} | ConvertTo-Json -Compress

$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Authorization =
New-Object System.Net.Http.Headers.AuthenticationHeaderValue(
"Bearer", $token
)

try {
    $content1 = New-Object System.Net.Http.StringContent(
    $body,
    [System.Text.Encoding]::UTF8,
    "application/json"
    )

    $content2 = New-Object System.Net.Http.StringContent(
    $body,
    [System.Text.Encoding]::UTF8,
    "application/json"
    )

    $task1 = $client.PostAsync($url, $content1)
    $task2 = $client.PostAsync($url, $content2)

    $tasks = [System.Threading.Tasks.Task[]]@($task1, $task2)
    [System.Threading.Tasks.Task]::WaitAll($tasks)

    $response1 = $task1.Result
    $response2 = $task2.Result

    Write-Host "`nREQUEST 1: $([int]$response1.StatusCode)"
    Write-Host $response1.Content.ReadAsStringAsync().Result

    Write-Host "`nREQUEST 2: $([int]$response2.StatusCode)"
    Write-Host $response2.Content.ReadAsStringAsync().Result
}
finally {
    $client.Dispose()
}
