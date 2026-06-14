# Compile-Time Runtime-Risk Warnings

Status: roadmap only. This file describes planned behavior and does not claim current
implementation.

## Goal

`javan check` and `javan build` should warn when reachable code may fail at runtime. The
analysis is conservative: it may report uncertain risk, but it must not claim safety unless
the required facts are proven.

## Planned Risk Checks

Initial checks:

- possible null dereference
- unsafe array index
- unsafe `String.charAt`
- unsafe `String.substring`
- `List.get(0)` without non-empty proof
- `Optional.get` without `isPresent` proof
- `Iterator.next` without `hasNext` proof
- division or modulo by possible zero
- unsafe casts without `instanceof` proof
- uncaught or panic-style exception paths
- redundant checks that can later feed release optimization

## Analysis Model

The planned analysis runs after bytecode lowering and before backend code generation:

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

- definite runtime failure is an error
- likely runtime failure is a warning
- uncertain finding is info and appears only in `--strict`

The analyzer should avoid warning spam by reporting the nearest source location and grouping
duplicate findings by diagnostic id, source location, risk kind, and reachable path.

## CLI And Report Policy

Planned command behavior:

- `javan check` reports errors and warnings for reachable code
- `javan build` runs the same analysis before native generation
- `javan report` reads and summarizes the generated report model
- strictness, warnings-as-errors, and feature toggles should be available from project or
  global settings before adding more public flags
- `javan explain <diagnostic-id>` may print the rule, examples, and suggested fixes once
  the diagnostic catalog is stable

## Planned Reports

Generated report paths:

- `.javan/reports/safety-warnings.json`
- `.javan/reports/safety-warnings.md`
- `.javan/reports/report.json`
- `.javan/reports/report.md`

The JSON report should preserve stable diagnostic ids, severity, class, method, descriptor,
source file, line, reachable path, facts used, invalidation points, reason, and fix. The
Markdown report should group findings by severity and source path.

## Constraints

- Never claim safety unless proven.
- Do not issue a warning for unreachable code unless a strict compatibility mode asks for it.
- Do not remove checks here; optimization remains a separate release-mode pass.
- Keep public/exported method boundaries conservative because callers may be outside the
  closed world.
- Prefer one precise diagnostic over many noisy descendants of the same root cause.
