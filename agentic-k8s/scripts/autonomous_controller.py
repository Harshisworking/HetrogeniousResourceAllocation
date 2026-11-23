import argparse
import json
import os
import time
from datetime import datetime, timezone
from urllib.error import URLError

from agentic_k8s.core import (
    HttpsEvidenceFetcher,
    Supervisor,
    VerificationAgent,
)
from agentic_k8s.kube import KubectlScaleExecutor
from agentic_k8s.prometheus import PrometheusMonitoringAgent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--cycles", type=int, default=6)
    parser.add_argument("--interval", type=int, default=15)
    parser.add_argument(
        "--target-rps-per-replica",
        type=float,
        default=3.0,
    )
    args = parser.parse_args()

    if args.cycles < 1 or args.interval < 10:
        parser.error("Cycles must be >= 1 and interval >= 10 seconds")

    if not 0 < args.target_rps_per_replica <= 100:
        parser.error("Invalid RPS target")

    if args.execute and not os.environ.get("KUBECONFIG"):
        parser.error("Set the restricted KUBECONFIG before execution")

    executor = KubectlScaleExecutor(
        namespace="agentic-demo",
        deployment="portal-amd64",
        enabled=args.execute,
    )

    monitoring = PrometheusMonitoringAgent(
        "http://127.0.0.1:9090",
        app="portal",
    )

    # No fabricated event and no trusted external source claimed.
    verifier = VerificationAgent(
        trusted_hosts=set(),
        fetcher=HttpsEvidenceFetcher(set()),
    )

    supervisor = Supervisor(
        verifier=verifier,
        executor=executor,
        audit_path="run/decisions.sqlite3",
    )

    # Experimental capacity target, NOT a measured SLO capacity.
    supervisor.optimiser.target_rps_per_replica = (
        args.target_rps_per_replica
    )

    last_scale = 0.0

    for cycle in range(args.cycles):
        print(f"\nCYCLE {cycle + 1}/{args.cycles}", flush=True)

        try:
            # Check that Prometheus is scraping the application.
            replicas = executor.read_replicas()

            healthy = monitoring.query_scalar(
                'sum(up{job="portal"})'
            )
            targets = monitoring.query_scalar(
                'count(up{job="portal"})'
            )
            oldest_age = monitoring.query_scalar(
                'max(time() - timestamp(up{job="portal"}))'
            )

            if (
                targets != replicas
                or healthy != replicas
                or oldest_age < 0
                or oldest_age > 30
            ):
                raise ValueError(
                    f"Portal telemetry unhealthy: "
                    f"healthy={healthy}, targets={targets}, "
                    f"replicas={replicas}, "
                    f"oldest_age={oldest_age:.1f}s"
                )
            metrics = monitoring.read(replicas)

            # Prevent repeated scale requests during a cooldown.
            cooldown_active = (
                time.monotonic() - last_scale < 45
            )

            allow_write = args.execute and not cooldown_active

            outcome = supervisor.run(
                metrics=metrics,
                now=datetime.now(timezone.utc),
                event=None,
                allow_mutation=allow_write,
            )

            print("METRICS:", json.dumps({
                "rps": round(metrics.request_rps, 3),
                "cpu_ratio": round(metrics.cpu_utilization, 4),
                "p95_ms": round(metrics.p95_latency_ms, 3),
                "replicas": replicas,
            }))

            print("DECISION:", json.dumps({
                "status": outcome.status,
                "desired": outcome.desired_replicas,
                "mode": outcome.plan_mode,
                "event_verified": outcome.verified,
                "reason": outcome.reason,
            }))

            if outcome.status == "REQUESTED":
                last_scale = time.monotonic()

        except (ValueError, URLError, TimeoutError) as exc:
            print("BLOCKED_TELEMETRY:", str(exc))
            print("Kubernetes scaling: NOT REQUESTED")

        if cycle + 1 < args.cycles:
            time.sleep(args.interval)


if __name__ == "__main__":
    main()
