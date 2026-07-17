# Test Projects

This folder contains deterministic Java projects used by the test and acceptance suites.
Each project should prove one behavior claim or one rejection rule.

These projects are not public examples. Public examples live under `example/` and must
be understandable as real user-facing samples.

## Layout

| Folder | Purpose |
| --- | --- |
| `acceptance` | end-to-end public-entrypoint checks used by release validation |
| `native-profile` | one-assumption supported native runtime/codegen scenarios |
| `negative` | deterministic rejection scenarios |

Promote a test project to `example/` or a future public examples folder only after rewriting it into a production-grade
sample with complete user-facing instructions.

External artifact smoke probes live outside this tree under `src/test/resources/external-probes/`.
That split is deliberate: compiler-owned test projects stay self-contained, while published third-
party artifact checks stay in a separate acceptance-only boundary.

When an external probe finds a bug, the permanent regression belongs in a generic JDK/runtime test
under `src/test/java/javan`, not in a probe-specific support rule or probe-named compiler test.
The current pinned external probes may change independently, while the durable Javan regression
and support line stays generic.
