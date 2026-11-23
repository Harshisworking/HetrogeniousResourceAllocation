"""Real kubectl scale adapter, disabled unless explicitly enabled by caller.

Requires an RBAC-restricted kubeconfig or Kubernetes ServiceAccount. This
adapter does not mutate pod specifications, nodes, or the HPA controller.
"""
import json
import subprocess


class KubectlScaleExecutor:
    def __init__(self, namespace: str, deployment: str, enabled: bool = False, context: str | None = None):
        self.namespace = namespace
        self.deployment = deployment
        self.enabled = enabled
        self.context = context
        if not namespace or not deployment or not all(c.isalnum() or c in "-." for c in namespace + deployment):
            raise ValueError("Invalid Kubernetes target")

    def _base(self):
        cmd = ["kubectl"]
        if self.context:
            cmd += ["--context", self.context]
        cmd += ["--namespace", self.namespace]
        return cmd

    def read_replicas(self) -> int:
        cmd = self._base() + ["get", "deployment", self.deployment, "-o", "json"]
        output = subprocess.run(cmd, text=True, capture_output=True, timeout=15, check=True)
        return int(json.loads(output.stdout)["spec"]["replicas"])

    def scale(self, desired: int) -> str:
        if not self.enabled:
            raise PermissionError("Kubernetes writes disabled; enable explicitly")
        if not 1 <= desired <= 6:
            raise ValueError("Target outside hard replica bounds")
        # kubectl scale calls the deployments/scale Kubernetes API subresource.
        cmd = self._base() + ["scale", "deployment", self.deployment, f"--replicas={desired}", "--current-replicas=" + str(self.read_replicas())]
        output = subprocess.run(cmd, text=True, capture_output=True, timeout=20, check=True)
        return "Kubernetes scale API requested: " + output.stdout.strip()
