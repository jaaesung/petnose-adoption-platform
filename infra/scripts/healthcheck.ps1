# healthcheck.ps1 — Windows용 헬스체크 스크립트
# 사용: .\infra\scripts\healthcheck.ps1

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerDir  = Join-Path $ScriptDir "..\docker"
$EnvFile    = Join-Path $DockerDir ".env"
$ComposeBase = Join-Path $DockerDir "compose.yaml"
$ComposeDev  = Join-Path $DockerDir "compose.dev.yaml"

Write-Host "=== 컨테이너 상태 ===" -ForegroundColor Cyan
docker compose `
    --env-file $EnvFile `
    -f $ComposeBase `
    -f $ComposeDev `
    ps

Write-Host ""
Write-Host "=== HTTP 헬스체크 ===" -ForegroundColor Cyan

function Check-Http {
    param(
        [string]$Label,
        [string]$Url
    )
    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        Write-Host "[OK]   $Label  ->  $Url" -ForegroundColor Green
    } catch {
        Write-Host "[FAIL] $Label  ->  $Url" -ForegroundColor Red
    }
}

# Nginx 진입점
Check-Http "nginx        " "http://localhost/"

# Nginx 경유 Spring actuator
Check-Http "nginx→spring " "http://localhost/actuator/health"

# Spring 직접 (dev 포트 오픈 시)
Check-Http "spring-api   " "http://localhost:8080/actuator/health"

# Spring dev ping (dev profile 전용)
Check-Http "dev-ping     " "http://localhost:8080/api/dev/ping"

# Python Embed (dev 포트 오픈 시)
Check-Http "python-embed " "http://localhost:8000/health"

# Qdrant (dev 포트 오픈 시)
Check-Http "qdrant       " "http://localhost:6333/healthz"

Write-Host ""
Write-Host "헬스체크 완료." -ForegroundColor Cyan
