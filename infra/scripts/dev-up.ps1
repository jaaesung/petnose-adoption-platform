# dev-up.ps1 — Windows용 dev 환경 기동 스크립트
# 사용: .\infra\scripts\dev-up.ps1

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DockerDir = Join-Path $ScriptDir "..\docker"
$EnvFile   = Join-Path $DockerDir ".env"
$ComposeBase = Join-Path $DockerDir "compose.yaml"
$ComposeDev  = Join-Path $DockerDir "compose.dev.yaml"

# .env 파일 존재 확인
if (-not (Test-Path $EnvFile)) {
    Write-Host "[ERROR] .env 파일이 없습니다." -ForegroundColor Red
    Write-Host "  Copy-Item `"$DockerDir\.env.example`" `"$EnvFile`"" -ForegroundColor Yellow
    Write-Host "  위 명령을 실행한 뒤 .env 값을 확인하세요." -ForegroundColor Yellow
    exit 1
}

Write-Host "[INFO] dev 환경 서비스를 시작합니다..." -ForegroundColor Cyan

docker compose `
    --env-file $EnvFile `
    -f $ComposeBase `
    -f $ComposeDev `
    up -d --build

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] docker compose 실행에 실패했습니다. 위 로그를 확인하세요." -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] 서비스가 기동되었습니다." -ForegroundColor Green
Write-Host "  접속: http://localhost"
Write-Host "  헬스체크: bash infra/scripts/healthcheck.sh (또는 직접 curl)"
