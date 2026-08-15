# Optimizer Roadmap

The optimizer pipeline is:

```text
bytecode -> javan IR -> reachability/substitution -> deduplication plan -> CFG facts -> release optimizations -> backend
```

Implemented now:

- `DeduplicationPlanner` runs after reachability
- reports duplicate string literals, runtime module families, array helper families, and bounds helper families
- deduplicates infrastructure only; observable Java identity is not merged

Runtime module families:

- `random` planned
- `time` implemented for time intrinsics
- `strings` implemented for current string helpers
- `arrays` implemented for current array helpers
- `io` planned

Runtime initialization hooks:

- `initSecureRandom()` planned
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
Release builds consume this evidence for bounded constant primitive arrays and reference-free
application objects whose exact constructor and later calls do not capture the value. Selected sites
must remain outside control-flow cycles and share one conservative 4 KiB function budget. Debug builds,
objects with managed-reference fields, and all other allocation shapes remain managed. Reports include
the selected stack-allocation count.

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

Release-mode optimization backlog:

- smart dead-code elimination for unreachable classes, methods, fields, constructors, runtime modules, intrinsics, strings, vtables, and dispatch tables
- expand stack allocation to managed-reference fields and repeated sites only when contained-reference
  rooting, publication, identity, and repeated-site lifetime are proven
- arena allocation for scoped temporary object graphs
- devirtualization
- method specialization
- generic specialization
- boxing elimination
- string literal deduplication, concat lowering, StringBuilder elimination, substring bounds proof, ASCII/UTF-8 fast path, and constant folding
- platform-aware intrinsics and substitutions for common JDK APIs

Intrinsic status:

| Intrinsic | Status |
| --- | --- |
| `Objects.requireNonNull(Object)` | implemented |
| `Math.abs/min/max` for `int` and `long` | implemented |
| `Math.atan2(double, double)` | implemented through the host math library |
| `System.arraycopy` | implemented |
| `Arrays.copyOf` for supported primitive/object arrays | implemented |
| `String.equals` | implemented |
| `String.length` | implemented |
| `String.isEmpty` | implemented |
| `String.charAt` | implemented |
| javac `StringConcatFactory` concat | implemented for supported shapes |
| `Integer.toString(int)` | implemented |
| `Long.toString(long)` | implemented |
| `System.currentTimeMillis` | implemented |
| `System.nanoTime` | implemented |
| `SecureRandom.nextBytes` | planned |
| `UUID.randomUUID` | planned |

Auto-substitution candidates:

- `new SecureRandom()` to the javan random runtime module and OS entropy
- `System.getenv` subset
- `System.getProperty` subset
- `Path.of`
- `Files.readString`
- `Files.writeString`
- `UUID.randomUUID`
- Base64 encoder/decoder

Reports:

- `.javan/reports/deduplication-plan.json`
- `.javan/reports/deduplication-plan.md`
- `.javan/reports/optimizations.json`
- `.javan/reports/optimizations.md`
