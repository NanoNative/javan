# JavaN Roadmap

Reviewed against main `0bd68804` and the linked issue evidence on 2026-09-20.
This is a delivery map, not a new release certification. Specifications own behavior;
[verification history](verification.md) owns earlier measurements and accepted runs.

## Direction

Keep the existing first-release priority: usable native packages for Linux x64,
Linux ARM64 and macOS ARM64, with Java support expanded through complete, tested
behavior slices. macOS x64 and Windows package support remain outside that release scope.
[REL-001](specs/release.md#requirements-and-acceptance) owns this boundary.

Normal Java source and build outputs remain the input. The compiler selects supported
behavior automatically, explains unsupported reachable behavior early, and preserves
Java runtime semantics. C remains the production backend.
[COMP-001 to COMP-005](specs/compiler.md#requirements) and
[ADR 0003](adr/0003-static-native-compilation.md) own that direction.

## Read The Status Correctly

- **Done:** the named scope has linked acceptance evidence; not universal Java support.
- **Partial:** some requirements are proven and the remaining boundary is explicit.
- **In progress:** active work with an identified unfinished acceptance step.
- **Planned:** scoped future work; no implementation completion claimed.
- **Blocked:** a named decision or missing evidence prevents the dependent step.
- **Dismissed:** excluded from the current scope.

GitHub issue state and historical CI runs are evidence, not live status dashboards.
Retain the existing limit of two implementation slices and one CI/performance slice in progress.

## Current Sequence

The earlier release-foundation queue is closed; its IDs and evidence remain
[below](#completed-release-foundation). The active sequence is bounded:

| Milestone | Outcome / requirements | Dependency | Exit evidence / status |
| --- | --- | --- | --- |
| REL-ASSETS-01 — separate public assets from rehearsal evidence | Correct the upload-boundary violation of [REL-005](specs/release.md#requirements-and-acceptance). | Existing release workflow and surface tests. | **Done:** [local workflow regression](verification.md#release-asset-selection) proves exact selection, rejection and retry behavior; candidate-level proof remains separate. |
| REL-CANDIDATE-01 — verify one candidate | Establish whether one clean commit meets [REL-001/002/004](specs/release.md#requirements-and-acceptance) and [ABI-003](specs/native-abi.md#requirements-and-acceptance) on every required host. | Candidate containing REL-ASSETS-01; matching host toolchains and package/import/rehearsal harnesses. | **Partial:** harnesses and historical proof exist, but a current candidate evidence set is not recorded. Complete the [candidate procedure](specs/release.md#candidate-evidence-procedure). |
| PERF-CANDIDATE-01 — recheck build baselines | Measure the accepted candidate through [OPT-004](specs/analysis-and-optimization.md#requirements-and-acceptance). | Verified artifacts from REL-CANDIDATE-01. | **Planned:** reuse [Package Build Baselines](specs/testing.md#ci-execution), compare with [historical measurements](verification.md#build-baselines), and record conditions/limitations without inventing a pass threshold. |

## Next Ready Milestone

**REL-CANDIDATE-01 is next.** The public upload boundary has local regression proof;
candidate selection still requires a clean commit containing the reviewed fix.
Select one clean candidate containing the fix and execute REL-CANDIDATE-01. Record its
full SHA and every required target's linked evidence; missing hosts or artifacts remain pending,
not assumed passes. Candidate selection and runner availability are execution inputs, not
unresolved architecture choices. A failed proof becomes a bounded regression/fix under the
candidate procedure before another compatibility feature is selected.

These milestones perform no publication. Release dispatch is a separate decision under
[REL-003/004](specs/release.md#requirements-and-acceptance).

## Deferred Implementation

These are not an implementation-ready feature queue. After candidate verification and baselines,
select a concrete workload and finish its owning contract before coding.

| Capability | Why it is not ready / required next decision |
| --- | --- |
| Broader Java behavior | **Planned:** select a minimized unsupported program and the observable native/JVM behavior it must gain. [Compiler](specs/compiler.md#open-decisions) owns compatibility choices. |
| Parallel native compilation | **Blocked:** prove bounded execution, cancellation, cache integrity and runtime ownership under [CONC-003](specs/concurrency-runtime.md#requirements-and-acceptance), then compare with the candidate baseline. A worker-count setting alone does not satisfy this contract. |
| Broader runtime and distributions | **Planned:** concurrency, UTF-16, exception shapes, richer ABI types and optional hosts need separate bounded contracts. [Decision owners](#decisions-before-later-work) identify where to resolve them; no TLS stack, collector or backend is selected here. |

## Completed Release Foundation

| Stable ID | Status and scope | Evidence |
| --- | --- | --- |
| [REL-SCOPE-01](https://github.com/NanoNative/javan/issues/103) | **Done:** scenario-bounded first-release contract. | [Release scope](specs/release.md#first-native-release-scope), closed issue. |
| [REL-ARTIFACT-01](https://github.com/NanoNative/javan/issues/116) | **Done:** extracted-artifact rehearsal implemented; each future candidate must run it. | [Rehearsal scripts and public tests](verification.md#release-foundation). |
| [ABI-IMPORT-01](https://github.com/NanoNative/javan/issues/117) | **Done:** primitive and borrowed-byte-array imports on the three declared targets. | [Recorded acceptance](verification.md#release-foundation). |
| [PERF-BASELINE-01](https://github.com/NanoNative/javan/issues/130) | **Done:** recorded cold/warm/changed-source measurements, not a universal time budget. | [Baseline evidence](verification.md#build-baselines). |
| [CI-SHARD-01](https://github.com/NanoNative/javan/issues/250) | **Done:** six native test workers balanced from recorded durations. | [Worker evidence](verification.md#test-worker-balance). |

## Supplemental And Deferred Work

[REL-CONTAINER-01](https://github.com/NanoNative/javan/issues/115) remains open as supplemental
Linux container proof. [REL-CONTAINER-01C](https://github.com/NanoNative/javan/issues/263)
also remains open although digest-proof implementation exists. Their reconciliation and
acceptance belong to [IMG-001/002](specs/container-images.md#requirements-and-acceptance);
a documentation edit does not close those issues or replace host-native package proof.

The current implementation already includes reachability, a canonical CFG, instantiated-type
analysis, bounded callback provenance, local facts, effect summaries, escape classification,
scoped stack allocation, closed-world reflection, resources and service loading.
Their boundaries are in [compiler](specs/compiler.md), [analysis](specs/analysis-and-optimization.md)
and the generated [support ledger](status/support-matrix.md). They are not new work merely
because an older planning paragraph says “later”.

## Decisions Before Later Work

Open choices live in their owning specs, so there is no second question registry here.

| Before | Decision owner |
| --- | --- |
| A public release | [Release review](specs/release.md#human-review): candidate evidence and actual publication decision. |
| Any new default analysis or performance promise | [Optimization review](specs/analysis-and-optimization.md#human-review): workload and measured cost/benefit; no invented thresholds. |
| General concurrent heap mutation or complete virtual threads | [Memory](specs/memory-runtime-correctness.md#human-review), [concurrency](specs/concurrency-runtime.md#human-review): ownership, scheduling and failure/lifecycle contracts. |
| Full UTF-16, wider reflection or exception compatibility | [Compiler review](specs/compiler.md#open-decisions): exact behavior profile and native/JVM acceptance. |
| Static/self-contained runtime or new OS/architecture | [Runtime selection](specs/runtime-feature-selection.md#human-review), [release](specs/release.md#human-review): linkage/platform policy and proof. |
| TLS, trust stores or additional dependency providers | [Dependencies](specs/dependency-and-license-reports.md#human-review), [compiler](specs/compiler.md#open-decisions): trust, provenance and failure boundaries first. |

## Scope Boundaries

Maven Central and Homebrew publication remain disabled. Coverage remains advisory.
General runtime class definition, unrestricted class loading/JNI, speculative/JIT optimization
and build-time application heaps are outside the current native model.
Backend experiments need a measured public workload before a production decision.

Studio, UI, IDE and build-plugin products stay outside this repository under
[ADR 0001](adr/0001-core-repo-boundary.md). Their integrations consume the same CLI,
normal Java outputs and reports; they do not introduce another compiler path.
