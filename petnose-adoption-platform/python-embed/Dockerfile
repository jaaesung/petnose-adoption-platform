# ─── Python Embedding Service ────────────────────────────────────────────────
FROM python:3.11-slim

WORKDIR /app

# 의존성 설치 (캐시 레이어 분리)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 앱 소스 복사
COPY app ./app

EXPOSE 8000

# healthcheck: Python 내장 urllib 사용 (curl 불필요)
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=3)" \
    || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
