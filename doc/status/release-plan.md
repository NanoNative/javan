# Javan Release Plan

Last updated: 2026-07-21

This is the shipping view for the first boring native release. It chooses release gates
over callable-counting progress and records the evidence required before publication.

## Source-of-truth order

Read these files in order before choosing work:

1. `doc/status/release-plan.md`
2. `doc/status/roadmap-progress.md`
3. `doc/status/jdk-compatibility.md`
4. `doc/status/support-matrix.md`
5. `doc/spec/release.md`
6. `doc/spec/native-abi.md`

If they disagree, repair the disagreement before widening implementation.

## Execution Rules

These are release requirements, not aspirational cleanup:

- No fake support, placeholder, or "later" implementation in a release-critical path.
- Reject only shapes that are fundamentally not translatable or explicitly outside the
  frozen first-release contract. Unsupported reachable shapes fail clearly before code
  generation.
- Prefer a smaller real surface over wider nominal support. Performance, resilience,
  stability, and developer experience are product requirements.
- No known leak may remain in a generated application, native library, or binding.
- Prove release-critical behavior through public entrypoints and E2E/package smoke first;
  helper and unit tests support that proof.
- Keep the coverage target at least `95%` line and `90%` branch. Coverage is reported
  even when it does not block a build; a release-critical path without public proof still
  blocks shipment.
- Parallelize tests whenever isolation is defensible. Deliberately isolate shared-state
  tests instead of leaving broad serial defaults.
- Do not improve callable accounting through rejection bookkeeping unless it removes a
  product blocker or makes the release contract more precise.

## External Probe Boundary

Nano Service and TypeMap are external reality probes, not Javan product targets. They
may demonstrate whether Javan works for a real service or library, but production code,
generated code, fixtures, tests, support-ledger rows, and release claims in this repository
must not name either probe or its package names. Translate any discovered failure into a
neutral compiler/runtime scenario, prove that scenario through a public entrypoint, and
then retain the external project only as package-backed evidence.

## Work Selection and Reporting

Choose one primary gate and one blocker per slice. Priority order:

1. Failing required local package or verification gate.
2. Self-host, native-library release smoke, cross-platform package validation, or an
   external-reality blocker.
3. Memory, ownership, runtime-correctness, and local platform gaps.
4. Safe test concurrency improvement.
5. JDK support only when it removes one of the above blockers.
6. Rejection accounting only when it sharpens the release contract.

Before coding, state why the selected gate beats a JDK micro-slice, the expected files,
the proof command, and the architectural stop condition. Every handoff and PR reports:

- current work item: `done / total = percent`; left: `left / total = percent`
- release milestones: `done / 10 = percent`; left: `left / 10 = percent`
- runtime-safety slices: `done / 10 = percent`; left: `left / 10 = percent`
- capability/privacy/supply-chain slices: `done / 10 = percent`; left: `left / 10 = percent`
- the gate moved, evidence run, and remaining explicit blocker

## Old Work and PR Cadence

Before a new slice, inventory local branches/worktrees, uncommitted changes, open PRs,
recent merged/unmerged release-gate work, and temporary clones. Classify each item as
`adopt now`, `cherry-pick later`, `discard`, or `archive as context`. Do not continue
historical work merely because it exists; re-verify any reused change against this plan
and the current local gates, and extract only the minimal sound part of a stale PR.

Once a coherent gate-moving slice is green locally with package-backed or public-entrypoint
proof, open a small PR against `NanoNative/javan`; do not let durable decisions remain
only in a temporary checkout. A PR must state the release gate moved, real blocker removed,
proof passed, and remaining unsupported scope. Do not merge a red or partially proven
slice or open a bookkeeping-only PR unless it materially sharpens the release contract.

## Frozen first-release contract

- Published native packages: Linux x64 and Linux aarch64.
- Required local host gate: macOS aarch64, using the same package-backed verification
  script as the release matrix.
- Required local Linux gate: reproducible package-backed smoke in a provisioned Linux
  container or VM for each release architecture that is being claimed.
- Windows current-thread and worker-thread smoke remains a compatibility objective, but
  is not currently a release acceptance gate without reproducible local evidence.
- macOS package publication and remote macOS runners are deferred until their runner and
  package gate is reliable; no macOS artifact is claimed by the first release.
- Unsupported reachable shapes must fail clearly before native code generation.
- Generated apps, native libraries, and bindings must finish sanitizer and ownership
  probes with zero final live heap/root residue.
- External projects are compatibility probes only and do not define compiler support rows.
- Remote GitHub Actions is intentionally disabled during this early stage. Historical
  remote results are evidence only; no current release claim may depend on a remote run.
  Verify locally on macOS aarch64 and in reproducible Linux containers or VMs. Re-enable
  remote CI only after those local gates are stable and the workflow contract is reviewed.

## Current scoreboard

| Measure | Current value |
| --- | ---: |
| Release milestones fully closed | 0 / 10 = 0.0% |
| Release milestones left to close | 10 / 10 = 100.0% |
| Historical remote Linux package rows proven | 2 / 2 = 100.0% |
| Required local macOS package gate | proven locally |
| Required local Linux container/VM gate | not yet recorded |
| Roadmap rows fully done | 4 / 38 = 10.5% |
| Named support scenarios | 300 / 300 = 100.0% |
| Runtime-safety acceptance slices fully closed | 0 / 10 = 0.0% |
| Capability/privacy/supply-chain slices fully closed | 0 / 10 = 0.0% |

## Milestones

| ID | Gate | Exit condition |
| --- | --- | --- |
| R1 | Release contract freeze | Supported, unsupported, and out-of-scope families are explicit. |
| R2 | Verification and packaging source of truth | Local package smoke, concurrency, coverage reporting, and future remote CI re-enablement are stable. |
| R3 | Cross-platform runtime floor | Claimed Linux package rows and local macOS host proof are green with reproducible local evidence. |
| R4 | Self-host release proof | Packaged Javan rebuilds Javan and the rebuilt binary starts. |
| R5 | Memory and ownership proof | Generated apps, native libraries, and bindings pass sanitizer/ownership gates. |
| R6 | Native ABI release proof | C ABI v2, result/error ownership, and generated binding smoke are verified. |
| R7 | External reality probes | Neutral compiler behavior and package-backed probe outputs are deterministic. |
| R8 | Release metadata and installability | Versioning, checksums, Linux formula, and extracted-package checks pass. |
| R9 | Container publication | Released Linux assets drive reproducible image verification. |
| R10 | Release rehearsal | A dry run completes all required gates without publishing side effects. |

## Slice rules

Work exactly one blocker and one primary gate at a time. Prefer a failing required CI or
package gate, then self-host, ABI/ownership, cross-platform, and real-probe blockers.
JDK micro-slices are justified only when they remove one of those blockers. Every slice
needs a real code or workflow change, public-entrypoint or package-backed proof, and an
updated status statement when evidence changes.

Before coding, state the chosen gate, blocker, reason it beats a JDK micro-slice, expected
files, proof command, and the stop condition if the slice becomes architectural.

## Runtime-Safety Programme

Status: `Planned` (`0 / 10 = 0.0%` acceptance slices closed; `10 / 10 = 100.0%` left)

This programme prevents or makes diagnosable avoidable native-runtime failures. It supports
R3, R5, R6, and R10, not an eleventh release milestone. Each slice remains `Planned`
until its public-entrypoint proof and, where relevant, sanitizer evidence are recorded.

Read these code seams before selecting a slice:

- `src/main/java/javan/codegen/RuntimeSourceMemorySections.java`
- `src/main/java/javan/codegen/RuntimeSourceCoreSection.java`
- `src/main/java/javan/codegen/CCodegen.java`
- `src/main/java/javan/codegen/BytecodeToIR.java`
- `src/main/java/javan/verify/StaticVerifier.java`
- `src/main/java/javan/reporting/ExceptionReports.java`
- `src/main/java/javan/reporting/ThreadReports.java`
- `.github/scripts/sanitizer-suite.sh`

Contract boundaries:

- Javan cannot guarantee that a process never exhausts memory. The release contract is
  configured limits where available, collection only at valid safe points, deterministic
  failure, cleanup, and actionable evidence.
- Warnings never replace runtime guards. Warnings are advisory unless a shape is already
  outside the frozen support contract and is rejected deterministically.
- Do not silently queue or delay arbitrary `Thread.start()` calls. That changes Java
  semantics. Any admission/backpressure mechanism needs an explicit API and proven
  ordering, cancellation, shutdown, and reentrancy behavior.
- Preserve Java arithmetic semantics: ordinary integral overflow wraps. Guard only
  operations that would be undefined or differently defined in C, including zero
  division/remainder, `MIN_VALUE / -1`, and allocation-size conversions.
- Native bindings must validate checkable contracts such as null-plus-length and
  ownership. They cannot safely dereference arbitrary hostile foreign pointers.

| ID | Status | Scope | Primary proof and exit condition |
| --- | --- | --- | --- |
| RS1 | Planned | Define stable failure families for null receiver/input, array bounds/size, arithmetic, allocation, host thread creation, ownership, and unsupported runtime state. Preserve source context in app and library errors. | Public app and C-ABI probes assert code, detail, source location, and cleanup for every family; no generic panic remains for a covered guard. |
| RS2 | Planned | Audit every supported array creation, length, read, and write lowering. Guard null receiver, negative length/index, index equal to length, large lengths, and every supported primitive/object kind. | Generated native matrix covers every supported kind and failure; sanitizer is clean; lowering has no unchecked direct array access. |
| RS3 | Planned | Make allocation pressure observable before deterministic allocation failure: requested, live, peak, configured limit, and collection counters. | Limit, recovery, and unrecoverable-allocation probes prove exact behavior without unconfigured performance regression. |
| RS4 | Planned | Classify every generated C allocation/reallocation as tracked heap, bounded temporary, process-lifetime metadata, or FFI result, with a cleanup owner and overflow check. | Mechanical inventory has zero unclassified sites; forced allocation failures leave no managed-root or native-resource residue in the supported scope. |
| RS5 | Planned | Replace raw Java-to-C division, remainder, and size calculations with Java-compatible helpers that avoid C undefined behavior. | Native parity matrix covers signs, zero divisors, `MIN_VALUE / -1`, normal wrapping arithmetic, and allocation-size overflow under UBSan. |
| RS6 | Planned | Add stable, non-blocking warnings for provable local null, bounds, zero-divisor, and allocation-size hazards, each with source location and a concrete fix. | `javan check` warns only for provable cases; matching native executions still use guards; legal uncertain control flow is not rejected. |
| RS7 | Planned | Report thread-start sites, host-create failures, active/completed counts, and resource-limit evidence. Add bounded admission only behind an explicit supported executor contract. | Spawn-to-limit probe proves root rollback and zero residue; any admission mode proves ordering, cancellation, shutdown, and reentrancy. |
| RS8 | Planned | Extend ownership checks at library and binding boundaries: null-plus-length, result/free contract, retained input lifetime, error cleanup, and copied-result behavior. | C, Rust, Go, and Python package smoke covers success, invalid input, defined repeat-free behavior, error result, retained input, and concurrency with zero sanitizer residue. |
| RS9 | Planned | Add one truthful runtime-safety report section for enabled limits, guard-family coverage, runtime counters, warning counts, and unsupported hazards. | App and library report fixtures share a deterministic schema; build-only commands do not fabricate runtime values. |
| RS10 | Planned | Run hostile-path local release smoke: allocation pressure, repeated safe points, invalid arrays/arithmetic, thread-create failure, binding errors, and sanitizer execution. | Required local macOS and Linux container/VM gates show no sanitizer signature, live root residue, owned FFI residue, or unclassified runtime failure. |

Start with RS5: Java division/remainder and size conversion are a narrow native-correctness
risk with a crisp parity proof. Then take RS1, RS2, and RS3. Escalate before adding a
new scheduler, heap, public ABI version, cross-function null analysis, or more than five
production files for one slice.

## Capability, Privacy, and Supply-Chain Reporting Programme

Status: `Planned` (`0 / 10 = 0.0%` acceptance slices closed; `10 / 10 = 100.0%` left)

This programme makes the reachable behavior of applications and third-party libraries
inspectable. It supports R1, R7, R8, and R10. It is reporting, not a
replacement for runtime security controls or legal advice.

Read these code seams before selecting a slice:

- `src/main/java/javan/analysis/ReachabilityAnalyzer.java`
- `src/main/java/javan/compat/JdkCallSupport.java`
- `src/main/java/javan/compat/NetworkApiSupport.java`
- `src/main/java/javan/reporting/DependencyReports.java`
- `src/main/java/javan/reporting/ReportSummarizer.java`
- `src/main/java/javan/build/RuntimeFeatureSelection.java`
- `doc/spec/dependency-and-license-reports.md`
- `doc/spec/runtime-feature-selection.md`

Privacy and evidence rules:

- `known static` is a literal or deterministic constant flow proven reachable; it is not
  evidence that an operation occurred. `dynamic or unknown` is counted with call-site and
  dependency attribution, never guessed. `observed runtime` is opt-in, aggregate-only,
  and never replaces the static inventory.
- Do not record URL paths, queries, headers, request/response bodies, DNS-resolved
  addresses, environment values, property values, file contents, or logging messages.
- External network rows list only normalized known scheme, host, and explicit port.
  Loopback (`localhost`, `127.0.0.0/8`, `::1`) is excluded from the external-domain list
  and reported as a separate count.
- Filesystem rows classify read, write/create, delete, list, metadata, and execute.
  Redact sensitive absolute paths; exclude bundled/classpath resources from external-file
  lists and report their count separately.
- Logging rows count reachable static sites by level plus stdout/stderr. Unsupported
  logging libraries and unknown levels remain explicit; no message text or arguments are
  stored.
- Every capability row identifies the reachable class/method and, for foreign code, its
  artifact coordinate. This keeps real-library behavior visible without naming external
  probe projects in product code or tests.
- License evidence is not legal advice. Normalize only high-confidence SPDX evidence;
  configurable `allow`, `warn`, and opt-in `block` policy outcomes may state matching
  conditions, but never claim universal license compatibility.

| ID | Status | Scope | Primary proof and exit condition |
| --- | --- | --- | --- |
| CPS1 | Planned | Report linked/omitted runtime modules, the reachable call families that require them, and dependency attribution. | `javan check` and `javan report` emit one stable module-reason schema for an app and a reachable third-party class. |
| CPS2 | Planned | Statically inventory HTTP, outbound/inbound socket, TLS/certificate, and DNS APIs. Extract literal URI/host/port only through deterministic constant flow. | Fixtures prove external-domain list/count, loopback count, dynamic-endpoint count, caller location, and dependency owner without URL/query leakage. |
| CPS3 | Planned | Statically inventory filesystem/resource reads, writes/creates, deletes, lists, metadata, execute, classpath resources, and dynamic paths. | Fixtures prove action classification, redacted literal paths, internal-resource exclusion count, dynamic-path count, and foreign-library attribution. |
| CPS4 | Planned | Inventory process execution, environment keys, property keys, shutdown/exit, native/foreign boundaries, reflection, dynamic loading, and unsupported dangerous APIs. | Every reachable capability has a stable category and source/dependency owner; unknown arguments are counted, never captured. |
| CPS5 | Planned | Inventory logging and console sites by recognized level, stdout/stderr, unknown level, and unsupported logger library. | Fixtures prove exact level counts and redaction for console and supported logger signatures. |
| CPS6 | Planned | Emit deterministic `capabilities.json` and `capabilities.md`, and show compact counts in the unified report. | Schema and report fixtures prove ordering, provenance, known/dynamic/observed labels, required counts, and no secret/payload leakage. |
| CPS7 | Planned | Add an opt-in aggregate runtime observation profile for generated apps and libraries. | Controlled run proves observations are absent by default, bounded in memory, redacted, and clearly distinct from static evidence. |
| CPS8 | Planned | Improve license evidence with high-confidence SPDX normalization, original evidence, direct/transitive/reachable state, checksums, and distribution context. | Fixtures cover metadata, bundled-file, unknown, ambiguous, local-path, direct, and transitive evidence; ambiguity remains `unknown`. |
| CPS9 | Planned | Add project-configured license policy matching with `allow`, `warn`, and opt-in `block` outcomes. | Policy fixture proves allowed/warn/blocked/unknown results, reproducible evidence, and the non-legal-advice boundary. |
| CPS10 | Planned | Emit a deterministic SBOM and package gate covering identity, version/checksum, reachability, license evidence/policy, duplicates/conflicts, and report schema. | Package-backed local verification proves the SBOM matches the archive and contains no secret, sensitive absolute path, or payload. |

Start with CPS1 through CPS6: they reuse reachability and reporting evidence without
widening runtime support. CPS7 changes the generated runtime and needs ownership review.
CPS9 stays warning-first until policy format and distribution context are explicit. Stop
and escalate before telemetry, automatic license downloads, content capture, claims of
complete dynamic analysis, a universal license matrix, or blocks based on ambiguous
license metadata.

## Required proof

Local host gate:

```sh
.github/scripts/verify-release.sh
```

Run the following inside the provisioned Linux container or VM for the matching host
architecture:

```sh
JAVAN_PACKAGE_TARGET=linux-x64 sh .github/scripts/verify-ci-package-smoke.sh
```

Use `linux-aarch64` on a matching Linux aarch64 host. While remote CI is disabled, these
local gates are the release evidence and historical remote results must not be presented
as fresh validation. A PR must explain which gate moved, which blocker was removed, what
package/public-entrypoint proof passed, and what remains explicitly unsupported. Never
merge a red or partially proven release slice.

## Stop conditions

Stop only for a real external blocker, an architectural decision requiring escalation, or
remote permissions/state that prevent safe progress. A merged PR is not a stop condition.
