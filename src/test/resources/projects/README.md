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
| `external-smoke` | external smoke probes against pinned third-party artifacts; each probe declares its own metadata and exact expected stdout, CI resolves artifacts from that metadata, local helper scripts resolve classpaths from the same metadata, and the acceptance harness must stay metadata-driven without hardcoding probe identities; these are moving upstream artifacts rather than internal fixtures, do not define compiler-owned support claims or product knowledge, may rotate over time, and must always map back to a generic compiler-owned regression in `src/test/java/javan` |

Promote a test project to `example/` or a future public examples folder only after rewriting it into a production-grade
sample with complete user-facing instructions.

Generated outputs do not belong here. Real-probe helper runs must leave no `.javan/`, `target/`, `build/`, or `out/`
directories behind under `src/test/resources/projects/external-smoke`.

When an external probe finds a bug, the permanent regression belongs in a generic JDK/runtime test
under `src/test/java/javan`, not in a probe-specific support rule or probe-named compiler test.
The current pinned external probes may change independently, while the durable Javan regression
and support line stays generic.
