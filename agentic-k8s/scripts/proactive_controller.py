"""Event-aware autoscaler; run from repository root with PYTHONPATH=.

Reuses core.py's verified event, supervised decisions, safety and SQLite audit.
Live events require an explicitly allowlisted HTTPS host. A separately labelled
controlled fixture can be used for a local integration demonstration only.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
import json
import math
import os
from pathlib import Path
import subprocess
import time
from urllib.error import HTTPError, URLError

from agentic_k8s.core import Event, HttpsEvidenceFetcher, Metrics, PredictionAgent, Supervisor, VerificationAgent
from agentic_k8s.kube import KubectlScaleExecutor
from agentic_k8s.prometheus import PrometheusMonitoringAgent


class ControlledFetcher:
    """Never performs network I/O. Only used under --controlled-demo."""
    def __init__(self, excerpt: str):
        self.excerpt = excerpt

    def fetch(self, url: str) -> str:
        return self.excerpt


class ForecastWithEventFloor(PredictionAgent):
    """Enforce the operator's explicit peak demand assumption within event window.

    This is a deterministic capacity estimate, NOT a trained ML prediction.
    It is only activated if the existing verifier approves the event.
    """
    def __init__(self, minimum_event_rps: float):
        self.minimum_event_rps = minimum_event_rps

    def forecast_rps(self, metrics, verified, event, now, lead_seconds=300):
        reactive = super().forecast_rps(metrics, verified, event, now, lead_seconds)
        if verified.approved and verified.event_time is not None and event is not None:
            until = (verified.event_time - now).total_seconds()
            if 0 <= until <= lead_seconds:
                return max(reactive, self.minimum_event_rps)
        return reactive


def load_event(filename: str) -> tuple[Event, float]:
    content = json.loads(Path(filename).read_text(encoding="utf-8"))
    allowed = {"title", "source_url", "service", "opening_utc", "evidence_excerpt", "expected_multiplier", "estimated_peak_rps"}
    unexpected = set(content) - allowed
    if unexpected:
        raise ValueError(f"Unsupported event properties: {sorted(unexpected)}")
    estimated = float(content["estimated_peak_rps"])
    if not math.isfinite(estimated) or not 0 < estimated <= 25:
        raise ValueError("estimated_peak_rps must be within (0, 25] for bounded lab demonstration")
    event = Event(**{key: value for key, value in content.items() if key != "estimated_peak_rps"})
    if event.service != "portal":
        raise ValueError("Only the portal service is allowed")
    return event, estimated


def generate_controlled_event(now: datetime, lead_seconds: int, estimated_rps: float) -> tuple[Event, float]:
    opening = (now + timedelta(seconds=lead_seconds)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    excerpt = f"CONTROLLED LAB EVENT: portal opens at {opening}"
    event = Event(
        title="CONTROLLED LAB TRAFFIC EVENT (NOT OFFICIAL)",
        source_url="https://controlled.example.org/lab-event",
        service="portal",
        opening_utc=opening,
        evidence_excerpt=excerpt,
        expected_multiplier=2.0,
    )
    return event, estimated_rps


def valid_targets(monitor: PrometheusMonitoringAgent, replicas: int) -> dict:
    # Prometheus query_scalar sums vector values, so use explicit scalar reductions.
    healthy = monitor.query_scalar('sum(up{job="portal"})')
    targets = monitor.query_scalar('count(up{job="portal"})')
    age = monitor.query_scalar('max(time() - timestamp(up{job="portal"}))')
    if healthy != replicas or targets != replicas or age < 0 or age > 30:
        raise ValueError(f"Pod telemetry not yet aligned: healthy={healthy}, targets={targets}, replicas={replicas}, oldest_age={age:.1f}s")
    return {"healthy": healthy, "targets": targets, "max_age_seconds": age}


def read_metrics(monitor: PrometheusMonitoringAgent, replicas: int) -> Metrics:
    # A NaN histogram is normal with no requests; this must not prevent advance
    # capacity provisioning when RPS is precisely zero and other metrics are valid.
    app = monitor.app
    rps = monitor.query_scalar(
        f'sum(rate(http_requests_total{{app="{app}"}}[1m])) or vector(0)'
    )
    cpu = monitor.query_scalar(f'avg(portal_cpu_utilization_ratio{{app="{app}"}})')
    try:
        p95 = monitor.query_scalar(f'histogram_quantile(0.95,sum by(le)(rate(http_request_duration_seconds_bucket{{app="{app}"}}[5m])))') * 1000
    except ValueError:
        if rps != 0:
            raise
        p95 = 0.0  # explicitly no current client latency observations
    return Metrics(rps, cpu, p95, replicas)


def ready_replicas() -> int:
    command = ["kubectl", "-n", "agentic-demo", "get", "deployment", "portal-amd64", "-o", "json"]
    proc = subprocess.run(command, capture_output=True, text=True, check=True, timeout=15)
    return int(json.loads(proc.stdout).get("status", {}).get("readyReplicas", 0))


def append_record(path: Path, record: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(record, sort_keys=True) + "\n")


def cli(argv=None):
    parser = argparse.ArgumentParser(description="Event-aware Kubernetes autoscaler with explicit research assumptions")
    event_group = parser.add_mutually_exclusive_group(required=True)
    event_group.add_argument("--event-file", help="Real event JSON with allowlisted HTTPS evidence")
    event_group.add_argument("--controlled-demo", action="store_true", help="Clearly labelled synthetic future event; NO official verification")
    parser.add_argument("--trusted-host", action="append", default=[], help="Allowlisted HTTPS hostname for a real event (repeatable)")
    parser.add_argument("--execute", action="store_true", help="Permit restricted Kubernetes scaling")
    parser.add_argument("--cycles", type=int, default=12)
    parser.add_argument("--interval", type=int, default=15)
    parser.add_argument("--target-rps-per-replica", type=float, default=5.0)
    parser.add_argument("--demo-event-in", type=int, default=150, help="Seconds until controlled event")
    parser.add_argument("--estimated-peak-rps", type=float, default=12.0, help="Controlled event peak estimate")
    parser.add_argument("--prometheus", default="http://127.0.0.1:9090")
    parser.add_argument("--audit", default="run/decisions.sqlite3")
    parser.add_argument("--timeline", default="run/proactive-timeline.jsonl")
    args = parser.parse_args(argv)
    if args.cycles <= 0 or not 10 <= args.interval <= 300:
        parser.error("Require cycles >= 1 and 10 <= interval <= 300")
    if not 0 < args.target_rps_per_replica <= 25:
        parser.error("target RPS must be within (0, 25]")
    if args.execute and not os.environ.get("KUBECONFIG"):
        parser.error("--execute requires a restricted KUBECONFIG environment variable")
    if args.event_file and not args.trusted_host:
        parser.error("Real event verification requires --trusted-host")
    if args.controlled_demo:
        if not 30 <= args.demo_event_in <= 300:
            parser.error("demo-event-in must be in [30, 300] seconds")
        if not 0 < args.estimated_peak_rps <= 15:
            parser.error("controlled peak estimate must be in (0, 15] RPS (<= 3 Pods at 5 RPS/Pod)")
        event, floor = generate_controlled_event(datetime.now(timezone.utc), args.demo_event_in, args.estimated_peak_rps)
        verifier = VerificationAgent({"controlled.example.org"}, ControlledFetcher(event.evidence_excerpt))
        print("CONTROLLED DEMO: evidence is local synthetic data, NOT a verified external announcement", flush=True)
    else:
        event, floor = load_event(args.event_file)
        hosts = {host.lower() for host in args.trusted_host}
        verifier = VerificationAgent(hosts, HttpsEvidenceFetcher(hosts))
        print("REAL HTTPS EVENT: verification is performed against allowlisted source each cycle", flush=True)

    executor = KubectlScaleExecutor("agentic-demo", "portal-amd64", enabled=args.execute)
    monitor = PrometheusMonitoringAgent(args.prometheus, app="portal")
    supervisor = Supervisor(verifier, executor, args.audit)
    supervisor.predictor = ForecastWithEventFloor(floor)
    supervisor.optimiser.target_rps_per_replica = args.target_rps_per_replica
    timeline = Path(args.timeline)
    last_scale = None

    for i in range(args.cycles):
        now = datetime.now(timezone.utc)
        record = {"utc": now.isoformat(), "cycle": i + 1, "event_opening_utc": event.opening_utc, "controlled_demo": args.controlled_demo}
        try:
            replicas = executor.read_replicas()
            record["replicas_before"] = replicas
            record["ready_before"] = ready_replicas()
            record["telemetry"] = valid_targets(monitor, replicas)
            metrics = read_metrics(monitor, replicas)
            record["metrics"] = {"request_rps": metrics.request_rps, "cpu_ratio": metrics.cpu_utilization, "p95_ms": metrics.p95_latency_ms}
            verification = verifier.verify(event, now)
            record["event_verified"] = verification.approved
            record["verification_reason"] = verification.reason
            # For an explicit event run, a failed event verification must not
            # produce live scaling writes disguised as proactive action.
            in_window = bool(verification.approved and verification.event_time and 0 <= (verification.event_time-now).total_seconds() <= 300)
            cooldown = last_scale is not None and time.monotonic() - last_scale < 45
            allow = args.execute and in_window and not cooldown
            result = supervisor.run(metrics, now, event=event, allow_mutation=allow)
            record.update({"status": result.status, "desired": result.desired_replicas, "mode": result.plan_mode, "reason": result.reason, "cooldown": cooldown})
            if result.status == "REQUESTED":
                last_scale = time.monotonic()
                record["scale_requested_utc"] = datetime.now(timezone.utc).isoformat()
        except (ValueError, KeyError, URLError, HTTPError, TimeoutError, subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
            record.update({"status": "BLOCKED_TELEMETRY_OR_EXECUTION", "reason": f"{type(exc).__name__}: {exc}", "kubernetes_write": False})
        append_record(timeline, record)
        print(json.dumps(record, sort_keys=True), flush=True)
        if i + 1 < args.cycles:
            time.sleep(args.interval)


if __name__ == "__main__":
    cli()
