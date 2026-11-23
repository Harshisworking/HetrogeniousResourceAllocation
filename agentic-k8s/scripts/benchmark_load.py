import time
import urllib.request

URL = "http://portal.agentic-demo.svc.cluster.local/"
DURATION = 180
SLEEP = 0.08

latencies = []
success = 0
errors = 0
start_time = time.monotonic()

while time.monotonic() - start_time < DURATION:
    start = time.monotonic()

    try:
        with urllib.request.urlopen(URL, timeout=3) as response:
            response.read()
            if response.status != 200:
                raise RuntimeError(f"HTTP {response.status}")
        success += 1
        latencies.append((time.monotonic() - start) * 1000)
    except Exception:
        errors += 1

    time.sleep(SLEEP)

latencies.sort()
p95 = (
    latencies[int(0.95 * (len(latencies) - 1))]
    if latencies else None
)

print("successful_requests:", success, flush=True)
print("failed_requests:", errors, flush=True)
print("client_p95_ms:", p95, flush=True)
print("actual_elapsed_seconds:", round(time.monotonic() - start_time, 2), flush=True)
