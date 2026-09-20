# Analysis And Optimization

## Human Review

Owns analysis facts and proof-backed transformations; the [compiler contract](compiler.md)
owns Java semantics. Existing analysis and scoped stack allocation are implemented, while
the additional transformations below are proposed capability scope, not a second delivery queue.
Current delivery order lives in [the roadmap](../roadmap.md).

Before another default pass or parallel compiler path, name the public workload, measure
compile time and peak memory, and define its semantic proof. No universal speed target or new
backend is selected here. Parallel compiler execution is owned by
[the concurrency contract](concurrency-runtime.md#requirements-and-acceptance).

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| OPT-001 | Analysis MUST discard unsupported or invalidated facts conservatively; it MUST NOT exclude a constructible receiver. | [Control-flow CLI](../../src/test/java/javan/ControlFlowCliTest.java), [method-effect CLI](../../src/test/java/javan/CliMethodEffectsIntegrationTest.java); unknown calls and merges remain conservative. |
| OPT-002 | A release rewrite MUST preserve effects, failure order and observable identity, and report the proof that permits it. | [Runtime translation](../../src/test/java/javan/CliRuntimeTranslationIntegrationTest.java); debug output preserves checks. |
| OPT-003 | Stack selection MUST preserve managed references and fall back to managed allocation outside its proven budget/lifetime. | [Escape CLI](../../src/test/java/javan/CliEscapeClassificationIntegrationTest.java), [memory proof](memory-runtime-correctness.md#tests-and-gates). |
| OPT-004 | A performance change MUST be evaluated through a public build with identified commit, fixture, toolchain, target and resources. Missing metrics MUST be reported as unknown. | [Package baseline command](../../.github/scripts/measure-package-build-baseline.sh), [historical measurements](../verification.md#build-baselines). New acceptance budgets require a workload and rationale. |

## Analysis Evidence

The canonical bytecode graph reports exact blocks and typed edges in
`.javan/reports/control-flow.json` and `.md`. Class-initialization owners, dependencies and
active-use sites are reported in `class-initialization.json` and `.md` in the same directory.
[Compiler semantics](compiler.md#current-boundary) owns their meaning.

Virtual/interface dispatch uses a reachable-construction fixpoint. Reachable `new`, enum
bootstraps, substitutions and externally supplied receivers/parameters contribute types;
materialized lambdas retain their exact target path. Unknown external receivers stay
conservative. `instantiated-types.json` and `.md` report the receiver set and its origins,
and C generation consumes the same facts as reachability.

Class structs/descriptors are emitted for reachable code, instantiated types, initialization
and required hierarchies. Stable type IDs preserve gaps when unused classes are omitted.
Incomplete analysis or reachable `Class.forName(String)` retains the parsed set conservatively.

Direct `Function.apply` and `Supplier.get` refine receivers through locals, casts, final fields,
direct arguments/returns and merges. `receiver-provenance.json` and `.md` report bounded sets
of up to four types; unknown/larger sets fall back to the global instantiated set. These are
existing implementation bounds, not newly chosen performance targets.

## Optimization Contract

The optimizer pipeline is:

```text
bytecode -> javan IR -> reachability/substitution -> deduplication plan -> CFG facts -> release optimizations -> backend
```

Implemented now:

- `DeduplicationPlanner` runs after reachability
- reports duplicate string literals, runtime module families, array helper families, and bounds helper families
- deduplicates infrastructure only; observable Java identity is not merged

Runtime module families:

- `random` implemented for default `SecureRandom` byte generation and version-4 UUIDs
- `time` implemented for time intrinsics
- `strings` implemented for current string helpers and basic Base64 text output/input
- `arrays` implemented for current array helpers and basic Base64 byte input/output
- `io` planned

Runtime initialization hooks:

- default `SecureRandom` and `UUID.randomUUID()` need no user hook; they read OS entropy on demand
- `initTime()` implemented through runtime time helpers
- `initConsole()` planned
- `initHeap()` planned

Implemented local facts:

- `NonNull(value)`
- `IsNull(value)`
- `TypeIs(value, class)`
- `Range(value, min, max)`
- `ArrayLength(array, value)`
- `StringLength(string, range)`
- `BooleanValue(value, true/false)`
- `SameValue(a, b)`

The facts flow through lowered control-flow blocks and merge conservatively. Unknown values
erase a fact instead of guessing. Debug builds preserve instructions and report candidates;
release builds remove a plain `Objects.requireNonNull(Object)` guard or fold a branch only
when the recorded entry facts prove the decision. Every decision is written to
`optimizations.json` and `optimizations.md` with its method and bytecode offset.

Facts still planned:

- enum constants

Method effects are implemented as a conservative transitive lattice over lowered functions:
pure, may-throw, allocates, reads, writes, and unknown. Exact application calls inherit their
callee effects, including recursive call groups. Current integer and object field facts survive
only proven non-writing calls; unknown calls, writes, and receiver reassignment invalidate them.
Other field kinds remain unoptimized rather than guessed. The same optimization report records
deterministic aggregate counts for the reachable method effects without dumping thousands of rows.

Managed allocation sites are classified conservatively as `NoEscape`, `ArgumentEscape`, or
`GlobalEscape`. The analysis follows local copies, control-flow merges, and transitive argument
capture through exact application calls; unknown calls remain argument escapes. Returns plus
heap/static stores are global escapes, and fixed resource bounds fall back to `GlobalEscape`.
Release builds consume this evidence for bounded constant primitive arrays and application objects
whose exact constructor and later calls do not capture the value. A stack object's runtime-state slot
and managed-reference fields remain in the function root frame, so contained heap objects survive GC,
reassignment, and nulling normally. Selected sites must remain outside control-flow cycles and share
one conservative 4 KiB function budget. Debug builds and all other allocation shapes remain managed.
Reports include the selected stack-allocation count.

Guard patterns:

- `Objects.requireNonNull(x)` implemented for proven non-null local values
- `if (x == null) throw`
- `if (x != null)`
- array index bounds
- `instanceof`
- range checks
- enum switch branches
- integer, array-length, and string-length branches implemented when their ranges prove the result
- pure validation methods later

Safety rules:

- do not remove checks with visible side effects
- do not remove checks with side-effecting message suppliers
- do not remove logging validations
- do not remove public/exported method guards globally if the method is reachable from unknown callers
- invalidate object-field facts after unknown calls that may mutate the object
- stay conservative with mutable objects, volatile, synchronized, and threads
- debug build keeps most checks
- release build removes only proven redundant checks

Prefer bounded closed-world facts over a general points-to engine. Global alias analysis,
symbolic execution, memory SSA, speculative/JIT tiers and profile-guided specialization
remain outside the current plan until a public workload demonstrates a need.

Additional transformation scope (delivery order belongs to the roadmap):

- extend dead-code elimination beyond generated class metadata to unreachable methods, fields, constructors, runtime modules, intrinsics, strings, vtables, and dispatch tables
- expand stack allocation to repeated sites only when publication, identity, and repeated-site lifetime are proven
- arena allocation for scoped temporary object graphs
- devirtualization
- method specialization
- generic specialization
- boxing elimination
- string literal deduplication, concat lowering, StringBuilder elimination, substring bounds proof, ASCII/UTF-8 fast path, and constant folding
- platform-aware intrinsics and substitutions for common JDK APIs

## Intrinsic Boundaries

The generated [support matrix](../status/support-matrix.md) owns scenario status, including
math, strings/concat, array copies, number conversion, clocks, entropy, environment/property
access and file operations. Do not maintain a second implemented-feature checklist here.

Constraints relevant to transformations remain explicit:

- `Math.atan2` uses host math linkage under [the ABI contract](native-abi.md#host-math-linkage).
- Default `SecureRandom` reads OS entropy; `UUID.randomUUID` produces canonical version-4 text.
- Basic Base64 supports byte arrays/encoded strings, strict basic-alphabet validation, padding
  and legal unpadded final units. URL, MIME, streams, no-padding mode and destination-buffer
  overloads remain outside that slice.
- `Class.forName(String)` covers compiled classes/valid arrays, one-time initialization and
  catchable lookup failures. Loader-selecting overloads remain outside that slice; the
  [compiler contract](compiler.md#current-boundary) owns broader lookup semantics.

## Optimization Reports

- `.javan/reports/deduplication-plan.json`
- `.javan/reports/deduplication-plan.md`
- `.javan/reports/optimizations.json`
- `.javan/reports/optimizations.md`
