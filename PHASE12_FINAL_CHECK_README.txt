Phase 12: evidence-gated runtime and scale-in execution audit

Overwrite only the included Java files. The existing 16-scenario fixture matrix stays synthetic and unchanged.

New: ControllerDrivenCloudSimDemo.runWithEvidence(...): calls the real Phase 7 EvidenceBackedTimingGate, converts a verified future service-opening timestamp to a caller-defined simulation epoch, and passes a real EventSignal to the runtime controller. Denied evidence falls back to reactive decisions. This requires a controlled clock/epoch to fit the 0..120s demonstration window. No real cloud VMs are created.

Updated audit: distinguish policy SCALE_IN decisions, broker.destroyVm(extra) API invocations and VM state observed on a later tick. For the three sustained cases, use the revised CSV to decide whether SCALE_IN was not enacted or enacted but not observed. Do not call shutdown destruction successful scale-in.

Run tests:
  mvn clean test "-Dtest=EvidenceBackedRuntimeSignalTest,VmLifecycleAuditTest"
  mvn clean test
  mvn compile "org.codehaus.mojo:exec-maven-plugin:3.6.4:java" "-Dexec.mainClass=com.harsh.cloudsim.benchmark.MultiWorkloadEvaluation"

Inspect target/phase12-vm-lifecycle-audit.csv. Do not commit before validation.
