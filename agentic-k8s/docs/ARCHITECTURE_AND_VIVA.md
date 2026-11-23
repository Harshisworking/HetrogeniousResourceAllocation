# Architecture, viva preparation, and demonstration guide

## One-minute project explanation

The goal is to reduce capacity-provisioning delays for a Kubernetes application when a scheduled event may cause traffic. The Python agent combines a scheduled-event verifier, live Prometheus monitoring, an explicit peak-demand estimate, a replica optimizer, safety checks, and a restricted Kubernetes scaling executor. A controlled scheduled event demonstrated a real scale-out from one to three replicas while observed RPS was zero, and three replicas were already ready 109 seconds before the planned opening. Separate three-minute workload Jobs measured client P95 latency of 17.38, 25.78, and 59.14 ms for proactive, reactive, and HPA respectively. These are single-run results; the live predictor currently uses an operator-entered peak estimate rather than a learned forecasting model.

## Main components and responsibilities

| Module | Responsibility |
|---|---|
| `agentic_k8s/core.py` | Event and Metrics models, VerificationAgent, PredictionAgent, OptimisationAgent, SafetyAgent, Supervisor and SQLite audit |
| `agentic_k8s/prometheus.py` | Queries Prometheus for request rate, CPU utilization and latency |
| `agentic_k8s/kube.py` | Restricted kubectl interaction and safe scale request |
| `scripts/proactive_controller.py` | Controlled lab event and strict external-event input, event-floor estimation, telemetry/health validation, supervised decision loop |
| `scripts/autonomous_controller.py` | Reactive control loop without scheduled event |
| `scripts/compare_scalers.py` | Orchestrates independent proactive, reactive and HPA benchmarking runs |
| `k8s/portal-hpa.yaml`, `k8s/portal-load-job.yaml` | CPU-based HPA baseline and in-cluster load Job |
| `run/final-results/*` | Preserved true benchmark logs and timestamped readiness evidence |

## Control loop

```text
Scheduled event + evidence             Prometheus pod/traffic measurements
             |                                       |
       Event verification                      Health/freshness gate
             |                                       |
             +--------> Demand estimation <---------+
                                  |
                       Replica target optimization
                                  |
                  Safety bounds + change cooldown
                                  |
                 Restricted Kubernetes scale API
                                  |
             Pod readiness observations + SQLite/JSONL audit
```

## Key equations

Demand-bound replicas = `ceil(estimated_peak_rps / target_rps_per_replica)`, at least 1.

In the controlled event `ceil(12 / 5) = 3` Pods. This is an explicitly assumed peak; the system does not learn 12 RPS from traffic history.

Observed successful throughput = `successful_requests / actual_elapsed_seconds`.

Percentage reduction in P95 vs comparator = `100 * (comparator_p95 - proactive_p95) / comparator_p95`.

The measured pre-event lead is computed from timestamped first 3/3-ready status and scheduled opening; it is a sampling lower bound on how early Pods were ready.

## Viva questions with model answers

**1. What makes scaling proactive?** The controller scales during a verified event lead window while the measured request rate is still zero. Reactive methods use observed traffic/CPU instead.

**2. What is agentic here?** A multi-stage, supervised control loop verifies an input, reads system state, estimates demand, plans a Kubernetes action, checks policies, executes through RBAC, and audits the outcome. This does not imply autonomous LLM reasoning or trained ML.

**3. Why Prometheus?** It provides near-real-time metrics and pod-target health checks, reducing the risk of acting on missing or stale observations.

**4. Why does idle P95 equal zero?** The histogram has no meaningful sample when no requests occur; missing latency is accepted only if measured RPS is zero. Missing P95 during nonzero RPS should fail rather than mislead the safety decision.

**5. Why an explicit traffic floor?** A simple multiplier applied to current idle RPS (`0 × multiplier`) remains zero. A bounded operator-entered peak provides a nonzero proactive capacity target for the demonstration.

**6. What controls unsafe scaling?** Replica range, per-cycle increase limit, cooldown, telemetry validity, event verification and restricted Kubernetes permissions.

**7. What is the difference between desired and ready replicas?** Desired is what the Deployment requests; ready is the number that have passed readiness checks and can serve traffic. Scaling is not complete for availability purposes until Pods are ready.

**8. Did proactive definitively beat HPA?** No. In single runs it had lower observed P95 and more successful requests, but lead times, controller policies and environmental variation mean we cannot make a statistically robust or causal claim.

**9. Why is it not production-ready?** The event is synthetic, the peak estimate is manual, testing is single-node, there is no automatic scale-in and repeated randomized trials and failures haven't been characterized.

**10. What would make this an AI forecasting project?** A validated demand model trained on historical traffic and event features, evaluation on held-out periods, uncertainty estimation, and integration under existing safety controls.

## Demonstration order (without rerunning load)

1. Run `python scripts/finalize_results.py` against real preserved logs.
2. Present the controlled event timeline and readiness observations.
3. Present benchmark P95/throughput table with single-run caveat.
4. Show the passing test suite and limited RBAC.
5. Explain what real-event verification and learned forecasting would require.

Do not edit or fabricate the saved log evidence; never claim the synthetic fixture is real public-event verification.
