from fastapi import FastAPI
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST
from fastapi.responses import Response
import time
import os

app = FastAPI()
REQUESTS = Counter("http_requests_total", "HTTP requests", ["app", "path"])
DURATION = Histogram("http_request_duration_seconds", "HTTP duration", ["app", "path"])
CPU = Gauge("portal_cpu_utilization_ratio", "Approximate process CPU use / configured CPU quota", ["app"])
last_wall = time.monotonic()
last_cpu = time.process_time()

@app.get("/health")
def health(): return {"status": "ok"}

@app.get("/")
def index():
    begin = time.monotonic()
    REQUESTS.labels(app="portal", path="/").inc()
    DURATION.labels(app="portal", path="/").observe(time.monotonic() - begin)
    return {"service": "portal", "message": "Kubernetes agentic demo"}

@app.get("/metrics")
def metrics():
    global last_wall, last_cpu
    wall, cpu = time.monotonic(), time.process_time()
    elapsed = max(wall - last_wall, 1e-9)
    quota = float(os.getenv("PORTAL_CPU_LIMIT_CORES", "0.5"))
    CPU.labels(app="portal").set(min(1.0, max(0.0, (cpu-last_cpu)/elapsed/max(quota, 1e-6))))
    last_wall, last_cpu = wall, cpu
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
