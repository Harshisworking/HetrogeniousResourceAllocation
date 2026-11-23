# Synopsis completion plan — no premature feature freeze

**Verified so far:** 190 passing Java tests for the simulation/integration paths reported by the project owner; 12 Python unit tests locally passed for this new starter. Neither result proves deployment on Kubernetes. We do not yet have real HPA comparison results.

## Milestone A — live Kubernetes basics (this archive)
- Verify restricted controller account with `kubectl auth can-i patch deployments/scale --resource-name portal-amd64 -n agentic-demo --as system:serviceaccount:agentic-demo:agentic-controller` (actual authentication checks are required).
- Run locally (DRY_RUN), deploy the demo app, configure Prometheus, run live metric read, test one authorized scale on a disposable cluster, observe Pod readiness.
- Keep agent decisions and actual Pod observations separately logged.

## Milestone B — integrate existing Java agent intelligence
- Reuse Java event extraction and claim verification; create a stable structured event/decision API boundary with the Python Kubernetes controller or implement the equivalent Java Kubernetes client.
- Add secured service identity, idempotency, persistent cooldown and protected scale-down.
- Connect monitoring to Prometheus and node-capacity-aware amd64/arm64 placement; do not mistake architecture labels for verified node compatibility.

## Milestone C — fair scientific experiment against real HPA
- Instrument portal p50/p95/p99 latency, requests/sec, failures, pod startup time, Pod-hours and error budget.
- Isolate HPA and agentic trials; use Locust profiles with identical RPS schedules, controlled warmup, repeated seeds, identical resource limits and node pools.
- Compare static vs Kubernetes HPA vs agentic controller in the same cluster. Keep event fixture identical, record event verification status, denial behavior, early-scaling lead time and cost/Pod-hour tradeoff.
- Report statistical variation, not just one synthetic run. A win is not guaranteed.

## Milestone D — feedback + rollback + deliverable
- Build feedback evaluator with explicit SLO thresholds and safe rollback behavior, SQLite audit/replay, deployment health and saturation checks.
- Validate against false and late events, stale/failed Prometheus, API timeout, CPU overload, unavailable edge node and pod scheduling failure.
- Only then write conclusion about real Kubernetes HPA benefit and submit final report/demo.
