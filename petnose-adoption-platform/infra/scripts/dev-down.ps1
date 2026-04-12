# dev-down.ps1 — Windows용 dev 환경 종료 스크립트
# 사용: .\infra\scripts\dev-down.ps1

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerDir = Join-Path $ScriptDir "..\docker"
$EnvFile   = Join-Path $DockerDir ".env"
$ComposeBase = Join-Path $DockerDir "compose.yaml"
$ComposeDev  = Join-Path $DockerDir "compose.dev.yaml"

Write-Host "[INFO] dev 환경 서비스를 종료합니다..." -ForegroundColor Cyan

docker compose `
    --env-file $EnvFile `
    -f $ComposeBase `
    -f $ComposeDev `
    down

Write-Host "[INFO] 서비스가 종료되었습니다." -ForegroundColor Green
Write-Host "  데이터 볼륨 삭제: docker compose ... down -v"
