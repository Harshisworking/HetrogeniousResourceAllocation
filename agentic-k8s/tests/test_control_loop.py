from datetime import datetime, timedelta, timezone
import sqlite3
import pytest
from agentic_k8s.core import Event, Metrics, VerificationAgent, Supervisor, SafetyAgent
from agentic_k8s.kube import KubectlScaleExecutor


NOW = datetime(2026, 11, 22, tzinfo=timezone.utc)


class Fetcher:
    def __init__(self, text): self.text = text
    def fetch(self, url): return self.text


class Recorder:
    def __init__(self): self.calls = []
    def scale(self, desired): self.calls.append(desired); return "request accepted"


def event(offset=120, excerpt=None, host="trusted.example.org"):
    when = (NOW + timedelta(seconds=offset)).isoformat()
    excerpt = excerpt if excerpt is not None else f"Registration opens at {when}"
    return Event("Admissions", f"https://{host}/notice", "portal", when, excerpt, 2.0)


def supervisor(tmp_path, text, executor=None):
    return Supervisor(VerificationAgent({"trusted.example.org"}, Fetcher(text)), executor or Recorder(), str(tmp_path / "audit.db"))


def test_verified_event_permits_proactive_dry_run(tmp_path):
    e = event()
    s = supervisor(tmp_path, e.evidence_excerpt)
    o = s.run(Metrics(30,.30,60,2), NOW, e)
    assert o.verified and o.plan_mode == "PROACTIVE" and o.status == "DRY_RUN" and o.desired_replicas == 3


def test_invalid_source_blocks_early_scale(tmp_path):
    e = event(host="untrusted.invalid")
    o = supervisor(tmp_path, e.evidence_excerpt).run(Metrics(30,.3,60,2), NOW, e)
    assert not o.verified and o.plan_mode == "REACTIVE" and o.status == "NO_CHANGE"


def test_excerpt_must_match_source(tmp_path):
    e = event()
    o = supervisor(tmp_path, "different source text").run(Metrics(30,.3,60,2), NOW, e)
    assert not o.verified and o.status == "NO_CHANGE"


def test_exact_timestamp_must_appear_in_excerpt(tmp_path):
    e = event(excerpt="Admissions are open sometime tomorrow")
    o = supervisor(tmp_path, e.evidence_excerpt).run(Metrics(30,.3,60,2), NOW, e)
    assert not o.verified and o.status == "NO_CHANGE"


def test_no_event_uses_reactive_telemetry(tmp_path):
    o = supervisor(tmp_path, "").run(Metrics(80,.9,500,2), NOW)
    assert o.plan_mode == "REACTIVE" and o.desired_replicas > 2 and o.status == "DRY_RUN"


def test_dry_run_never_invokes_executor(tmp_path):
    r = Recorder()
    o = supervisor(tmp_path, event().evidence_excerpt, r).run(Metrics(30,.3,60,2), NOW, event())
    assert o.status == "DRY_RUN" and r.calls == []


def test_write_requires_explicit_switch(tmp_path):
    r = Recorder()
    o = supervisor(tmp_path, event().evidence_excerpt, r).run(Metrics(30,.3,60,2), NOW, event(), allow_mutation=True)
    assert o.status == "REQUESTED" and r.calls == [3]


def test_overscale_rejected(tmp_path):
    o = supervisor(tmp_path, "").run(Metrics(1000,.9,500,2), NOW)
    assert o.status == "BLOCKED"


def test_sqlite_audit_recorded(tmp_path):
    s = supervisor(tmp_path, "")
    s.run(Metrics(10,.2,15,1), NOW)
    with sqlite3.connect(tmp_path / "audit.db") as db:
        assert db.execute("SELECT COUNT(*) FROM decisions").fetchone()[0] == 1


def test_execute_adapter_disabled_by_default():
    with pytest.raises(PermissionError):
        KubectlScaleExecutor("agentic-demo", "portal-amd64").scale(3)


def test_invalid_metrics_blocked(tmp_path):
    o = supervisor(tmp_path, "").run(Metrics(-1,.5,50,2), NOW)
    assert o.status == "BLOCKED"


def test_outside_lead_window_remains_reactive(tmp_path):
    e = event(offset=3600)
    o = supervisor(tmp_path, e.evidence_excerpt).run(Metrics(30,.3,60,2), NOW, e)
    assert o.verified and o.plan_mode == "REACTIVE" and o.status == "NO_CHANGE"
