# Event-Aware Agentic AI for Efficient Cloud Resource Management

## Final Technical Report — Controlled Kubernetes Prototype

**Study type:** Single-node K3s proof-of-concept with controlled synthetic event and in-cluster benchmark.  
**Date:** 23 November 2026.  
**Scope:** This document summarizes the implementation and experiment evidence supplied by the project developer. Source code and raw logs are stored in the local project repository; this report is not a substitute for those files.

### Abstract

The prototype investigates whether an event-aware supervised autoscaling controller can provision service capacity ahead of a scheduled traffic increase. A Python controller monitors Prometheus, verifies scheduled-event evidence under a defined trust policy, computes a bounded demand estimate and replica target, checks safety constraints, and changes a Kubernetes Deployment through restricted RBAC. In a controlled synthetic-event demonstration, it requested one-to-three replica scaling at zero observed request rate; a timestamped Deployment observation showed three ready replicas 109 seconds before the event's scheduled opening. Single workload runs measured client P95 latencies of 17.38 ms for the proactive controller, 25.78 ms for the reactive controller, and 59.14 ms for Kubernetes HPA. Results support feasibility on the testbed but are not causal or statistically generalizable comparisons.

### 1. Problem and research objective

Conventional reactive autoscaling reacts to resource utilization or load after the increase has begun. It can therefore take time to provision additional ready capacity. The project objective is to use an incoming, verifiable event signal to request capacity before expected load arrives, under conservative safety and monitoring checks. The prototype targets a demonstration of pre-event readiness rather than production-grade prediction accuracy.

### 2. Testbed and architecture

- Host: Windows environment using Ubuntu WSL2 and single-node K3s.
- Workload: FastAPI portal Deployment `portal-amd64`, service `portal`, namespace `agentic-demo`.
- Observability: Prometheus with Kubernetes pod discovery and per-Pod targets.
- Controller: Python, Prometheus Monitoring Agent, event verifier, demand-estimate predictor, optimizer, safety gate, supervisor and kubectl scale executor.
- Security: controller's restricted ServiceAccount has Deployment read and scale permissions; admin kubectl handles experiment setup, Pod/Job observation and HPA lifecycle.
- Audit: SQLite decision records, newline-delimited JSON proactive timeline, Kubernetes Deployment readiness sampling, load Job logs.

**Control/data flow:** Scheduled event + trust-checked evidence → verification → Prometheus health/freshness and request/CPU/latency telemetry → demand estimate → replica optimization → safety gate/cooldown → Kubernetes scale request → readiness observation → audit.

### 3. Event verification and demand capacity

For external-event mode, the source is restricted to allowlisted HTTPS hosts and a citation excerpt must match fetched content and the exact UTC opening timestamp. The controlled demonstration uses a clearly labelled local fixture. Both paths check time-window conditions before event-based scale-out. The deterministic event-floor predictor takes the maximum of the reactive estimate and an explicitly supplied `estimated_peak_rps` during a verified lead window; the live prototype does **not** train an AI or machine-learning forecasting model. The demonstration sets the peak estimate to **12 RPS** and target throughput per replica to **5 RPS**, implying `ceil(12/5) = 3` replicas. Telemetry cannot be stale or nonfinite; when request rate is genuinely zero, a missing latency histogram observation can be treated as zero for the idle decision. The replica safety limit is one to six with a maximum addition of two per control cycle. Automatic scale-in has not been implemented.

### 4. Controlled pre-event scaling proof

The synthetic event had opening time **2026-11-23 01:41:36 UTC**. The controller requested changing from one to three replicas at **01:39:37.591790 UTC**, while observed RPS was zero. Deployment readiness samples recorded **1/1 at 01:39:35**, **1/3 at 01:39:41**, and **3/3 at 01:39:47 UTC**. Thus the first sampled full-capacity observation preceded the scheduled event by **109 seconds**. Precise instantaneous Pod readiness may have occurred between observations. This proof establishes advance resource provisioning, not real-world event forecasting or the outcome of a traffic event at that precise scheduled opening.

### 5. Experimental design

Three modes were compared: (a) proactive controller running ahead of traffic; (b) reactive controller activated at workload onset; (c) Kubernetes Horizontal Pod Autoscaler (HPA). Each completed a **180-second in-cluster load Job**, with successful/failed requests and client P95 duration recorded. Separate experiment setup reset the Deployment to one replica. HPA was removed following its experiment. Important differences: the proactive benchmark used **60 seconds** of pre-event lead; reactive and HPA runs used **45 seconds**; the HPA behavior and replica bounds are not necessarily identical to those of the agent; and proactive/reactive experiment runners were interrupted even though the Kubernetes load Jobs subsequently finished and their final results were recovered.

### 6. Results

| Metric | Proactive | Reactive | Kubernetes HPA |
|---|---:|---:|---:|
| Successful requests | 1,963 | 1,903 | 1,656 |
| Failed requests | 0 | 0 | 0 |
| Client P95 latency (ms) | 17.38 | 25.78 | 59.14 |
| Actual load duration (s) | 180.05 | 180.06 | 180.09 |
| Successful throughput (requests/s) | 10.90 | 10.57 | 9.20 |

Measured proactive client P95 was approximately **32.6% lower** than reactive and **70.6% lower** than HPA in these individual runs. These differences are descriptive, not confidence intervals, statistically validated effects, or evidence of universal superiority. Achieved request rate reflects the behavior of this specific in-cluster client and service workload and should not be treated as measured maximum server capacity.

### 7. Validation and safety

The project passed **18 Python automated tests** in the final run. The proactive execution used restricted Kubernetes RBAC and an audit timeline. The controlled pre-event demonstration recorded actual Kubernetes scale acceptance and later healthy 3/3 readiness. There was a temporary monitoring mismatch while new Pods appeared, which the controller blocked safely; later observations confirmed alignment across all three Prometheus targets. The HPA comparison was observed through admin credentials and HPA was absent after the run.

### 8. Limitations and threats to validity

1. Synthetic event only; external public event verification is implemented as a pathway but was not demonstrated with a genuine announcement in the recorded experiment.
2. Assumed 12 RPS peak; no learned demand forecast and no prediction-quality evaluation.
3. One run per policy; 60-second vs 45-second lead-time mismatch; controller/HPA configurations differ.
4. Interrupted experiment orchestration left partial readiness/agent traces for proactive and reactive runs; final load Job logs nonetheless exist.
5. Single-node K3s under WSL2; results cannot be extrapolated to production multi-node elasticity.
6. No automatic post-event scale-down, robust multi-event scheduling, distributed state management, cloud-cost objective, or formal fault-injection results.
7. The results do not establish that an actual traffic spike occurred at the pre-event demonstration's opening timestamp; the separate load benchmarks evaluate request handling.

### 9. Next steps

Validate a real trusted HTTPS event with verifiable timestamps; learn demand forecasts from historical portal demand and event metadata; perform repeated balanced, randomized benchmark runs with matched lead-time, resource requests and replica ceilings; measure end-to-end SLO compliance; add cooldown/hysteresis-based safe scale-in; repeat on a multi-node cluster; and include cost/resource-efficiency metrics and resilience tests.

### 10. Conclusion

The demonstrated result is a **working event-aware, safety-supervised Kubernetes controller** capable of preparing three ready Pods in advance of a controlled scheduled event while current request traffic is zero. The single-run performance observations favor proactive policy on this testbed but require matched repeated evaluation before broader claims can be made. The work provides a defensible foundation for improving real-event ingestion and demand forecasting in a future stage.

### Supporting files on developer's computer

`run/final-results/proactive-traffic.log`; `reactive-traffic.log`; `hpa-traffic.log`; `proactive-timeline.jsonl`; `proactive-readiness.log`. The bundled `finalize_results.py` reads these logs and generates a machine-readable and Markdown comparison without altering any source evidence.
