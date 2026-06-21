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
| `real-probes` | optional TypeMap/Nano compatibility probes |

Promote a test project to `example/` or a future public examples folder only after rewriting it into a production-grade
sample with complete user-facing instructions.
