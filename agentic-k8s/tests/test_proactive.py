"""Use: PYTHONPATH=. .venv-linux/bin/python -m pytest -q tests/test_proactive.py"""
from datetime import datetime, timezone, timedelta
from types import SimpleNamespace
import pytest
from agentic_k8s.core import Metrics, VerificationAgent, Supervisor
from scripts.proactive_controller import (
    ControlledFetcher, ForecastWithEventFloor, generate_controlled_event, read_metrics, valid_targets
)

NOW = datetime(2026, 11, 23, 10, tzinfo=timezone.utc)

class Recorder:
    def __init__(self): self.values=[]
    def scale(self, value): self.values.append(value); return "accepted"


def test_proactive_zero_rps_requests_advance_replicas(tmp_path):
    event, minimum = generate_controlled_event(NOW, 150, 12)
    recorder=Recorder()
    supervisor=Supervisor(VerificationAgent({"controlled.example.org"}, ControlledFetcher(event.evidence_excerpt)), recorder, str(tmp_path/"audit.db"))
    supervisor.predictor=ForecastWithEventFloor(minimum)
    supervisor.optimiser.target_rps_per_replica=5
    result=supervisor.run(Metrics(0, .01, 0, 1), NOW, event, allow_mutation=True)
    assert result.status == "REQUESTED"
    assert result.verified and result.plan_mode == "PROACTIVE"
    assert result.desired_replicas == 3 and recorder.values == [3]


def test_untrusted_event_cannot_trigger_advance_scaling(tmp_path):
    event, minimum=generate_controlled_event(NOW, 150, 12)
    recorder=Recorder()
    supervisor=Supervisor(VerificationAgent(set(),ControlledFetcher(event.evidence_excerpt)),recorder,str(tmp_path/"audit.db"))
    supervisor.predictor=ForecastWithEventFloor(minimum)
    supervisor.optimiser.target_rps_per_replica=5
    result=supervisor.run(Metrics(0,.01,0,1), NOW,event,allow_mutation=False)
    assert not result.verified and result.plan_mode=="REACTIVE"
    assert result.status == "NO_CHANGE" and recorder.values==[]


def test_event_ahead_of_window_is_not_proactive():
    event, minimum=generate_controlled_event(NOW, 600, 12)
    verified=VerificationAgent({"controlled.example.org"},ControlledFetcher(event.evidence_excerpt)).verify(event,NOW)
    assert verified.approved
    assert ForecastWithEventFloor(minimum).forecast_rps(Metrics(0,.01,0,1),verified,event,NOW)==0


def test_idle_latency_nan_can_be_zero_when_rps_zero():
    class Monitor:
        app="portal"
        def query_scalar(self,q):
            if q.startswith("sum(rate("): return 0.
            if q.startswith("avg("): return .01
            raise ValueError("NaN")
    m=read_metrics(Monitor(),1)
    assert m.p95_latency_ms==0 and m.request_rps==0


def test_nonzero_rps_nan_latency_blocks():
    class Monitor:
        app="portal"
        def query_scalar(self,q):
            if q.startswith("sum(rate("): return 3.
            if q.startswith("avg("): return .01
            raise ValueError("NaN")
    with pytest.raises(ValueError): read_metrics(Monitor(),1)


def test_two_healthy_targets_accepted():
    class Monitor:
        def query_scalar(self,q):
            return 9. if "timestamp" in q else 2.
    assert valid_targets(Monitor(), 2)["healthy"] == 2
