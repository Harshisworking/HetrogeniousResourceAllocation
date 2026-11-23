"""Runnable local fixture and optional real Kubernetes mode.

Fixture evidence is a CONTROLLED demonstration, not a public-source check.
"""
import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from dataclasses import asdict
from .core import Event, Metrics, Supervisor, VerificationAgent, HttpsEvidenceFetcher
from .kube import KubectlScaleExecutor
from .prometheus import PrometheusMonitoringAgent


class FixtureFetcher:
    def __init__(self, text):
        self.text = text
    def fetch(self, url):
        return self.text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--live-metrics", action="store_true", help="Read Prometheus and Kubernetes metrics")
    parser.add_argument("--execute", action="store_true", help="Actually request Kubernetes scaling; explicit opt-in")
    parser.add_argument("--prometheus", default="http://localhost:9090")
    parser.add_argument("--namespace", default="agentic-demo")
    parser.add_argument("--deployment", default="portal-amd64")
    parser.add_argument("--context", default=None)
    parser.add_argument("--trusted-host", action="append", default=[], help="Configured HTTPS evidence hostname")
    parser.add_argument("--event-file", default=None, help="JSON Event document fetched/verified against allowed source")
    parser.add_argument("--audit", default="run/decisions.sqlite3")
    args = parser.parse_args()
    if args.execute and (not args.live_metrics or not args.event_file or not args.trusted_host):
        parser.error("Live scaling requires --live-metrics, --event-file and --trusted-host; controlled fixture cannot write")
    now = datetime.now(timezone.utc)
    executor = KubectlScaleExecutor(args.namespace, args.deployment, enabled=args.execute, context=args.context)
    if args.live_metrics:
        replicas = executor.read_replicas()
        try:
            metrics = PrometheusMonitoringAgent(args.prometheus).read(replicas)
        except ValueError as exc:
            print(json.dumps({
                "status": "BLOCKED_TELEMETRY",
                "reason": str(exc),
                "kubernetes_write": False
            }, indent=2))
            return
    else:
        metrics = Metrics(request_rps=40, cpu_utilization=.60, p95_latency_ms=850, replicas=2)
    if args.event_file:
        event = Event(**json.loads(Path(args.event_file).read_text(encoding="utf-8")))
        verifier = VerificationAgent(set(args.trusted_host), HttpsEvidenceFetcher(set(args.trusted_host)))
    else:
        opening = (now + timedelta(seconds=120)).isoformat()
        excerpt = f"Registration service opens at {opening}"
        event = Event("CONTROLLED fixture: registration opens", "https://example.org/controlled", "portal", opening, excerpt, 2.0)
        verifier = VerificationAgent({"example.org"}, FixtureFetcher(excerpt))
        print("WARNING: using CONTROLLED local evidence fixture, not a verified official event")
    result = Supervisor(verifier, executor, args.audit).run(metrics, now, event, allow_mutation=args.execute)
    print(json.dumps(asdict(result), indent=2))


if __name__ == "__main__":
    main()
