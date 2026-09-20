# Diagnostics

## Human Review

Owns failure presentation, source mapping and evidence-based static hazard diagnostics.
[Compiler](compiler.md) owns Java exception semantics; [native ABI](native-abi.md) owns
cross-boundary error transport. Static detection and runtime rendering are separate capabilities
with one shared diagnostic/report contract.

Status: Partial. Exact literal hazards and scoped runtime source mapping are implemented;
broader flow-sensitive warnings, expression ranges and call paths remain planned.
Before broadening rejection or severity, define proven versus possible failure and its public
diagnostic. Before richer traces, agree which source metadata survives optimization and how
missing source is represented. This consolidation adds no rejection policy or CLI option.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| ERR-001 | Supported diagnostics MUST explain the failure with stable identity and available Java source location, without fabricating missing metadata. | [Runtime translation CLI](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java), diagnostic/report behavior below. |
| ERR-002 | Supported native-library failures MUST use the owning ABI error contract and remain recoverable as documented. | [Native ABI](native-abi.md#error-and-result-abi), [package tests](../../src/test/java/javan/CliPackagingIntegrationTest.java). |
| ERR-003 | New native/source trace rendering MUST map to the original Java behavior before it is claimed as supported. | Broader call paths, expression ranges and debug-native frames remain planned below. |
| RISK-001 | Exact supported reachable failures MUST be diagnosed before native generation; unreachable findings MUST remain visible without failing the build. | [Safety CLI tests](../../src/test/java/javan/CliSafetyDiagnosticsIntegrationTest.java). |
| RISK-002 | Unknown, merged or invalidated facts MUST NOT be reported as proven safe or definite failures. | Existing literal boundaries below; broader flow-sensitive findings remain planned. |
| RISK-003 | A diagnostic MUST preserve stable identity, source context and a useful explanation in the shared report model. | [Safety CLI tests](../../src/test/java/javan/CliSafetyDiagnosticsIntegrationTest.java); broader report fields below are proposals. |

## Shared Contract

Build/check diagnostics and runtime failures should point to the Java the user wrote, with a
stable code, short summary, available class/method/source location, reason and concrete fix.
Hide generated C, JVM-internal, linker and specialized-method names from default output.
Do not invent exact source locations when metadata is absent, or collapse distinct reachable
failure paths into one vague error. Reports keep deterministic ordering.

One root cause should produce a precise diagnostic rather than many noisy descendants.
Broader analysis should group duplicate findings by diagnostic ID, source location, risk kind
and reachable path. Diagnostic analysis does not remove runtime checks: proof-backed rewriting
belongs to [optimization](analysis-and-optimization.md).

### Current Reports

Paths below are relative to `.javan/reports/`:

| Output | Current scope |
| --- | --- |
| `diagnostics.txt`, `diagnostics.json`, `diagnostics.md` | Shared build/check findings, including exact unreachable hazards without terminal errors. |
| `exceptions.json`, `exceptions.md` | Native-build lowered panic sites; not a complete runtime exception inventory. JSON includes `sourceLine` when found, and Markdown includes that source line. |
| `debug-map.json` | Generated panic symbols and Java source mapping; generated names remain here rather than in default diagnostics. |
| `report.json`, `report.md` | Unified summary of generated report families, also read/refreshed by `javan report`. |

These reports already exist. Expanded IDE/LSP fields and extra `safety-warnings.json`/`.md`
reports remain proposals, not current output promises. JSON must remain deterministic and
suitable for tool consumption; Markdown should remain concise for people and CI logs.

## Compile-Time Hazards

`javan check` and `javan build` diagnose runtime failure only when available facts prove it.
The current verifier tracks literal `null`, one-dimensional literal array lengths, supported
ASCII string lengths and direct local copies within one straight-line bytecode segment.

| Proven failure | Reachable error | Unreachable report warning |
| --- | --- | --- |
| Null receiver for an argument-free instance call, field read or `arraylength` | `JAVAN070` | `JAVAN170` |
| Literal array-read index outside a locally created literal array length | `JAVAN071` | `JAVAN171` |
| Literal `String.charAt` index outside a supported ASCII literal | `JAVAN072` | `JAVAN172` |
| Literal `String.substring` start/end outside a supported ASCII literal | `JAVAN073` | `JAVAN173` |

Reachable errors stop before native code generation. Unreachable findings remain visible in
the shared reports without failing the command. Non-ASCII literals retain the existing
`JAVAN046` UTF-16-profile diagnostic, not a second bounds finding for an unrepresentable string.

Reassigned locals, parameters, fields, returned values, calls with arguments, array writes,
dynamic lengths/indexes, branch merges, exception paths and other dynamic shapes remain unknown
to this slice. They keep runtime checks. Unknown is neither safe nor a proven failure.
Public/exported boundaries remain conservative because callers may be outside the closed world.

### Planned Analysis Expansion

The literal rule runs during static verification. Broader diagnostic analysis is proposed after
lowering: `bytecode -> IR -> CFG -> flow-sensitive facts -> diagnostics -> reports`.
The optimizer already uses [local facts](analysis-and-optimization.md#optimization-contract);
that does not establish broader warning support.

The proposed diagnostic facts include non-null/maybe-null, numeric ranges, array/string lengths,
collection sizes, exact types, Boolean values and value equality. Mutation, unknown calls and
volatile/thread-visible state must invalidate facts that can no longer be proven.
`Objects.requireNonNull` may establish non-nullness after return; project-local null/non-empty
guard summaries require understood bytecode and proof that side effects do not invalidate them.
Unknown helpers remain ordinary calls.

Candidate checks include broader null receivers/arguments, array writes/indexes, unguarded
`List.get(0)`, `Optional.get` and `Iterator.next`, division/modulo by zero, unsafe casts,
uncaught/panic paths and redundant checks that may later inform optimization.

The earlier severity proposal adds warnings for likely failures and strict-only information for
uncertain findings. It is not implemented or an approved new build gate. Agree severity and
cost before expansion. Prefer project/global configuration for strictness, warnings-as-errors
and feature toggles before adding public flags. A `--strict` diagnostic flag and
`javan explain <diagnostic-id>` remain proposals; the existing `strict` build profile is separate.

Expanded reports should preserve descriptor, source file/line, reachable path, facts used,
invalidation points, reason and fix, grouping Markdown findings by severity and source path.

## Runtime Source Mapping

Current rendering covers uncaught generated `athrow` panics and runtime-helper panics emitted
within generated Java statement context:

- `LineNumberTable` and `SourceFile` are used when present, with a deterministic
  `<Class>.java` filename fallback; absent metadata is not an exact location.
- App stderr prints code, summary, Java class/method/file/line, bytecode offset, why, detail
  and fix. Source-backed builds add a `Code:` line and caret marker.
- Native exports keep a compact `javan_last_error()` envelope with code, summary, location,
  bytecode offset and detail; it is not the full app stderr layout. Ownership and recovery
  follow [the ABI contract](native-abi.md#error-and-result-abi).
- Allocation-free stack nodes retain active source context. Nested calls restore the caller,
  and recovered library panics do not leave stale source pointers.
- Runtime-internal panics outside generated source context have no Java source mapping;
  generic native panic fields do not establish a Java location.
- Helper failures map to the consuming IR statement, not yet the exact expression or operand.

### Planned Rendering

Full Java exception semantics, exact expression/range highlighting, reachable call paths,
expression-level helper blame and `--debug-native` frame expansion remain unimplemented here.
The target includes source-focused null, array/string bounds, cast, divide-by-zero, uncaught
supported platform-exception and explicit panic diagnostics. This rendering target does not
claim that all underlying exception behavior is missing or supported.

For example, the richer target could explain a proven null failure as follows; the expression
blame, explanation of its origin and call path are proposed output, not current fields:

```text
[JAVAN-RUNTIME-NULL] `user` is null

Where:
  com.acme.UserService.save(UserService.java:42)

Code:
  user.name()
  ^^^^ null

Why:
  `repository.find(id)` can return null.
  No null-check happened before this access.

Fix:
  Add a guard:
    Objects.requireNonNull(user, "user");

Path:
  Main.main -> UserController.handle -> UserService.save
```

The broader debug map should trace optimized, specialized, deduplicated, inlined, generated
wrapper and C-export methods to Java origins, preserving original class/method/descriptor,
source file/line tables, transformation reasons and reachable path segments when known.
Java frames come first; optional native detail must not replace source-focused default output.
Unknown internal failures should still receive stable identity and actionable remediation.
