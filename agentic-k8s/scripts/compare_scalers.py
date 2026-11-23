#!/usr/bin/env python3
"""Controlled three-policy comparison for the local agentic-demo K3s prototype.

Run from repository root. Admin kubectl is used ONLY for experiment setup and
read-only observation. Python agents use restricted KUBECONFIG. No HPA/agent
may control the Deployment simultaneously.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from urllib.request import urlopen

NS = 'agentic-demo'
DEP = 'portal-amd64'
JOB = 'portal-load'

def utc():
    return datetime.now(timezone.utc).isoformat()

def admin(*parts, check=True, timeout=30):
    command = ['sudo', 'k3s', 'kubectl', '-n', NS, *parts]
    return subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=check)

def restricted(*parts, timeout=20):
    return subprocess.run(['kubectl','-n',NS,*parts], capture_output=True, text=True, timeout=timeout, check=True)

def deployment():
    return json.loads(admin('get','deployment',DEP,'-o','json').stdout)

def status():
    dep=deployment()
    return {'desired':int(dep['spec'].get('replicas',0)),
            'ready':int(dep.get('status',{}).get('readyReplicas',0)),
            'available':int(dep.get('status',{}).get('availableReplicas',0))}

def assert_preflight():
    if not os.environ.get('KUBECONFIG'):
        raise RuntimeError('Set KUBECONFIG to your restricted controller YAML before running')
    if not Path('scripts/proactive_controller.py').is_file() or not Path('scripts/autonomous_controller.py').is_file():
        raise RuntimeError('Run from the existing agentic-k8s repository root')
    if not Path('k8s/portal-hpa.yaml').is_file() or not Path('k8s/portal-load-job.yaml').is_file():
        raise RuntimeError('Existing HPA and load-job manifests are missing')
    if not Path('.venv-linux/bin/python').is_file():
        raise RuntimeError('Ubuntu project virtual environment missing')
    try:
        with urlopen('http://127.0.0.1:9090/-/healthy',timeout=5) as r:
            if r.status != 200:
                raise RuntimeError('Prometheus unhealthy')
    except Exception as exc:
        raise RuntimeError('Prometheus port-forward on localhost:9090 unavailable') from exc
    if admin('get','hpa','portal-hpa',check=False).returncode == 0:
        raise RuntimeError('HPA exists: do not start a new experiment with another controller present')
    if admin('get','job',JOB,check=False).returncode == 0:
        job = json.loads(admin('get','job',JOB,'-o','json').stdout)
        if job.get('status',{}).get('active',0):
            raise RuntimeError('Previous load Job is still running. Wait for completion before starting')
    restricted('get','deployment',DEP)
    if restricted('auth','can-i','patch','deployment.apps/'+DEP,'--subresource=scale').stdout.strip() != 'yes':
        raise RuntimeError('Restricted controller account cannot patch scale')

def snapshot(out, phase, t0):
    try:
        cur = status()
        cur.update({'utc':utc(),'phase':phase,'elapsed_s':round(time.monotonic()-t0,3)})
    except Exception as exc:
        cur={'utc':utc(),'phase':phase,'error':str(exc)}
    with (out/'samples.jsonl').open('a') as f:
        f.write(json.dumps(cur)+'\n')
    return cur

def run_logged(cmd, path, env=None):
    stream = path.open('w')
    proc = subprocess.Popen(cmd, stdout=stream, stderr=subprocess.STDOUT, env=env)
    return proc,stream

def stop_process(proc,stream):
    if proc is not None and proc.poll() is None:
        proc.terminate()
        try: proc.wait(timeout=8)
        except subprocess.TimeoutExpired:
            proc.kill(); proc.wait()
    if stream is not None:stream.close()

def wait_until(target, out, phase, t0, active=None):
    while time.monotonic() < target:
        snapshot(out,phase,t0)
        if active is not None and active.poll() is not None:
            raise RuntimeError(f'{phase} controller exited early (exit code {active.returncode}); see controller.log')
        time.sleep(min(5,max(0,target-time.monotonic())))

def experiment(mode, lead, job_seconds, out):
    if mode not in ('proactive','reactive','hpa'):
        raise ValueError(mode)
    out.mkdir(parents=True,exist_ok=True)
    assert_preflight()
    hpa_started=False
    agent=None; agent_stream=None
    try:
        # Ensure previous Job is completed; preflight refused any active Job.
        admin('delete','job',JOB,'--ignore-not-found=true','--wait=true',timeout=90)
        admin('scale','deployment/'+DEP,'--replicas=1')
        admin('rollout','status','deployment/'+DEP,'--timeout=120s',timeout=130)
        # Observe actual readiness rather than relying on rollout status alone.
        reset_deadline = time.monotonic()+45
        while status()['ready'] != 1 or status()['desired'] != 1:
            if time.monotonic()>reset_deadline: raise RuntimeError('Deployment did not settle at 1/1')
            time.sleep(2)
        t0=time.monotonic()
        start_utc=datetime.now(timezone.utc)
        opening=start_utc+timedelta(seconds=lead)
        (out/'metadata.json').write_text(json.dumps({'mode':mode,'started_utc':start_utc.isoformat(),
                 'planned_load_utc':opening.isoformat(),'lead_seconds':lead,'workload_seconds':job_seconds,
                 'initial':status(), 'note':'Synthetic scheduled event; demand floor assumed, not learned.'},indent=2))
        snapshot(out,'baseline',t0)
        env=os.environ.copy();env['PYTHONPATH']='.'
        if mode=='proactive':
            # Start BEFORE the event. max 3 replicas under the controlled event estimate.
            cmd=['.venv-linux/bin/python','scripts/proactive_controller.py',
                 '--controlled-demo','--execute','--demo-event-in',str(lead),
                 '--estimated-peak-rps','12','--target-rps-per-replica','5',
                 '--cycles',str(max(8,(lead+job_seconds)//15+4)),'--interval','15',
                 '--timeline',str(out/'proactive-timeline.jsonl'),
                 '--audit',str(out/'audit.sqlite3')]
            agent,agent_stream=run_logged(cmd,out/'controller.log',env)
        elif mode=='hpa':
            # Activate before load; if it pre-scales during idle, that is captured.
            admin('apply','-f','k8s/portal-hpa.yaml')
            hpa_started=True
        # Controller in reactive mode is intentionally not started until load onset.
        wait_until(t0+lead,out,'pre_event',t0,agent if mode=='proactive' else None)
        # Capture readiness *before* load begins.
        snapshot(out,'at_event_before_load',t0)
        if mode=='reactive':
            cmd=['.venv-linux/bin/python','scripts/autonomous_controller.py',
                 '--cycles',str(job_seconds//15+4),'--interval','15',
                 '--target-rps-per-replica','5','--execute']
            agent,agent_stream=run_logged(cmd,out/'controller.log',env)
        admin('apply','-f','k8s/portal-load-job.yaml')
        snapshot(out,'load_started',t0)
        finish=t0+lead+job_seconds+45
        job_completed=False
        while time.monotonic()<finish:
            snapshot(out,'load',t0)
            j=json.loads(admin('get','job',JOB,'-o','json').stdout)
            if j.get('status',{}).get('succeeded',0)>=1:
                job_completed=True
                break
            if j.get('status',{}).get('failed',0)>=1:
                raise RuntimeError('Job failed: inspect '+str(out/'job_status.json'))
            print(
            f"[BENCHMARK] {time.strftime('%H:%M:%S')} "
            "Experiment running — collecting Kubernetes observations...",
            flush=True,
        )
        time.sleep(5)
        j=admin('get','job',JOB,'-o','json')
        (out/'job_status.json').write_text(j.stdout)
        (out/'traffic.log').write_text(admin('logs','job/'+JOB,timeout=30).stdout)
        snapshot(out,'finished',t0)
        if not job_completed: raise RuntimeError('Job did not complete within allotted window')
        print('Completed',mode,'\n'+(out/'traffic.log').read_text())
    finally:
        stop_process(agent,agent_stream)
        if hpa_started:
            try:
                (out/'hpa_final.yaml').write_text(admin('get','hpa','portal-hpa','-o','yaml').stdout)
            except Exception: pass
            admin('delete','hpa','portal-hpa','--ignore-not-found=true',check=False)
        # Deliberately do not reset the Deployment here: preserve the observed state.
        print('Final deployment:',status(),'\nResults:',out)

def main():
    p=argparse.ArgumentParser(description='Controlled in-cluster comparison runner (one mode at a time)')
    p.add_argument('--mode',choices=['proactive','reactive','hpa'],required=True)
    p.add_argument('--lead-seconds',type=int,default=120)
    p.add_argument('--workload-seconds',type=int,default=180)
    p.add_argument('--output',default=None)
    args=p.parse_args()
    if args.lead_seconds<45 or args.lead_seconds>300:
        p.error('lead seconds must be 45..300')
    # Existing load.py is hard-coded for 180 seconds; avoid implying configurable duration.
    if args.workload_seconds!=180:
        p.error('Existing benchmark_load.py workload is fixed at 180 seconds')
    out=Path(args.output or 'run/compare-v1/'+args.mode)
    if out.exists() and any(out.iterdir()):
        p.error('Output directory already contains files; choose another --output to preserve evidence')
    experiment(args.mode,args.lead_seconds,args.workload_seconds,out)

if __name__=='__main__':
    main()
