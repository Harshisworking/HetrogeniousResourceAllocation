"""Read-only Prometheus HTTP API source for application metrics.

All queries are fixed, not model-generated. RPS metric names should be matched
to the instrumented demonstration application before production usage.
"""
from urllib.parse import urlencode, urlparse
from urllib.request import urlopen
import json
import math
from .core import Metrics


class PrometheusMonitoringAgent:
    def __init__(self, base_url: str, app: str = "portal"):
        p = urlparse(base_url)
        if p.scheme not in ("http", "https") or not p.hostname:
            raise ValueError("Invalid Prometheus endpoint")
        if not app.replace("_", "").replace("-", "").isalnum():
            raise ValueError("Invalid application metric label")
        self.url = base_url.rstrip("/")
        self.app = app

    def query_scalar(self, query: str) -> float:
        url = self.url + "/api/v1/query?" + urlencode({"query": query})

        with urlopen(url, timeout=5) as response:
            data = json.load(response)

        if data.get("status") != "success":
            raise ValueError("Prometheus query failed")

        results = data.get("data", {}).get("result", [])
        if not results:
            raise ValueError("Prometheus returned no data")

        total = sum(float(row["value"][1]) for row in results)

        if not math.isfinite(total):
            raise ValueError(
                "Prometheus returned an undefined metric (NaN/Inf)"
            )

        return total

    def read(self, replicas: int) -> Metrics:
        app = self.app
        rps = self.query_scalar(f'sum(rate(http_requests_total{{app="{app}"}}[1m]))')
        # CPU ratio needs an application-specific usage metric, normalized 0..1.
        cpu = self.query_scalar(f'avg(portal_cpu_utilization_ratio{{app="{app}"}})')
        p95 = self.query_scalar(f'histogram_quantile(0.95,sum by(le)(rate(http_request_duration_seconds_bucket{{app="{app}"}}[5m])))') * 1000
        return Metrics(rps, cpu, p95, replicas)
