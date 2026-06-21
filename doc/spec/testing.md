# Testing Policy

`mvn verify` enforces:

- line coverage >= 95%
- branch coverage >= 90%

The enforced JaCoCo gate is not a whole-repository quality number yet. It is scoped to
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

The Maven build writes `target/jacoco-surefire.exec`, instruments child `java ... javan.Main`
runs into `target/jacoco-child/*.exec`, merges them into `target/jacoco-merged.exec`, and
runs report/check from that merged file.

JUnit parallel execution is enabled in opt-in mode through `src/test/resources/junit-platform.properties`.
Unannotated tests stay same-thread. A test class may use `@Execution(CONCURRENT)` only when
each test owns its files and does not mutate global JVM state such as `System` properties,
shared project output, locale, timezone, or process-wide caches. Tests that touch shared
state stay serial until the shared state is removed or guarded by a narrow resource lock.
The CLI compatibility command tests are split into `CliCompatIntegrationTest`; its three
JDK-inventory/probe tests run concurrently and now take about `31s` together instead of
about `88s` when they lived inside the serial CLI monolith. Cheap CLI command/report/toolchain
tests live in `CliCommandIntegrationTest` and stay temp-directory scoped. Repo-level
`target/classes` and current-JVM system-property mutation tests live in the serial
`CliSharedStateIntegrationTest`. The remaining temp-project native CLI matrix runs
concurrently in `CliIntegrationTest` under the fixed four-worker cap.

The following area still needs direct public-entrypoint tests, more targeted child-JVM
coverage, or non-JaCoCo native/runtime evidence before it can join the numeric gate:

- `javan/codegen/BytecodeToIR*`

Current state after the latest verified run:

- `javan/build/**` is back inside the hard JaCoCo gate.
- `mvn -q clean verify` now passes with only `javan/codegen/BytecodeToIR*` excluded.
- The merged gate with only `javan/codegen/BytecodeToIR*` excluded currently sits at
  `96.9285%` line / `90.2731%` branch.
- `javan/codegen/BytecodeToIR` itself is still too low for the hard gate:
  `92.9645%` line / `81.5075%` branch after the latest class metadata, field, array,
  checkcast, `iinc`, branch-selection, switch, collection, optional, StringBuilder,
  file/path, boxed-wrapper, `System`, `Arrays`, `List`, `Optional`, primitive string-concat,
  stdout-overload, object-backed PrintStream, interface dispatch, process-substitution, and
  diagnostic lowering tests, arithmetic/compare opcode coverage, `makeConcat` object/array
  descriptor coverage, Optional instance-helper coverage, concrete `HashMap` helper coverage,
  PrintStream receiver-branch coverage, boolean/float/double instance-field coverage, and
  unsupported PrintStream/field descriptor diagnostics, unsupported collection and empty-stack
  diagnostics, array clone variant coverage, StringBuilder lowering coverage, exact unsupported JDK
  branch diagnostics, print-stream object coercion, static-field lookup diagnostics, wrong-kind call
  argument and return stack diagnostics, primitive empty-return stack diagnostics, shared call-result
  lowering, complete multi-target interface dispatch return-type coverage, PrintStream receiver
  stack diagnostics, JDK object-value stack diagnostics, String instance-helper lowering,
  Path `isAbsolute`/`toAbsolutePath`/`resolve(Path)` lowering, File separator-char lowering,
  `Files.exists` lowering, `List.of` fixed-arity lowering, `Map.copyOf` lowering,
  ProcessRunner missing-result diagnostics, unsupported static-field write descriptor diagnostics,
  plus dead-code cleanup.

Next gate: remove the remaining `BytecodeToIR*` exclusion only after `mvn clean verify`
passes with merged coverage and the same thresholds. Native binaries remain covered by acceptance,
sanitizer, leak/soak, and counter-backed runtime heap gates, not by JaCoCo.

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
