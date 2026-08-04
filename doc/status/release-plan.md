# Javan Release Plan

Last updated: 2026-07-18

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

## Frozen first-release contract

- Published native packages: Linux x64 and Linux aarch64.
- Required local host gate: macOS aarch64, using the same package-backed verification
  script as the release matrix.
- Required CI runtime smoke: Windows current-thread and worker-thread lanes.
- macOS package publication and remote macOS runners are deferred until their runner and
  package gate is reliable; no macOS artifact is claimed by the first release.
- Unsupported reachable shapes must fail clearly before native code generation.
- Generated apps, native libraries, and bindings must finish sanitizer and ownership
  probes with zero final live heap/root residue.
- External projects are compatibility probes only and do not define compiler support rows.
- Pushes to `main` run the Linux package workflow as a dry run; publication remains an
  explicit manual dispatch requiring `BOT_TOKEN`.

## Current scoreboard

| Measure | Current value |
| --- | ---: |
| Release milestones fully closed | 0 / 10 = 0.0% |
| Release milestones left to close | 10 / 10 = 100.0% |
| Remote Linux package rows proven | 2 / 2 = 100.0% |
| Required local macOS package gate | proven locally |
| Roadmap rows fully done | 4 / 38 = 10.5% |
| Named support scenarios | 300 / 300 = 100.0% |

## Milestones

| ID | Gate | Exit condition |
| --- | --- | --- |
| R1 | Release contract freeze | Supported, unsupported, and out-of-scope families are explicit. |
| R2 | CI and packaging source of truth | Required jobs, package smoke, concurrency, and coverage behavior are stable. |
| R3 | Cross-platform runtime floor | Linux package rows and local macOS host proof are green; Windows smoke is green. |
| R4 | Self-host release proof | Packaged Javan rebuilds Javan and the rebuilt binary starts. |
| R5 | Memory and ownership proof | Generated apps, native libraries, and bindings pass sanitizer/ownership gates. |
| R6 | Native ABI release proof | C ABI v2, result/error ownership, and generated binding smoke are verified. |
| R7 | External reality probes | Neutral compiler behavior and package-backed probe outputs are deterministic. |
| R8 | Release metadata and installability | Versioning, checksums, Linux formula, and extracted-package checks pass. |
| R9 | Container publication | Released Linux assets drive reproducible image verification. |
| R10 | Release rehearsal | A dry run completes all required gates without publishing side effects. |

## Native build efficiency lane

Status: Planned. This is a product-performance lane, not an excuse to delay correctness,
ownership, package, or cross-platform release gates.

Javan must make repeated native builds practical without risking stale or non-reproducible
output. The implementation plan is detailed in [the native build cache roadmap](../spec/roadmap.md#0295a-native-build-cache-and-bounded-parallel-compilation).

Exit criteria:

- Content-addressed reuse is correct for generated sources, native objects, and final outputs.
- Every cache key includes input byte content, ordered dependencies, resources, target, build
  options, Javan/runtime identity, and native-toolchain identity; uncertain state is a miss.
- Independent native compilation work uses a bounded, memory-aware worker pool, with a
  deterministic serial fallback and an explicit user override.
- Concurrent builds cannot publish partial artifacts or corrupt another build's cache entry.
- Public-entrypoint measurements report cold, warm, and one-input-changed build time, peak
  memory, cache hit/miss stages, and invalidation reasons for a representative supported app.
- The initial target is a warm native rebuild of a normal supported service in under 30 seconds
  on the recorded reference machine. It is a target, not a current performance claim.

## Slice rules

Work exactly one blocker and one primary gate at a time. Prefer a failing required CI or
package gate, then self-host, ABI/ownership, cross-platform, and real-probe blockers.
JDK micro-slices are justified only when they remove one of those blockers. Every slice
needs a real code or workflow change, public-entrypoint or package-backed proof, and an
updated status statement when evidence changes.

Before coding, state the chosen gate, blocker, reason it beats a JDK micro-slice, expected
files, proof command, and the stop condition if the slice becomes architectural.

## Required proof

Local host gate:

```sh
.github/scripts/verify-release.sh
```

Focused package smoke:

```sh
JAVAN_PACKAGE_TARGET=linux-x64 sh .github/scripts/verify-ci-package-smoke.sh
```

Remote proof must show green Linux x64, Linux aarch64, and Windows runtime lanes. A PR
must explain which gate moved, which blocker was removed, what package/public-entrypoint
proof passed, and what remains explicitly unsupported. Never merge a red or partially
proven release slice.

## Stop conditions

Stop only for a real external blocker, an architectural decision requiring escalation, or
remote permissions/state that prevent safe progress. A merged PR is not a stop condition.
