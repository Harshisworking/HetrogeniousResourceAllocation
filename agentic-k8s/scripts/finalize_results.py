#!/usr/bin/env python3
"""Read actual experiment evidence and generate a defensible summary. Stdlib only.

Never generates or edits raw evidence. Missing evidence yields a non-zero exit status.
"""
import argparse
import csv
import json
import math
from datetime import datetime, timezone
from pathlib import Path
import re

MODES = ("proactive", "reactive", "hpa")
NAMES = {"proactive":"Proactive agent", "reactive":"Reactive agent", "hpa":"Kubernetes HPA"}
FILES = {"proactive":"proactive-traffic.log", "reactive":"reactive-traffic.log", "hpa":"hpa-traffic.log"}
FIELDS = ("successful_requests", "failed_requests", "client_p95_ms", "actual_elapsed_seconds")

def parse_traffic(path):
    raw = path.read_text(encoding="utf-8")
    values = {}
    for key in FIELDS:
        matched = re.findall(rf"(?m)^\s*{re.escape(key)}\s*:\s*([^\s]+)\s*$", raw)
        if len(matched) != 1:
            raise ValueError(f"{path}: expected exactly one {key}; found {len(matched)}")
        try:
            values[key] = float(matched[0])
        except ValueError as exc:
            raise ValueError(f"{path}: invalid {key}") from exc
        if not math.isfinite(values[key]) or values[key] < 0:
            raise ValueError(f"{path}: invalid nonfinite/negative {key}")
    for key in ("successful_requests","failed_requests"):
        if not values[key].is_integer():
            raise ValueError(f"{path}: {key} must be integer")
        values[key] = int(values[key])
    if values["actual_elapsed_seconds"] == 0:
        raise ValueError(f"{path}: elapsed seconds cannot be zero")
    values["successful_rps"] = values["successful_requests"] / values["actual_elapsed_seconds"]
    return values

def parse_timeline(path):
    records=[]
    for index,line in enumerate(path.read_text(encoding="utf-8").splitlines(),1):
        if line.strip():
            try: records.append(json.loads(line))
            except json.JSONDecodeError as exc: raise ValueError(f"{path}, line {index}: invalid JSON") from exc
    requested=[r for r in records if r.get("status")=="REQUESTED" and r.get("mode")=="PROACTIVE"]
    if not requested:
        return {"proactive_request_found":False,"note":"No successful proactive REQUESTED record found"}
    r=requested[0]
    opening = datetime.fromisoformat(r["event_opening_utc"].replace("Z","+00:00"))
    requested_at = datetime.fromisoformat(r["scale_requested_utc"].replace("Z","+00:00"))
    return {"proactive_request_found":True,"scale_requested_utc":requested_at.isoformat(),
        "event_opening_utc":opening.isoformat(),"scale_request_lead_seconds":round((opening-requested_at).total_seconds(),3),
        "observed_request_rps_at_request":r.get("metrics",{}).get("request_rps"),
        "replicas_before":r.get("replicas_before"),"desired_replicas":r.get("desired"),
        "controlled_demo":r.get("controlled_demo")}

def parse_readiness(path, event_opening_utc):
    if not event_opening_utc:
        return {"ready_3_before_event":None,"note":"No event opening timestamp available"}
    opening = datetime.fromisoformat(event_opening_utc.replace("Z","+00:00"))
    previous_time=None
    observations=[]
    for line in path.read_text(encoding="utf-8").splitlines():
        stamp = re.match(r"^===== (\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ) =====$", line.strip())
        if stamp:
            previous_time = datetime.fromisoformat(stamp.group(1).replace("Z","+00:00"))
        match = re.match(r"^portal-amd64\s+(\d+)/(\d+)\s+", line.strip())
        if previous_time is not None and match:
            ready,desired = int(match.group(1)),int(match.group(2))
            observations.append((previous_time,ready,desired))
    matches=[t for t,ready,desired in observations if t<opening and ready>=3 and desired>=3]
    if not matches:
        return {"ready_3_before_event":False,"note":"No timestamped 3/3 pre-event evidence found"}
    first=min(matches)
    return {"ready_3_before_event":True,"first_three_ready_utc":first.isoformat(),
            "three_ready_lead_seconds":round((opening-first).total_seconds(),3),
            "sampled_observation_count":len(observations),
            "note":"First sampled 3/3 Deployment status; precise readiness instant may be earlier"}

def build(evidence,output):
    metrics={m:parse_traffic(evidence / FILES[m]) for m in MODES}
    for optional in ("proactive-timeline.jsonl", "proactive-readiness.log"):
        if not (evidence/optional).is_file():
            raise FileNotFoundError(f"Required evidence missing: {evidence / optional}")
    timeline=parse_timeline(evidence/"proactive-timeline.jsonl")
    readiness=parse_readiness(evidence/"proactive-readiness.log",timeline.get("event_opening_utc"))
    summary={"generated_utc":datetime.now(timezone.utc).isoformat(),"evidence_directory":str(evidence),
             "data_origin":"Locally saved experiment logs, not a model-generated reconstruction",
             "benchmarks":metrics,"proactive_timeline":timeline,"readiness":readiness,
             "caveats":["Single run per policy: no confidence intervals or causal claim",
                        "Proactive lead 60 s vs reactive/HPA lead 45 s in reported benchmark runs",
                        "Synthetic event; explicit operator-estimated 12 RPS floor; no trained forecasting model",
                        "Client-generated load and resource differences may influence measured outcomes",
                        "K3s on a single node; not a multi-node production cluster",
                        "Interrupted proactive/reactive orchestrator runs: workload Jobs completed, but complete controller traces may be absent"]}
    output.mkdir(parents=True,exist_ok=True)
    (output/"summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    with (output/"metrics.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.writer(f);w.writerow(["policy",*FIELDS,"successful_rps"])
        for m in MODES: w.writerow([m,*[metrics[m][k] for k in FIELDS],round(metrics[m]["successful_rps"],4)])
    base=metrics["proactive"]["client_p95_ms"]
    r=metrics["reactive"]["client_p95_ms"]
    h=metrics["hpa"]["client_p95_ms"]
    lines=["# Measured Results — Event-Aware Agentic Kubernetes Autoscaling","",
           "**Evidence-derived analysis.** Parsed from the preserved logs in `run/final-results/`.","",
           "| Policy | Success | Failed | Client P95 (ms) | Successful requests/s |",
           "|---|---:|---:|---:|---:|",]
    for m in MODES:
        s=metrics[m]
        lines.append(f"| {NAMES[m]} | {s['successful_requests']:,} | {s['failed_requests']:,} | {s['client_p95_ms']:.2f} | {s['successful_rps']:.2f} |")
    lines += ["",f"Single-run proactive P95 was {(r-base)/r*100:.1f}% lower than reactive and {(h-base)/h*100:.1f}% lower than HPA.","",
              "## Pre-event capacity evidence", ""]
    if timeline.get("proactive_request_found"):
        lines.append(f"A controlled synthetic event generated a proactive Kubernetes scale request at `{timeline['scale_requested_utc']}` for event opening `{timeline['event_opening_utc']}`. Observed RPS at request: `{timeline.get('observed_request_rps_at_request')}`. This fixture was **not** an independently verified public announcement.")
        lines.append("")
    if readiness.get("ready_3_before_event"):
        lines.append(f"The first timestamped observation of 3/3 ready replicas was `{readiness['first_three_ready_utc']}`, **{readiness['three_ready_lead_seconds']:.0f} seconds before** the scheduled opening. Sampling does not establish the exact readiness transition instant.")
    else:
        lines.append("The supplied readiness log did not establish a sampled 3/3 pre-event observation.")
    lines += ["","## Interpretation and limitations","",
        "These findings are descriptive, from one run per strategy. They do not prove that the proactive policy consistently outperforms reactive or HPA. Proactive used a 60-second pre-event lead, versus 45 seconds in the reactive and HPA benchmark runs. The HPA and agent policies also have differing configuration details. The benchmark used a controlled synthetic event and a manually specified demand estimate. Actual external-event ingestion, machine-learned prediction, automated scale-in, multi-node validation and repeated statistically controlled trials remain future work.","",
        "The interrupted proactive/reactive orchestration runs leave only partial controller observation histories, even though their Kubernetes load Jobs completed and final request metrics were recovered.","",
        "## Source evidence", "", "- `proactive-traffic.log`, `reactive-traffic.log`, `hpa-traffic.log`", "- `proactive-timeline.jsonl`, `proactive-readiness.log`", ""]
    (output/"RESULTS.md").write_text("\n".join(lines),encoding="utf-8")
    return summary

def main():
    p=argparse.ArgumentParser(description="Summarise existing Kubernetes benchmark logs; does not alter them")
    p.add_argument("--evidence",type=Path,default=Path("run/final-results"))
    p.add_argument("--output",type=Path,default=Path("run/final-analysis"))
    args=p.parse_args()
    try: summary=build(args.evidence,args.output)
    except (FileNotFoundError,ValueError,KeyError) as exc: p.error(str(exc))
    print("Final analysis saved in",args.output)
    for m in MODES: print(f"{m:10s} p95={summary['benchmarks'][m]['client_p95_ms']:.2f}ms successes={summary['benchmarks'][m]['successful_requests']}")
    print("Pre-event 3/3 verified:", summary["readiness"].get("ready_3_before_event"))

if __name__=="__main__": main()
