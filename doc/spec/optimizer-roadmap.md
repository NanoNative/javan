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

Facts planned for redundant-check elimination:

- `NonNull(value)`
- `IsNull(value)`
- `TypeIs(value, class)`
- `Range(value, min, max)`
- `LessThan(value, bound)`
- `GreaterEqual(value, bound)`
- `ArrayLength(array, value)`
- `StringLength(string, range)`
- `BooleanValue(value, true/false)`
- `EnumValue(value, enumConstant)`
- `SameValue(a, b)`

Guard patterns:

- `Objects.requireNonNull(x)`
- `if (x == null) throw`
- `if (x != null)`
- array index bounds
- `instanceof`
- range checks
- enum switch branches
- string length checks
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
- escape analysis and stack allocation
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
