# Testing Policy

Coverage targets are:

- line coverage >= 95%
- branch coverage >= 90%

These are direction targets, not merge blockers yet. CI publishes the JaCoCo reports and
adds a warning when the current soft target is missed; `mvn verify` does not fail on a
coverage percentage. The report is scoped to
deterministic compiler-core behavior that runs inside the Maven test JVM plus explicitly
instrumented child JVMs that execute `javan.Main`:

- reachability
- static verification
- C code generation
- native linker success/failure handling
- diagnostics
- compatibility bytecode support classification
- project detection and main-class detection
- classfile cursor, constant-pool, parser, and scanner behavior
- `javan.Main`, `javan.Javan`, CLI parsing/facade behavior, and project report
  orchestration through merged child-JVM coverage
- utility helpers for deterministic strings, JSON, files, and process execution

The Maven build writes one `target/jacoco-surefire-<fork>.exec` file per reusable Surefire
process, instruments child `java ... javan.Main` runs into `target/jacoco-child/*.exec`,
merges all of them into `target/jacoco-merged.exec`, and runs the report from that merged
file.

## Generated Compatibility Status

Full `mvn verify` on JDK 25 runs the canonical `javan compat` command against the already
compiled `target/classes` tree. A different Java feature release fails before generation so
it cannot silently rewrite versioned matrix keys. The lifecycle always synchronizes:

- `doc/status/support-matrix.md`
- `doc/status/support-matrix.json`

The lifecycle also synchronizes `doc/status/jdk-compatibility.md` when Maven runs on the
canonical Linux x64 platform. The report records the Java feature contract (`JDK25`), not a
vendor, patch, or host stamp that would churn whenever the toolchain image is refreshed. If any
tracked status file in scope was stale, verification writes it and fails once with the
changed paths and an instruction to review them and rerun `mvn verify`. The repeat run must
pass.

Every JDK 25 run still generates its active environment report under `target/.javan/` and
`target/classes/doc/status/`. A non-canonical platform leaves the tracked JDK snapshot unchanged,
avoiding machine-dependent churn. The dedicated `verify-compatibility-status` CI
job provisions the project Java on the canonical Linux x64 platform before running the same
Maven lifecycle. Platform drift fails closed instead of silently skipping the JDK snapshot. There
is no separate render, copy, or comparison command for contributors to remember.

## CI Execution

Pull requests and `main` pushes are thin entry workflows over the reusable
`.github/workflows/build-common.yml` build. The common build keeps one source of truth for
the verification commands while allowing release orchestration to remain separate.

The CI work is divided by independent proof rather than running the longest native checks
serially:

- six CLI integration shards run with `max-parallel: 6`
- native acceptance, sanitizer, and package/self-host proofs run as separate jobs for both
  Linux x64 and Linux arm64
- lightweight compiler/platform contract smoke runs on Linux, macOS, and Windows for x64
  and arm64
- verified native packages run on Linux; macOS package rows remain explicit and disabled
  after the arm64 proof exceeded its job-time projection and x64 was already the slower
  architecture lane; Windows package rows remain explicit and disabled

Every operating-system/architecture row remains in the matrix with an explicit `enabled`
flag. If a preview runner is unreliable, or a secondary architecture is disproportionately
slower without adding distinct evidence, change that flag to `false`; do not delete the row.
The disabled row then remains visible as an intentional CI policy decision.

Manual releases reuse this common build and its uploaded package/publication artifacts.
External actions are pinned to immutable commit SHAs with readable version comments; moving
major tags are not accepted by the workflow policy tests.

Native packaging tests reuse one self-hosted Javan bootstrap when several primitive-literal
programs need the same compiler. Each program still has its own labeled output assertion;
only the repeated compiler bootstrap is removed. On the implementation host, the complete
`CliPackagingIntegrationTest` suite fell from `116.49s` on fresh `main` to `80.18s` with
the same 21 tests passing; remote CI timings remain the acceptance evidence for runner gains.

JUnit parallel execution is enabled by default through `src/test/resources/junit-platform.properties`.
This keeps the policy visible to Maven, IDEs, and other JUnit Platform launchers. Tests run
concurrently unless they opt into `@Execution(SAME_THREAD)`, `@Isolated`, or a
`@ResourceLock`. Any test that mutates global JVM state such as `System` properties, shared
project output, locale, timezone, or process-wide caches must stay serial until that shared
state is removed or guarded by a narrow resource lock. Maven uses two reusable Surefire
processes so isolated native suites can advance two at a time without sharing JVM state.
Each suite keeps its existing execution and resource-lock rules inside its process; the
fixed two-process bound avoids scaling native compiler load with the host CPU count.
The CLI compatibility command tests are split into `CliCompatIntegrationTest`; its three
JDK-inventory/probe tests run concurrently and now take about `31s` together instead of
about `88s` when they lived inside the serial CLI monolith. Cheap CLI command/report/toolchain
tests live in `CliCommandIntegrationTest` and stay temp-directory scoped. Repo-level
`target/classes` and current-JVM system-property mutation tests live in the serial
`CliSharedStateIntegrationTest`. The remaining temp-project native CLI matrix stays serial
inside each suite and gains bounded concurrency only through the two isolated Surefire
processes.

The following area still needs direct public-entrypoint tests, more targeted child-JVM
coverage, or non-JaCoCo native/runtime evidence before the coverage targets can become a
meaningful merge gate:

- `javan/codegen/BytecodeToIR*`

CI currently reports coverage without failing the build. The next gate is to make the 95% line
and 90% branch targets blocking only after the full measured scope reaches them without broad
package exclusions. Native binaries remain covered by acceptance, sanitizer, leak/soak, and
counter-backed runtime heap gates, not by JaCoCo.

## Test Shape

Every test checks exactly one assumption, scenario, or case.

Use shared setup when it keeps test projects readable, but split unrelated expectations into
separate tests. A failing test name should identify the broken promise without reading a
large assertion bundle.

Required behavior coverage for feature slices:

- one success case per supported shape
- one negative case per unsupported reachable shape
- one report-content case per generated report contract
- one public-entrypoint case for user-visible behavior
- one regression case per fixed bug

Research spikes and agent work follow the same one-scenario test rule before migration
into the main suite.
