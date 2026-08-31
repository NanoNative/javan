# Compile-Time Runtime-Risk Warnings

Status: partial. `javan check` and `javan build` reject exact literal null-receiver, array-read,
and supported ASCII `String` indexing failures in one straight-line bytecode segment. Broader
nullness, range, and collection facts remain planned.

## Goal

`javan check` and `javan build` must diagnose runtime failure only when the available facts
prove it. Dynamic values keep normal Java runtime checks; the analyzer must never claim that
an unknown path is safe.

## Implemented Slice

The verifier tracks only literal `null`, literal one-dimensional array lengths, supported ASCII
string lengths, and direct local copies within a straight-line bytecode segment. It reports only
exact failures:

- reachable instance calls with no arguments, field reads, and `arraylength` fail with
  `JAVAN070` before native code generation
- reachable array reads whose literal index is outside a locally-created literal array length
  fail with `JAVAN071` before native code generation
- reachable `String.charAt` calls whose literal index is outside a supported ASCII literal
  length fail with `JAVAN072` before native code generation
- reachable `String.substring` calls whose literal start or end is outside a supported ASCII
  literal length fail with `JAVAN073` before native code generation
- the same shapes in an unreachable method are retained as `JAVAN170`, `JAVAN171`,
  `JAVAN172`, and `JAVAN173` in `.javan/reports/diagnostics.json` and `.md` without making
  `check` fail
- reassigned locals, method parameters, field values, returned values, calls with arguments,
  array writes, dynamic string or array lengths or indexes, non-ASCII string literals, branch
  merges, exception paths, and all other dynamic shapes remain unknown and are not diagnosed by
  this slice

Non-ASCII string literals retain the existing `JAVAN046` UTF-16-profile diagnostic; this slice
does not add a second bounds error for a string the current native profile cannot represent.

This small boundary prevents a known native runtime `NullPointerException` without treating a
partial local scan as a general nullness analysis.

Integral `idiv`, `ldiv`, `irem`, and `lrem` are runtime-correct independently of static facts:

- a zero divisor routes `ArithmeticException` with `/ by zero` through the original Java
  handler or uncaught-exception path
- division and remainder never execute C signed `/` or `%` with a zero divisor
- `Integer.MIN_VALUE / -1`, `Long.MIN_VALUE / -1`, and their remainder forms preserve Java
  results without relying on C signed-overflow behavior
- `ArithmeticException` propagates through application methods so a caller can catch it

This is runtime semantics, not a new compile-time warning. Javan does not yet reject a literal
zero divisor before native generation because a valid program can catch the exception.

One-dimensional `newarray` and `anewarray` allocations are likewise runtime-correct for dynamic
lengths:

- a negative length routes `NegativeArraySizeException` with the Java decimal length as its
  message through the original handler or uncaught-exception path
- the exception can propagate through application methods for callers to catch
- known non-negative literals avoid an unnecessary generated check
- generated C allocation helpers retain their negative-length panic as an internal defensive
  boundary; Java bytecode reaches the Java exception path first

The current direct handler admission is deliberately bounded: a protected range may contain one
integral division or remainder, or one one-dimensional array allocation, plus bytecodes already
classified as non-throwing. Broader protected ranges, including other potential failure points,
remain outside this release slice.

## Next Risk Checks

Initial checks:

- broader possible null dereference and null arguments
- unsafe array writes and broader array-index analysis
- `List.get(0)` without non-empty proof
- `Optional.get` without `isPresent` proof
- `Iterator.next` without `hasNext` proof
- static diagnosis of division or modulo by a possible zero divisor
- unsafe casts without `instanceof` proof
- broader uncaught or panic-style exception diagnostics
- redundant checks that can later feed release optimization

## Analysis Model

The implemented literal rule runs in static verification before native code generation. The
broader planned analysis runs after bytecode lowering:

```text
bytecode -> javan IR -> CFG -> flow-sensitive facts -> diagnostics -> reports
```

Required flow-sensitive facts:

- `NonNull(value)`
- `MaybeNull(value)`
- `Range(value, min, max)`
- `ArrayLength(array, value)`
- `StringLength(string, range)`
- `CollectionSize(value, range)`
- `TypeIs(value, class)`
- `BooleanValue(value, true/false)`
- `SameValue(a, b)`

Facts must be invalidated after mutation, unknown calls, volatile/thread-visible state, and
other operations that can change the proof boundary.

## Guard Summaries

The first planned guard summaries:

- `Objects.requireNonNull(x)` proves `x` is non-null after the call returns
- project-local `requireNonNull(x)` helpers can become summaries only after explicit proof
- `requireNonEmpty(list)` style helpers can prove collection size only when their bytecode is
  understood and has no side effects that change the value being checked

Unknown guard helpers remain ordinary calls.

## Severity Rules

Severity is deterministic:

- a reachable, definite runtime failure is an error
- an exact unreachable finding is retained as a warning in the report
- likely runtime failure is a warning
- uncertain finding is info and appears only in `--strict`

The analyzer should avoid warning spam by reporting the nearest source location and grouping
duplicate findings by diagnostic id, source location, risk kind, and reachable path.

## CLI And Report Policy

Current command behavior:

- `javan check` and `javan build` stop before native generation for `JAVAN070`, `JAVAN071`, and
  `JAVAN072`, and `JAVAN073`
- exact unreachable findings are persisted in shared diagnostic reports without terminal error
  output
- `.javan/reports/diagnostics.json` and `.javan/reports/diagnostics.md` are the current stable
  machine-readable and human-readable surfaces

Planned command behavior:

- broader static safety analysis runs before native generation
- `javan report` reads and summarizes the generated report model
- strictness, warnings-as-errors, and feature toggles should be available from project or
  global settings before adding more public flags
- `javan explain <diagnostic-id>` may print the rule, examples, and suggested fixes once
  the diagnostic catalog is stable

## Planned Reports

Generated report paths:

- `.javan/reports/diagnostics.json`
- `.javan/reports/diagnostics.md`
- `.javan/reports/safety-warnings.json`
- `.javan/reports/safety-warnings.md`
- `.javan/reports/report.json`
- `.javan/reports/report.md`

The JSON report should preserve stable diagnostic ids, severity, class, method, descriptor,
source file, line, reachable path, facts used, invalidation points, reason, and fix. The
Markdown report should group findings by severity and source path.

## Constraints

- Never claim safety unless proven.
- Keep unreachable findings non-blocking and separate from reachable build errors.
- Do not remove checks here; optimization remains a separate release-mode pass.
- Keep public/exported method boundaries conservative because callers may be outside the
  closed world.
- Prefer one precise diagnostic over many noisy descendants of the same root cause.
