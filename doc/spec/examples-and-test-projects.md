# Examples And Test Projects

Javan keeps public examples separate from test projects.

## Public Examples

`examples/` contains runnable Java projects that users can inspect and build. They may
also serve as release acceptance targets, but they are not fake implementations and not
private test projects.

Future additions to `examples/` must be complete user-facing samples, not renamed test
test projects. A top-level example should document a real use case, run through the public
CLI, and make its expected behavior obvious without relying on local tribal knowledge.

Requirements:

- A user can understand why the example exists.
- The project builds through the normal `javan` CLI.
- JVM output and native output match when the example is an app.
- Generated files are never committed.
- Build and run instructions are complete enough for a new user.
- Complexity grows over time: simple feature examples stay, but real application-shaped
  examples are required before release claims.

Current public showcase:

- `examples/native-showcase`: verified native app showing object allocation, final fields,
  interface dispatch, `ArrayList`, primitive arrays, string operations, string
  concatenation, and selected JDK intrinsics.

Optional real-project probes:

- `src/test/resources/projects/real-probes/typemap-pair`: optional real dependency probe against TypeMap.
- `src/test/resources/projects/real-probes/nano-metric`: optional real dependency probe against Nano's `MetricUpdate`
  record.
- `src/test/resources/projects/real-probes/nano-duration`: optional Nano duration example slice using
  `NanoUtils.formatDuration(long)` without `DevConsoleService`.

## Test Projects

`src/test/resources/projects` is for test-only projects. These can be narrow and
artificial because each test project exists to prove one assumption or one rejection rule.
Executable acceptance projects live here when they are not release-quality public
samples. The old one-feature top-level examples are preserved as test resources rather
than public examples because they are compiler/runtime probes, not user-facing sample
applications.

Current layout:

- `src/test/resources/projects/native-profile`: executable one-assumption supported
  native behavior probes used by acceptance.
- `src/test/resources/projects/negative`: deterministic rejection test projects.

Requirements:

- One test project should support one behavior claim.
- Negative test projects must fail with a deterministic diagnostic.
- Test-only projects must not be documented as user examples.
- Runnable test projects must stay under `src/test/resources/projects` unless they are
  rewritten into release-quality public examples.

## Future Complex Examples

The current public examples are intentionally small because Javan still rejects broad JDK
surface area. As the compiler supports more Java, add larger user-facing examples only
when they are release-quality samples:

- CLI app with resources and argument parsing.
- Multi-class service-style app with interfaces and substitutions.
- Native library with C, Rust, Go, and Python consumers.
- Dependency-backed TypeMap scenario.
- Dependency-backed Nano scenario.
- Self-host bootstrap is covered by release tooling; future complex examples should
  focus on larger public apps and dependency-backed scenarios.
