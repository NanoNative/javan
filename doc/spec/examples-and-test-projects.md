# Examples And Test Projects

Javan keeps public examples separate from test projects.

## Public Examples

`example/` contains the runnable public showcase that users can inspect and build. Public
examples may also serve as release acceptance targets, but they are not fake
implementations and not private test projects.

Future additions to `example/` or a future `examples/` directory must be complete
user-facing samples, not renamed test projects. A top-level example should document a
real use case, run through the public CLI, and make its expected behavior obvious without
relying on local tribal knowledge.

Requirements:

- A user can understand why the example exists.
- The project builds through the normal `javan` CLI.
- JVM output and native output match when the example is an app.
- Generated files are never committed.
- Build and run instructions are complete enough for a new user.
- Complexity grows over time: simple feature examples stay, but real application-shaped
  examples are required before release claims.
- Each user-visible compiler/runtime enrichment should add one small showcase capability
  or a new complete public example in the same slice.
- Release image verification must keep proving that the default container image can build
  and run the current showcase.

Current public showcase:

- `example`: verified native app showing object allocation, final fields,
  interface dispatch, `ArrayList`, `HashMap`, `Map.copyOf`, `Optional`, explicit
  iterators, enums, static initialization, scoped try/catch, primitive arrays, string
  operations, string concatenation, and selected JDK intrinsics. This is the rolling
  public proof target: when Javan gains a visible feature, grow this showcase unless a
  separate complete example is more honest.

Optional real-project probes:

- `src/test/resources/projects/real-probes/*`: external compatibility smoke against selected
  third-party artifacts. Probe identity, coordinates, expected stdout, and the required mapping
  back to a compiler-owned generic regression all live in per-probe metadata and in the dedicated
  ledger at `doc/status/real-project-readiness.md`.

Each probe owns its own `probe.properties`, `expected.stdout`, and `build-example.sh`.
The acceptance harness only iterates probe directories; it does not hardcode library-specific
support claims into the compiler-owned test line.

Boundary:

- probe names may stay in probe metadata, probe READMEs, and the dedicated external-smoke docs
- probe names must stay out of support rows, JDK coverage ledgers, and compiler-owned regression tests
- upstream probe changes are allowed; Javan support claims must still be expressed in generic JDK/runtime terms

These are not core compiler/runtime support tests. They are allowed to prove "Javan can compile
this pinned real artifact today", but they are not allowed to define "Javan supports this JDK
feature". Core support tests must stay compiler-owned, deterministic, and independent of external
project semantics. When a real probe finds a bug, the fix must land with a synthetic
compiler-owned regression test that proves the underlying JDK/runtime shape without depending on
any external project identity. External probes are allowed to answer only one question: "does
Javan compile this pinned real artifact today?" They are not allowed to answer "is this Java
feature supported?"

Javan must not learn probe-specific semantics from these projects. If an upstream real probe
changes, the probe metadata and smoke assets may change, but the durable compiler/runtime
regression must still be expressed in generic JDK/runtime terms inside the core test line.

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
- Dependency-backed external library scenario.
- Dependency-backed external service scenario.
- Self-host bootstrap is covered by release tooling; future complex examples should
  focus on larger public apps and dependency-backed scenarios.
