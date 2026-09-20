# Testing Policy

## Human Review

Owns verification commands, suite membership, host preparation and quality evidence.
A failed test blocks its gate; coverage percentages are advisory. Local and CI checks use the same Maven
suite vocabulary. [Release](release.md) owns what a shipping artifact must prove.

No test or coverage policy is changed by reorganizing documentation. Converting coverage
targets into a hard gate would require a separate decision; it is not an automatic next step.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| TEST-001 | Local verification depths MUST select the documented suites through the Maven lifecycle. | [Maven profiles](../../pom.xml), [JUnit suite definitions](../../src/test/java/javan/testing/TestSuite.java), [workflow policy tests](../../src/test/java/javan/CiParallelWorkflowSurfaceTest.java). |
| TEST-002 | Distributed native test assignments MUST be deterministic, disjoint and complete without hardcoded workflow class selectors. | [Worker planner tests](../../src/test/java/javan/testing/CiTestWorkerPlannerTest.java); [historical balance evidence](../verification.md#test-worker-balance). |
| TEST-003 | Local Maven verify MUST refresh tracked support status, report stale changes and pass on a stable repeat without hand-copying files. | [CLI lifecycle refresh tests](../../src/test/java/javan/compat/CompatibilityStatusRefreshTest.java), including canonical-platform ownership and missing generated artifacts; CI explicitly skips refresh. |
| PLAT-001 | Each claimed native target MUST be proved on its matching host through the extracted artifact. | [Release proof](release.md#candidate-evidence-procedure), [workflow policy tests](../../src/test/java/javan/CiParallelWorkflowSurfaceTest.java). |
| PLAT-002 | Disabled rows MUST stay explicit; platform smoke MUST NOT be presented as package support. | [Common workflow](../../.github/workflows/build-common.yml), [release scope](release.md#first-native-release-scope). |
| PLAT-003 | A proof MUST report actual tools, target and resource evidence; unsupported measurements MUST remain unknown. | [Package baseline harness](../../.github/scripts/measure-package-build-baseline.sh), [historical evidence](../verification.md). |

Coverage targets are:

- line coverage >= 95%
- branch coverage >= 90%

These are advisory targets. Maven writes the merged JaCoCo report; workflows do not
upload partial coverage artifacts or print per-job coverage summaries. `mvn verify`
does not fail on a coverage percentage. The report is scoped to
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

## Verification Vocabulary

| Term | Question it answers | Values |
| --- | --- | --- |
| Suite | Which related tests run? | `core` (untagged default), `native`, `packaging`, `external` |
| Depth | How much runs locally? | `quick`, `standard`, `full` |
| Proof | What native evidence does CI collect? | `smoke`, `acceptance`, `sanitizer`, `package` |
| Portability | Which core subset repeats on another host? | `platform`, `windows` |
| Target | Which operating system and architecture run it? | `linux_x64`, `linux_arm64`, `mac_x64`, `mac_arm64`, `win_x64`, `win_arm64` |

A depth selects suites. CI then runs additional proofs on its configured targets. These are separate
dimensions: `quick` is not a suite, and `smoke` is not a depth.

## Local Verification

Use the smallest depth that covers the current change. Every command uses the normal Maven
lifecycle and writes its JaCoCo report to `target/site/jacoco/index.html`; only the full command
measures every Java-test suite.

| Depth | Command | Includes | Use it for |
| --- | --- | --- | --- |
| Quick | `./mvnw -Pquick verify` | Compiler/core tests; excludes native, packaging, and external suites | Normal edit/compile feedback |
| Standard | `./mvnw -Pstandard verify` | Core plus native CLI behavior; excludes packaging and external probes | Before pushing an ordinary compiler/runtime change |
| Full | `./mvnw clean verify` | Every test suite from a clean build | Release, packaging, workflow, or broad cross-cutting changes |

These depths select local edit feedback; they are not substitutes for artifact acceptance.
Before declaring a meaningful compiler, runtime, linker, toolchain, packaging or diagnostics
change complete, the existing local macOS [release gate](release.md#local-gate) also applies.
Other claimed hosts require their matching CI proof. Documentation-only changes can use
focused documentation/workflow checks without rebuilding every native artifact.

For a failing-first regression, run the narrow test while editing, then run the appropriate
depth above:

```sh
./mvnw -Dtest='ClassName#methodName' test
```

CLI integration classes declare exactly one documented execution suite:

- `@NativeTest` generates, compiles, and executes native C through the JavaN CLI.
- `@PackagingTest` builds and verifies distributable or self-hosted packages.
- `@ExternalTest` uses external probe artifacts, toolchains, or services.
- `@PlatformTest` repeats portable JVM-only behavior on every enabled OS and architecture.
- `@WindowsCompatibilityProof` temporarily selects portable runtime checks that CI repeats with a
  Windows toolchain while support is incomplete. It is not a Windows-only test category and should
  disappear once the complete relevant suite runs on every supported Windows target.

Ordinary JVM-only tests need no annotation; they belong to the default `core` suite. Portability
annotations select focused repeats without removing those tests from normal local Maven depths.

These annotations are composed JUnit tags defined in `javan.testing.TestSuite`. Maven can also
run a phase directly, for example `./mvnw -Dgroups=native test`,
`./mvnw -Dgroups=platform test`, or `./mvnw -Dgroups=windows-compatibility test`. CI discovers and distributes
native tests across six workers automatically. Each enabled Windows runtime matrix row uses the same
tagged Maven phase, and JUnit runs its methods concurrently. Contributors do not maintain class or
method selectors in workflow YAML. `worker_index` identifies one native worker, while `worker_count`
states how many workers share that phase. Adding a CLI integration class without exactly one suite
fails the workflow policy test, and any literal test selector in a workflow fails policy verification.

## Generated Compatibility Status

Every local `mvn verify` depth on JDK 25 runs the canonical `javan compat` command against the already
compiled `target/classes` tree. A different Java feature release fails before generation so
it cannot silently rewrite versioned matrix keys. The lifecycle always synchronizes:

- `docs/status/support-matrix.md`
- `docs/status/support-matrix.json`

The lifecycle also synchronizes `docs/status/jdk-compatibility.md` when Maven runs on the
canonical Linux x64 platform. The report records the Java feature contract (`JDK25`), not a
vendor, patch, or host stamp that would churn whenever the toolchain image is refreshed. If any
tracked status file in scope was stale, verification writes it and fails once with the
changed paths and an instruction to review them and rerun `mvn verify`. The repeat run must
pass.

The public CLI continues to generate `doc/status/*` below its input root. Maven copies
the generated `target/classes/doc/status/*` files into tracked `docs/status/*`; these are
different boundaries, not competing repository documentation roots.
Every JDK 25 run still generates its active environment report under `target/.javan/` and
`target/classes/doc/status/`. A non-canonical platform leaves the tracked JDK snapshot unchanged,
avoiding machine-dependent churn. CI provisions the project Java version on its enabled
targets and uses the same test suites, but passes `-Dexec.skip=true` to skip compatibility
regeneration. There is no separate render, copy, or comparison command for contributors
to remember locally.

## CI Execution

Pull requests and `main` pushes are thin entry workflows over the reusable
`.github/workflows/build-common.yml` build. The common build keeps one source of truth for
the verification commands while allowing release orchestration to remain separate.

The CI work is divided by independent proof rather than running the longest native checks
serially:

- six automatically discovered native CLI workers plus dedicated packaging and external suites run
  with `max-parallel: 8`
- native acceptance, sanitizer, and package/self-host proofs run as separate jobs for both
  Linux x64 and Linux arm64
- annotation-selected compiler/platform contract smoke runs on Linux and macOS for x64 and arm64,
  plus Windows x64; the Windows arm64 row remains explicit and disabled until Temurin 25 is available
- native package lanes run on every [required release host](release.md#first-native-release-scope);
  PRs use the `bootstrap` package scope, snapshots use `full` on Linux and `bootstrap` on macOS,
  and manual releases use `full` everywhere; only `full` includes the artifact rehearsal

Every operating-system/architecture row remains in the matrix with an explicit `enabled`
flag. If a preview runner is unreliable, or a secondary architecture is disproportionately
slower without adding distinct evidence, change that flag to `false`; do not delete the row.
The disabled row then remains visible as an intentional CI policy decision.

Manual releases reuse this common build and its uploaded package/publication artifacts.
External actions are pinned to immutable commit SHAs with readable version comments; moving
major tags are not accepted by the workflow policy tests.

Native packaging tests reuse one self-hosted Javan bootstrap when several primitive-literal
programs need the same compiler. Each program still has its own labeled output assertion;
only the repeated compiler bootstrap is removed. Earlier timings are retained in
[verification history](../verification.md#build-baselines); current runner gains need fresh evidence.
The package self-host proof sets a target-specific managed-heap bound so collection occurs
under the release workload and the proof stays within its host time budget. Dedicated
acceptance and sanitizer probes keep their own smaller explicit heap bounds for deterministic
out-of-memory behavior.
Native package timing artifacts record wall time, CPU time, peak RSS, metric source, processor
count, and physical memory. A platform that cannot provide a resource metric records `unknown`;
reports never substitute a guessed value.

`Package Build Baselines` is a manual three-target workflow for release performance evidence. It
extracts a host-native package and measures the public `bin/javan build` command against the
versioned `example` fixture: three cold runs, three warm runs, and one controlled source-change
rebuild. Its JSON and Markdown artifacts record the commit, target, JDK, C toolchain, wall time,
CPU time, peak RSS, artifact size, and native-object-cache decisions per result. These are
comparative measurements, not a CI regression threshold.

Native CLI workers also retain their standard Maven XML reports for 14 days. Those reports are
the recorded per-test timing evidence for balancing the existing six automatic workers; workflow
YAML continues to contain no Java class or method selector.
The planner stores the resulting class-duration profile in
`src/test/resources/javan/testing/native-class-durations.tsv`; unknown newly discovered classes
use their method count until the next recorded timing run replaces the profile.

JUnit parallel execution is enabled by default through `src/test/resources/junit-platform.properties`.
This keeps the policy visible to Maven, IDEs, and other JUnit Platform launchers. Tests run
concurrently unless they opt into `@Execution(SAME_THREAD)`, `@Isolated`, or a
`@ResourceLock`. Any test that mutates global JVM state such as `System` properties, shared
project output, locale, timezone, or process-wide caches must stay serial until that shared
state is removed or guarded by a narrow resource lock. Maven uses two reusable Surefire
processes so isolated native suites can advance two at a time without sharing JVM state.
Each suite keeps its existing execution and resource-lock rules inside its process.
JUnit uses CPU-scaled pools (`dynamic.factor = 1.0`) in each JVM, so the two-process
bound is not a global limit on native compiler processes. Before making a serial native
suite concurrent, establish an explicit resource bound and verify its isolation.
The CLI compatibility command tests are split into `CliCompatIntegrationTest`; its
JDK-inventory/probe tests run concurrently. Cheap CLI command/report/toolchain
tests live in `CliCommandIntegrationTest` and stay temp-directory scoped. Repo-level
`target/classes` and current-JVM system-property mutation tests live in the serial
`CliSharedStateIntegrationTest`. The remaining temp-project native CLI matrix stays serial
inside each suite and gains bounded concurrency only through the two isolated Surefire
processes.

The following area still needs stronger public-entrypoint tests, targeted child-JVM
coverage, or non-JaCoCo native/runtime evidence:

- `javan/codegen/BytecodeToIR*`

Coverage remains advisory; reaching a target does not automatically enable a failing gate.
Native binaries remain covered by acceptance, sanitizer, leak/soak, and
counter-backed runtime heap gates, not by JaCoCo.

## Native Host Verification

Use the same candidate commit and declared inputs on every required host. Prepare the project
JDK, host C compiler, archive/checksum tools and binding toolchains required by the chosen proof.
[Toolchains](toolchains.md) owns discovery and installation; the
[native-proof workflow](../../.github/workflows/native-proof.yml) records CI preparation.
Networked setup and cache warming are explicit preparation, not hidden test dependencies.
Tests use local files, declared artifacts and restored caches, never silently installed tools
or mutable external services. Reports identify actual tools rather than infer them from labels.

The [local completion gate](#local-verification) and each host's
[candidate evidence](release.md#candidate-evidence-procedure) serve different purposes;
neither certifies the other hosts. Promotion requires package, ABI, self-host and sanitizer proof.
A Linux container on macOS or Windows does not prove a native package for that host, and an
emulator does not substitute for execution on a claimed native host. [Containers](container-images.md)
owns native Linux architecture/digest proof; Windows containers remain future scope.

### Bootstrap And Timing

Generation 3 CI exports portable C from the verified Linux x64 bootstrap. Fresh Linux ARM64
and macOS ARM64 runners compile it with their own compiler and execute their selected package
proof. Reusing C does not permit relabeling a binary from another architecture.
Package verification also reuses generated self-host C for its sanitizer leg. Record the
bootstrap generation and proof scope with the artifact; the
[memory contract](memory-runtime-correctness.md#self-host-memory-proof) owns allocation,
collection and final heap/root-residue requirements.

Run the manual [Timings workflow](../../.github/workflows/timings.yml) when changing reachability,
verification, code generation, the native runtime or packaging. It compares generations 2 and 3
on release hosts using the same package proof, with summary comparisons and phase measurements
in artifacts. Timing does not replace correctness/memory gates. The separate cold/warm/changed-source
app-build baseline is described under [CI execution](#ci-execution).

### Wider JDK Matrix — Planned

Current verification uses the project Java feature version under the
[canonical snapshot policy](#generated-compatibility-status), not an all-LTS release matrix.
Before claiming a wider range, agree the selected versions and report orchestration, then:

- identify each claimed LTS home and any tracked current/previous feature release;
- reuse the [existing JDK commands](toolchains.md#commands), selection rules and global cache;
- prepare missing JDKs explicitly before offline verification;
- emit deterministic, version-labelled inventory and bytecode-pattern reports;
- prove native/JVM parity and diagnostic boundaries for that range.

This expansion does not call for another installer, cache layout or speculative compatibility CLI.

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

Negative cases cover reachable unsupported bytecode, constant-pool tags, classfile attributes,
bootstrap/JDK shapes and export signatures; missing or mismatched toolchains/targets; broken
build wrappers; dependency checksum failures; array-copy type errors; and runtime null/bounds
failures. Assert the specific reason and diagnostic contract, not merely a nonzero exit.
Changed examples must compile/run, reports must remain deterministic, and tests must use
declared local inputs or explicitly prepared caches. Container changes also need native Linux
architecture proof under [the container contract](container-images.md).
