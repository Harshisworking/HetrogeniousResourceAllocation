"""Small, auditable event-aware Kubernetes control loop.

Safety: this module does not issue writes without an injected executor. Event
verification requires actual fetched text from a configured URL; the example
fixture is explicitly marked CONTROLLED, not an official-source verification.
"""
from __future__ import annotations
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, build_opener, HTTPRedirectHandler
from html.parser import HTMLParser
import json
import math
import sqlite3
from typing import Protocol


@dataclass(frozen=True)
class Event:
    title: str
    source_url: str
    service: str
    opening_utc: str
    evidence_excerpt: str
    expected_multiplier: float = 2.0


@dataclass(frozen=True)
class Metrics:
    request_rps: float
    cpu_utilization: float
    p95_latency_ms: float
    replicas: int


@dataclass(frozen=True)
class Verification:
    approved: bool
    reason: str
    event_time: datetime | None = None


@dataclass(frozen=True)
class Plan:
    desired_replicas: int
    reason: str
    mode: str
    projected_rps: float


@dataclass(frozen=True)
class Outcome:
    status: str
    desired_replicas: int
    current_replicas: int
    reason: str
    verified: bool
    plan_mode: str


class Fetcher(Protocol):
    def fetch(self, url: str) -> str: ...


class _TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.fragments: list[str] = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in {"script", "style"}:
            self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden:
            self.fragments.append(data)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Source redirects are not permitted")


class HttpsEvidenceFetcher:
    """Only HTTPS with configured hostname; no redirects. Set allowlisted hosts.

    For deployment, network egress restrictions must additionally enforce the same
    trust policy. URL allowlisting alone is not a complete SSRF defense.
    """
    def __init__(self, allowed_hosts: set[str]):
        self.allowed_hosts = {h.lower() for h in allowed_hosts}

    def fetch(self, url: str) -> str:
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.hostname not in self.allowed_hosts or parsed.username or parsed.password or parsed.port not in (None, 443):
            raise ValueError("Source URL is not explicitly allowlisted HTTPS")
        req = Request(url, headers={"User-Agent": "AgenticResearchVerifier/0.1"})
        with build_opener(_NoRedirect).open(req, timeout=5) as response:
            if response.status != 200:
                raise ValueError("Source HTTP response was not 200")
            data = response.read(256_001)
            if len(data) > 256_000:
                raise ValueError("Source page too large")
            typ = response.headers.get_content_type()
            if typ not in {"text/plain", "text/html"}:
                raise ValueError("Unsupported source content type")
            txt = data.decode("utf-8", errors="replace")
            if typ == "text/html":
                parser = _TextExtractor()
                parser.feed(txt)
                txt = " ".join(parser.fragments)
            return txt


def parse_utc(value: str) -> datetime:
    moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if moment.tzinfo is None:
        raise ValueError("Opening time must contain an explicit time zone")
    return moment.astimezone(timezone.utc)


class VerificationAgent:
    def __init__(self, trusted_hosts: set[str], fetcher: Fetcher):
        self.trusted_hosts = {h.lower() for h in trusted_hosts}
        self.fetcher = fetcher

    def verify(self, event: Event | None, now: datetime) -> Verification:
        if event is None:
            return Verification(False, "No event supplied")
        if not event.title or not event.service or not event.evidence_excerpt.strip():
            return Verification(False, "Missing event title, service, or excerpt")
        url = urlparse(event.source_url)
        if url.scheme != "https" or url.hostname not in self.trusted_hosts or url.username or url.password or url.port not in (None, 443):
            return Verification(False, "Untrusted source URL")
        try:
            when = parse_utc(event.opening_utc)
        except (ValueError, TypeError):
            return Verification(False, "Invalid or timezone-free service opening")
        if not now < when <= now + timedelta(days=7):
            return Verification(False, "Opening must be in the next seven days")
        if not (1 <= event.expected_multiplier <= 5):
            return Verification(False, "Invalid impact multiplier")
        try:
            source = " ".join(self.fetcher.fetch(event.source_url).split())
        except Exception as ex:
            return Verification(False, f"Evidence fetch failed: {type(ex).__name__}")
        excerpt = " ".join(event.evidence_excerpt.split())
        if excerpt not in source:
            return Verification(False, "Evidence excerpt absent from fetched source")
        # Timestamp matching is intentionally strict, not semantic NLP inference.
        if event.opening_utc not in excerpt:
            return Verification(False, "Exact opening timestamp absent from cited excerpt")
        return Verification(True, "Exact source excerpt and opening timestamp confirmed", when)


class PredictionAgent:
    """Deterministic baseline; a trained forecaster is still future work."""
    def forecast_rps(self, metrics: Metrics, verified: Verification, event: Event | None, now: datetime, lead_seconds: int = 300) -> float:
        if verified.approved and verified.event_time and event:
            time_to = (verified.event_time - now).total_seconds()
            if 0 <= time_to <= lead_seconds:
                return metrics.request_rps * event.expected_multiplier
        return metrics.request_rps


class OptimisationAgent:
    def __init__(self, target_rps_per_replica: float = 25.0, target_cpu: float = .70):
        self.target_rps_per_replica = target_rps_per_replica
        self.target_cpu = target_cpu

    def propose(self, metrics: Metrics, forecast_rps: float, event_approved_in_lead_window: bool) -> Plan:
        required_by_traffic = max(1, math.ceil(forecast_rps / self.target_rps_per_replica))
        required_by_cpu = math.ceil(metrics.replicas * metrics.cpu_utilization / self.target_cpu)
        # Response to observed load doesn't require an approved external event.
        proposed = max(1, required_by_traffic, required_by_cpu)
        if event_approved_in_lead_window:
            mode = "PROACTIVE"
        else:
            mode = "REACTIVE"
        # Deliberately no automatic scale-in yet; would need temporal stability and SLO checks.
        proposed = max(metrics.replicas, proposed)
        return Plan(proposed, "Forecast, observed traffic and CPU target", mode, forecast_rps)


class SafetyAgent:
    def __init__(self, min_replicas=1, max_replicas=6, max_step=2):
        self.min_replicas, self.max_replicas, self.max_step = min_replicas, max_replicas, max_step

    def approve(self, metrics: Metrics, plan: Plan, allow_mutation: bool) -> tuple[bool, str]:
        if not all(math.isfinite(n) for n in [metrics.request_rps, metrics.cpu_utilization, metrics.p95_latency_ms, plan.projected_rps]):
            return False, "Non-finite input"
        if metrics.request_rps < 0 or not 0 <= metrics.cpu_utilization <= 1 or metrics.p95_latency_ms < 0 or metrics.replicas < self.min_replicas:
            return False, "Invalid observed metrics"
        if not self.min_replicas <= plan.desired_replicas <= self.max_replicas:
            return False, "Replica bounds exceeded"
        if plan.desired_replicas - metrics.replicas > self.max_step:
            return False, "Scaling change exceeds max step"
        if not allow_mutation:
            return True, "Approved for dry-run only"
        return True, "Approved for requested execution mode"


class Executor(Protocol):
    def scale(self, desired: int) -> str: ...


class Supervisor:
    def __init__(self, verifier: VerificationAgent, executor: Executor, audit_path: str, safety: SafetyAgent | None = None):
        self.verifier = verifier
        self.predictor = PredictionAgent()
        self.optimiser = OptimisationAgent()
        self.safety = safety or SafetyAgent()
        self.executor = executor
        self.audit_path = audit_path

    def run(self, metrics: Metrics, now: datetime, event: Event | None = None, allow_mutation=False) -> Outcome:
        if now.tzinfo is None:
            raise ValueError("Clock must be timezone-aware")
        # Even if verification fails, telemetry-based reactive decisions remain possible.
        verification = self.verifier.verify(event, now)
        forecast = self.predictor.forecast_rps(metrics, verification, event, now)
        in_window = bool(verification.approved and verification.event_time and 0 <= (verification.event_time - now).total_seconds() <= 300)
        plan = self.optimiser.propose(metrics, forecast, in_window)
        approved, reason = self.safety.approve(metrics, plan, allow_mutation)
        if not approved:
            outcome = Outcome("BLOCKED", plan.desired_replicas, metrics.replicas, reason, verification.approved, plan.mode)
        elif plan.desired_replicas == metrics.replicas:
            outcome = Outcome("NO_CHANGE", plan.desired_replicas, metrics.replicas, "No scaling necessary", verification.approved, plan.mode)
        elif not allow_mutation:
            outcome = Outcome("DRY_RUN", plan.desired_replicas, metrics.replicas, "No Kubernetes write requested", verification.approved, plan.mode)
        else:
            try:
                result = self.executor.scale(plan.desired_replicas)
                outcome = Outcome("REQUESTED", plan.desired_replicas, metrics.replicas, result, verification.approved, plan.mode)
            except Exception as ex:
                outcome = Outcome("EXECUTION_FAILED", plan.desired_replicas, metrics.replicas, f"{type(ex).__name__}: {ex}", verification.approved, plan.mode)
        self._audit(now, event, verification, metrics, plan, outcome)
        return outcome

    def _audit(self, now, event, verification, metrics, plan, outcome):
        parent = Path(self.audit_path).parent
        parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(self.audit_path) as db:
            db.execute("CREATE TABLE IF NOT EXISTS decisions (id INTEGER PRIMARY KEY, created_utc TEXT NOT NULL, event_title TEXT, verification TEXT NOT NULL, metrics TEXT NOT NULL, plan TEXT NOT NULL, outcome TEXT NOT NULL)")
            db.execute("INSERT INTO decisions (created_utc,event_title,verification,metrics,plan,outcome) VALUES (?,?,?,?,?,?)", (
                now.isoformat(), event.title if event else None, json.dumps({"approved":verification.approved,"reason":verification.reason}),
                json.dumps(asdict(metrics)), json.dumps(asdict(plan)), json.dumps(asdict(outcome))))
