# ADR 0003: Static Native Compilation And Runtime State

## Status

Accepted existing direction, recorded on 2026-09-20. Sources: the pre-existing roadmap and
binary-first distribution contract at main `0bd68804`, which require ordinary Java source
and runtime application initialization. This record
consolidates that decision; it does not approve a new backend or broaden Java compatibility.

## Context

JavaN should compile ordinary Java build outputs into useful native programs while keeping
compilation bounded and failures understandable. Native artifacts still create objects,
initialize classes and manage resources when users run them.

## Decision

Use a real JDK to produce classfiles, analyze the closed world reachable from entrypoints,
and generate C for the host compiler. C is the production backend. Keep application class
initialization, entropy, clocks, object lifetime and execution-dependent state at runtime.
Compile-time facts may remove work only where behavior is proven unchanged.

Resolve supported static reflection, resources and services automatically from the compiled
inputs. Emit actionable diagnostics for unsupported reachable shapes. Unknown facts remain
conservative. The [compiler spec](../specs/compiler.md) owns behavior and acceptance.

## Alternatives And Consequences

- A full JVM or runtime class-loading system would substantially broaden the execution model;
  it is outside the current product scope.
- Build-time application initialization could capture machine-specific state and change
  Java behavior; it is not the chosen startup model.
- Another native backend remains a possible measured experiment, not a planned replacement
  date for C or a second compatibility contract.
- A runtime is required even though compilation happens ahead of time. Its scope follows
  supported behavior and [explicit ownership](../specs/memory-runtime-correctness.md).
