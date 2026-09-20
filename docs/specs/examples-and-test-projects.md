# Examples And Test Projects

## Human Review

Owns public examples, isolated test fixtures and external-probe identity boundaries.
The compiler remains project-neutral. A successful pinned probe proves that artifact,
not every upstream version or a whole JDK family.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| EX-001 | Public examples MUST run through the documented CLI with understandable expected output and native/JVM parity where applicable. | [Showcase](../../example/README.md), [showcase verifier](../../.github/scripts/verify-showcase.sh). |
| EX-002 | External probe identities MUST stay within their permitted metadata/source boundary; durable compiler fixes MUST have generic regressions. | [Isolation tests](../../src/test/java/javan/ExternalProbeIsolationTest.java), [dependency CLI](../../src/test/java/javan/CliDependencyProjectIntegrationTest.java). |
| EX-003 | Each test-only fixture MUST prove a supported scenario or deterministic rejection without being advertised as a complete user example. | [Acceptance harness](../../.github/scripts/acceptance.sh), [test projects](../../src/test/resources/projects/README.md). |

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

## External Probe Boundary

External probes answer only “can JavaN compile this pinned artifact?”, not “is this whole Java
feature supported?”. The compiler and its permanent regression line remain project-neutral.

| Location | Owns |
| --- | --- |
| `src/test/resources/external-probes/*` | Reproducible smoke inputs: `probe.properties`, `expected.stdout`, `build-example.sh`, local README and tiny importing sources. |
| `src/test/resources/external-artifacts/*` | Bundled source used to build deterministic external artifact JARs. |
| [External readiness ledger](../status/real-project-readiness.md) | Pinned evidence and its mapping to a generic compiler-owned regression, separate from language-support accounting. |

Identity rules:

- Keep upstream names, coordinates, `identityAliases` and `identityPackages` in probe metadata;
  importing source files may name the external classes they use.
- Keep upstream identities and disposable `artifact-*` probe labels out of product code,
  support/JDK ledgers, compiler-owned test names, reports, workflow/acceptance scripts,
  public examples, non-probe test resources and general docs. Probe READMEs describe generic
  metadata-driven commands, not upstream-specific semantics.
- The acceptance harness iterates probe directories; it does not hardcode library-specific
  support rules. Evolving external examples stay outside the compiler-owned regression line.
- When a probe finds a real gap, reproduce it in a synthetic generic Java/JDK test and keep
  that regression with the fix. Changing or removing a probe should otherwise require only
  metadata/smoke changes, not product changes or renamed support claims.

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
