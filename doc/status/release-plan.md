# Javan Release Plan

Last updated: 2026-07-20

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
| Release milestones fully closed | 10 / 10 = 100.0% |
| Release milestones left to close | 0 / 10 = 0.0% |
| Remote Linux package rows proven | 2 / 2 = 100.0% |
| Required local macOS package gate | proven locally |
| Roadmap rows fully done | 31 / 38 = 81.6% |
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

R3 is closed: remote run `29663790338` passed Linux x64, Linux aarch64, and both
Windows runtime lanes; the required local macOS aarch64 package gate and archive
checksum also passed. macOS publication remains outside the first-release contract.

R4 is closed: the local macOS package smoke used packaged `bin/javan` to rebuild
Javan into a JAR and native binary, started the rebuilt binary with the package
version, and passed the package-backed self-host sanitizer with zero final heap/root
residue. The macOS leak-detection fallback also passed. The current branch additionally
revalidated the native bootstrap -> rebuilt -> verified self-host chain through
`JAVAN_BUILD_REUSE_TARGET=true sh scripts/build.sh target/.javan/bin/javan-optimized-selfhost`;
the verified binary completed its `--version` smoke after the metadata-sort and source-line
lowering optimizations.

R5 is closed: the full local macOS sanitizer suite passed generated-app, root/GC,
native-library, and allocation-pressure probes. Native-library ownership proof
passed for Python, Rust, and Go bindings with zero final live heap/root residue and
no failure signatures.

R6 is closed: the package-backed native-library acceptance path verified C ABI v2,
`JavanResult` and result cleanup, structured last-error ownership, retained
`String`/`byte[]` ownership, and generated C/Rust/Go/Python binding smoke. The
generated ABI artifacts and C caller completed without failure signatures.

R7 is closed: the package-backed external-probe acceptance path passed all five
project-neutral probes with required dependencies present and exact expected output.
The remote packaging/probes lane and external-probe isolation tests also remain
green; probe identities stay outside compiler-owned support claims.

R8 is closed: published Linux x64 and Linux aarch64 archives and checksums passed
verification, the Linux-only Homebrew formula generated from those checksums passed
its verifier, and the local macOS package checksum and extracted layout passed.
macOS publication remains outside the first-release contract.

R9 is closed: all published versioned and floating Wolfi, distroless, and scratch
image tags passed amd64/arm64 manifest verification, and the default published
image built and ran the native showcase with zero diagnostics.

R10 is closed: the exact local macOS release rehearsal completed with exit code 0,
including Maven/native tests, 117 acceptance checks, package checksum and extraction,
self-host rebuild, and the full sanitizer suite. No release publication side effect
occurred; the publication workflows remain disabled.

R1 is closed: the first-release contract is frozen and consistent across the release,
support, and native-ABI source documents, including Linux-only publication, local
macOS host coverage, Windows smoke-only scope, explicit rejection policy, and deferred
macOS publication.

R2 is closed: the exact rehearsal and green remote matrix verified the CI/package
workflow surface, package smoke, platform lanes, parallel-test policy, and coverage
configuration. Workflow enablement remains intentionally disabled for this release
review state.

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
