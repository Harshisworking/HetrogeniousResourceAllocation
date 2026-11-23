import json
import urllib.parse
import urllib.request

PROMETHEUS = "http://127.0.0.1:9090"

def query(promql):
    params = urllib.parse.urlencode({"query": promql})
    url = f"{PROMETHEUS}/api/v1/query?{params}"

    with urllib.request.urlopen(url, timeout=10) as response:
        payload = json.load(response)

    if payload["status"] != "success":
        raise RuntimeError(payload)

    results = payload["data"]["result"]
    return [float(item["value"][1]) for item in results]

up = query('up{job="portal"}')
rps = query('sum(rate(http_requests_total{job="portal",app="portal"}[1m]))')

print("Portal scrape healthy:", up == [1.0])
print("Requests per second:", round(sum(rps), 4) if rps else "Not enough samples")
