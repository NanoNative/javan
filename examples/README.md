# Examples

This folder contains user-facing samples only.

Current public sample:

- `native-showcase`: a small native app that demonstrates the supported compiler/runtime
  path through normal `javan build`.

Narrow regression projects belong in `src/test/resources/projects`, not here. Optional
TypeMap and Nano compatibility probes also live under test resources until their external
inputs are pinned for remote CI.

Generated `.javan`, `.gradle`, `bin`, `target`, and class output must not be committed.
