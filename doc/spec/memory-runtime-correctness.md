# Memory And Runtime Correctness

Goal: make native memory behavior explicit, testable, and impossible to overclaim.

## Current Contract

The current native runtime is not a JVM heap.

| Area | Current behavior |
| --- | --- |
| Java objects and arrays | Allocated from tracked C heap through `javan_alloc`. |
| Allocation metadata | Every tracked allocation has kind, type id, runtime kind, size, mark, and collectibility metadata. |
| Allocation accounting | Runtime keeps live allocation, live byte, total allocation, total byte, and peak live byte counters. |
| Type descriptors | Generated descriptor table records class type ids and object-field offsets. |
| Java allocation lifetime | Generated objects, object arrays, primitive arrays, boxed primitive wrappers, runtime-owned strings, runtime containers, and owned container storage are eligible for safe-point mark/sweep; remaining tracked allocations are explicit runtime temporaries or released by registered shutdown cleanup. |
| FFI input strings/byte arrays | Copied into GC-managed Java strings/arrays before exported Java code runs; retained Java references remain valid after the C call returns. |
| FFI returned strings/byte arrays | Javan-owned export copies; caller releases with `javan_free`. |
| Temporary runtime buffers | Javan-owned; explicitly freed in the runtime path that allocated them. |
| Static roots | Generated static object-field inventory is registered before class initializers run. |
| Local/parameter roots | Generated root frames register object parameters and object locals until function return. |
| Local root liveness | Generated methods clear dead object local/parameter root slots at CFG-proven safe-point boundaries across straight-line code, labels, branches, and loops. |
| Return roots | Object-returning generated functions publish the returned object through a single-threaded static return root until the callee safe point and function-frame pop complete. |
| Safe-point placement | Generated entry, library init, labels, non-terminal statement boundaries, and protected object returns call `javan_gc_safe_point`. |
| Runtime containers | Lists, maps, iterators, optionals, string builders, and owned backing storage are tagged collectible and traced from reachable owners. `List.of` varargs are rooted before the target list allocation. Map backing-array growth publishes each moved buffer before any later allocation can collect. Heap metadata validation rejects stale list, map, and StringBuilder owned-buffer references. |
| Runtime size checks | StringBuilder growth rejects terminator-inclusive capacity overflow before C signed arithmetic can overflow. |
| Process results | Runtime-owned stdout/stderr strings are traced from reachable process results and released when the process result is explicitly freed. |
| Runtime string helper ownership | Current UTF-8 string helpers root runtime-owned sources across allocating substring, replace, char-array construction, copy, concat, StringBuilder append, path helper, and export-copy paths. |
| Managed heap | Full managed heap is not implemented. |
| Heap reclamation | Implemented for generated objects, object arrays, primitive arrays, boxed primitive wrappers, runtime-owned strings, runtime containers, and owned container storage at generated safe points. |
| Tracing GC | Partial single-threaded mark/sweep for generated object graphs, arrays, boxed primitive wrappers, runtime-owned strings, and runtime containers. |
| Operand/call-temporary roots | Generated object-producing expression temporaries are rooted until the enclosing generated statement or return completes. |
| Allocation-path collection | Scoped allocator path: checked allocation sizes, GC retry under heap pressure, and deterministic native panic for allocation denial on generated object/array/string paths. |
| Thread roots | Not implemented. |
| Metadata stress | `JAVAN_GC_STRESS=N` validates metadata/accounting every N allocator events. |
| Collection stress | `JAVAN_GC_SAFEPOINT_INTERVAL=N` collects every N generated safe points after roots/descriptors are registered. |
| Sanitizer instrumentation | Not part of normal builds yet; release/CI smoke recompiles generated C with sanitizers. |

This is acceptable for small deterministic CLI/native-library probes and should be
leak-clean at process exit under AddressSanitizer/LeakSanitizer. It is not enough for
long-running services, allocation-heavy applications, or thread-heavy programs because it
does not yet manage all Java/runtime allocation shapes during execution.

## Next Milestone: M13R Remote Release-Matrix No-Residue Proof

Goal: prove the generated native Javan binary itself is sanitizer-clean, counter-clean,
warning-free, and represented in fresh reports.

Status: Partial. Local package-backed self-host sanitizer proof exists on macOS
aarch64 with nonzero tracked allocation/GC counters, zero final live heap/root residue,
and no sanitizer failure signatures. Remote release-matrix validation remains 0/4
completed.

Exit criteria:

1. Build generated native Javan from `target/classes --main javan.Main` with sanitizer
   instrumentation and counter checks enabled.
2. Run the generated binary through `--version`, `check target/classes --main javan.Main`,
   `report target`, and one tiny supported build/check loop.
3. Require `.javan/reports/sanitizer-proof.json` and `.javan/reports/sanitizer-proof.md`
   for the self-host binary, not only for small native-profile apps.
4. Assert nonzero total allocations, nonzero GC collections, zero final live heap
   allocations, zero final live bytes, zero open root-frame depth, zero open frame roots,
   and no sanitizer failure signatures.
5. Run the proof in CI/release rows for every supported OS/ARCH target before claiming
   release-level no-leak confidence.

This milestone is intentionally smaller than full managed heap. It proves the production
tool binary under the current runtime contract before expanding into long-running service
memory behavior.

Local proof currently records the self-host probes through an exit-safe counter writer.
The latest local run produced nonzero tracked self-host allocations and GC collections
with zero final tracked heap/root residue. The remaining gate is remote release-matrix
proof on every supported OS/ARCH target.

## Required Managed Heap Design

Before claiming managed Java memory, Javan still needs:

- complete operand/call-temporary coverage for all eval-order and hostile safe-point paths
- thread roots once platform or virtual threads exist
- full string object model for UTF-16 semantics, literals, borrowed argv/env values, and
  ABI input ownership
- cycle-safe mark traversal for every Java heap shape, not only generated object graphs
- deterministic exception-during-allocation behavior
- a debug stress mode that forces collection at hostile points
- in-process reclamation for all Java allocation shapes with precise ownership boundaries

## Root Model

The first safe root model should be precise, not conservative scanning:

| Root kind | Requirement |
| --- | --- |
| Static fields | Generated object-field root table registered before class initializers. |
| Locals/parameters | Generated root frames push object parameters and locals, then pop before generated returns. |
| Direct object returns | Generated single-threaded return root protects returned objects through callee safe points and frame pop. |
| Operand/call-temporary values | Generated expression root frames protect object-producing temporaries for call arguments, nested call results, store operands, print operands, array operands, and return operands. |
| Object fields | Generated type descriptors identify object-field offsets and the collector traverses them. |
| Object arrays | Collector traverses each element. |
| Primitive arrays | Collectible leaf allocations, no reference traversal. |
| Boxed primitive wrappers | `Boolean`, `Integer`, `Long`, `Float`, and `Double` wrapper objects are collectible leaf allocations for the current value-of/unbox lowering. Wrapper cache identity and the full boxed-wrapper API are not claimed here. |
| Strings | Runtime-owned UTF-8 strings are tagged collectible; current helper paths root runtime-owned sources across helper and export-copy allocation. ABI string inputs are copied into GC-managed runtime strings. Non-NUL ASCII constants are valid for the current string subset; non-ASCII constants fail in `javan check` and native lowering when used with UTF-16-sensitive operations until the UTF-16 string object model exists. Literals and argv/env values remain borrowed/untracked unless explicitly copied. |
| Runtime containers | Tagged lists/maps/iterators/optionals/string builders trace contained values and owned backing storage only when the container is reachable. |
| FFI handles | Explicit handle table with ownership rules. |
| Threads | Per-thread root set once threads are implemented. |

## GC Milestones

1. Heap metadata and allocation accounting. Implemented for tracked allocations.
2. Static root registration. Implemented for generated object static fields.
3. Type descriptors for object fields. Implemented for generated classes.
4. Local/parameter root frames. Implemented for generated methods.
5. Straight-line local root liveness. Implemented.
6. Safe-point mark/sweep for generated objects and object arrays. Implemented.
7. Precise runtime-container tracing for lists, maps, iterators, optionals, string builders, and owned backing storage. Implemented.
8. Statement and loop-label safe points. Implemented for generated labels and non-terminal statement boundaries.
9. Protected direct object returns. Implemented with a single-threaded static return root.
10. Object expression temporary root frames. Implemented for generated object-producing
   statement and return operands.
11. Allocator-path GC retry, checked array allocation sizes, and deterministic allocation
    denial panic for generated object/array/string paths. Implemented.
12. Primitive-array leaf collection and export-wrapper roots for byte-array ABI inputs.
    Implemented.
13. Runtime-owned string collection under heap pressure. Implemented.
14. Runtime-container reclamation under heap pressure. Implemented for list/map/iterator/optional/string-builder profiles.
15. Runtime source-container rooting during copy/view helper allocations. Implemented
    for `List.of(array)`, `List.copyOf`, `Map.copyOf`, `Map.values`, and list iterator
    construction under heap pressure.
16. Reallocation heap-limit accounting, panic-time root-frame cleanup, and registry
    growth partial-allocation cleanup. Implemented.
17. Hostile eval-order and allocation-path stress for receiver temporaries, array-load
    temporaries, runtime string temporaries, nested runtime containers, caught exception
    values, and live-root heap-limit panic. Implemented.
18. Runtime string helper source rooting for current UTF-8 strings. Implemented for
    substring, replace, char-array construction, copy, concat, and StringBuilder append
    allocation paths under heap pressure.
19. Runtime boundary source rooting for path and export-copy helpers. Implemented for
    path normalize/resolve/of/parent/file-name/name/relativize, `javan_string_export`,
    and `javan_byte_array_export`.
20. Panic-expression temporary rooting, array-copy source rooting, sanitizer panic
    failure-signature rejection, and deterministic allocation-denial probes for current
    string, list, map, path, array-copy, and catch-path allocation families. Implemented.
21. Directory-stream source path rooting across result-list and child-path allocations.
    Implemented.
22. CFG-aware local root liveness across labels, branches, and loops. Implemented.
23. Boxed primitive wrapper collection for `Boolean`, `Integer`, `Long`, `Float`, and `Double`
    value-of/unbox lowering under heap pressure. Implemented.
24. HashMap backing-array growth publish-before-GC safety under heap pressure.
    Implemented.
25. `List.of` varargs element rooting before result-list allocation under heap pressure.
    Implemented.
26. Owned backing-buffer publish-before-validation invariants for `ArrayList`,
    `HashMap`, and `StringBuilder`. Implemented.
27. StringBuilder terminator-inclusive capacity overflow rejection before signed C
    arithmetic. Implemented.
28. GC-managed C ABI input copies for retained `String` and `byte[]` exports.
    Implemented.
29. Full stop-the-world mark/sweep for all Java heap shapes.
30. Stress GC mode with hostile-point collection across all allocation shapes.
31. Thread root integration.
32. Optional arena/request allocation for scoped workloads.
33. Escape-analysis stack allocation where proven safe.

## Ownership Rules

- Java-managed objects must never be returned to C callers as raw ownership.
- FFI `String` and `byte[]` results allocated by Javan must be released with
  `javan_free`.
- ABI v2 `JavanResult` diagnostic strings must be released with
  `javan_result_free`; they are not Java heap objects and callers must not use
  `javan_free` or raw `free` for them.
- `javan_free` rejects unknown pointers; callers must not use raw `free` for Javan-owned memory.
- C inputs passed into Javan are borrowed unless copied by a wrapper.
- Generated bindings must document who owns every pointer.
- Runtime reports must list the active ownership model.

## Tests And Gates

Current gates:

- `memory-soak` native-profile project allocates repeated objects, arrays, bytes, and strings
  and compares native output against the JVM.
- `static-root-inventory` native-profile project verifies generated static object roots while
  comparing native output against the JVM.
- `root-frame-stack` native-profile project verifies generated object descriptors and
  local/parameter root frames while comparing native output against the JVM.
- `gc-generated-object-graph` native-profile project runs with `JAVAN_GC_SAFEPOINT_INTERVAL=1`
  and verifies static-root graph traversal, object-array traversal, cycle-safe marking,
  and repeated generated-object reclamation against JVM output.
- `object-registry-gc` native-profile project runs with `JAVAN_HEAP_LIMIT_BYTES=3072`
  and verifies object registration cannot collect a new object while growing registry
  storage before the generated expression root exists.
- `protected-object-return` native-profile project runs with `JAVAN_GC_SAFEPOINT_INTERVAL=1`
  and verifies direct object returns remain valid across callee safe points, frame pop,
  caller allocation churn, and later dereference.
- `operand-call-temporary-roots` native-profile project runs with
  `JAVAN_GC_SAFEPOINT_INTERVAL=1` and verifies nested object call arguments, field-store
  operands, array-store operands, return operands, and allocation churn against JVM output.
- `large-arrays` native-profile project verifies checked allocation sizing for each
  supported primitive array family while comparing native output against the JVM.
- `primitive-array-gc` native-profile project runs with `JAVAN_HEAP_LIMIT_BYTES=8192`
  and verifies all primitive array families are collectible leaf allocations under heap
  pressure while comparing native output against the JVM.
- `boxed-boolean-gc`, `boxed-integer-gc`, `boxed-long-gc`, `boxed-float-gc`, and `boxed-double-gc`
  native-profile projects run with heap limits, `JAVAN_GC_STRESS=1`, and
  `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify `Boolean`, `Integer`, `Long`, `Float`, and `Double`
  value-of/unbox allocations are collectible leaf objects under heap pressure while
  comparing native output against the JVM.
- `runtime-filetime-gc` native-profile project runs with `JAVAN_HEAP_LIMIT_BYTES=8192`,
  `JAVAN_GC_STRESS=1`, and `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify
  `Files.getLastModifiedTime(...).toMillis()` allocates a collectible `FileTime` leaf object
  under heap pressure while comparing native output against the JVM.
- `runtime-duration-millis-gc` and `runtime-duration-seconds-gc` native-profile projects run
  with `JAVAN_HEAP_LIMIT_BYTES=8192`, `JAVAN_GC_STRESS=1`, and
  `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify `Duration.ofMillis/ofSeconds(...).toMillis()`
  allocates collectible `Duration` leaf objects under heap pressure while comparing native
  output against the JVM.
- `local-root-liveness-gc` native-profile project runs with
  `JAVAN_HEAP_LIMIT_BYTES=6000`, `JAVAN_GC_STRESS=1`, and
  `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify straight-line dead local roots are cleared
  before later allocations under heap pressure.
- `cfg-local-root-liveness-gc` native-profile project runs with
  `JAVAN_HEAP_LIMIT_BYTES=6000`, `JAVAN_GC_STRESS=1`, and
  `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify loop-local roots are cleared at CFG-proven
  safe points before later loop allocations under heap pressure.
- `string-growth-limit` native-profile project runs with `JAVAN_HEAP_LIMIT_BYTES=4096`
  and verifies dead runtime-owned strings are reclaimed under a configured heap ceiling
  while comparing native output against the JVM.
- `string-static-root` native-profile project runs with forced safe-point collection and
  verifies a dynamically-created static string remains live through generated static
  root inventory.
- `runtime-list-of-array-gc`, `runtime-list-of-varargs-gc`, `runtime-list-copy-gc`,
  `runtime-map-copy-gc`, and `runtime-map-values-gc` native-profile projects run with
  `JAVAN_HEAP_LIMIT_BYTES` and verify source arrays, vararg elements, source lists,
  source maps, and derived value views survive runtime helper allocation and later GC
  pressure.
- `runtime-map-realloc-gc` runs with `JAVAN_HEAP_LIMIT_BYTES=8192`,
  `JAVAN_GC_STRESS=1`, and `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify `HashMap`
  backing-array growth publishes moved buffers before later allocator-triggered GC can
  traverse the rooted map.
- `runtime-realloc-growth-fit` native-profile project runs with
  `JAVAN_HEAP_LIMIT_BYTES=300` and `JAVAN_GC_STRESS=1` and verifies `realloc`
  charges only positive replacement-buffer growth against the configured heap ceiling.
- `operand-call-receiver-temporary-root`, `operand-array-load-temporary-root`,
  `operand-object-compare-temporary-root`, `operand-field-load-temporary-root`,
  `operand-chained-field-load-temporary-root`,
  `operand-chained-call-receiver-temporary-root`, `runtime-string-temporary-root`,
  `runtime-nested-container-reclaim`, and
  `exception-catch-heap-pressure` native-profile projects run with heap limits,
  `JAVAN_GC_STRESS=1`, and `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify hostile
  eval-order roots and nested runtime-container reclamation against JVM output.
- `runtime-string-substring-source-root`, `runtime-string-replace-source-root`,
  `runtime-string-from-chars-source-root`, `runtime-string-char-array-copy-gc`, and
  `runtime-stringbuilder-append-source-root` native-profile projects run with heap
  limits, `JAVAN_GC_STRESS=1`, and `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify current
  UTF-8 string helper source ownership across allocating helper paths against JVM output.
- `runtime-directory-stream-source-root` runs with heap limits, `JAVAN_GC_STRESS=1`,
  and `JAVAN_GC_SAFEPOINT_INTERVAL=1` to verify `Files.newDirectoryStream` keeps the
  source path live while allocating the result list and child paths.
- `runtime-directory-stream-result-allocation-limit-panic` denies the result-list allocation
  before any native `DIR*` handle is opened and verifies deterministic panic output under
  sanitizer checks.
- `runtime-read-string-allocation-limit-panic` and
  `runtime-read-all-bytes-allocation-limit-panic` deny result allocations after a `FILE*`
  has been opened and verify panic-time native-resource cleanup under sanitizer checks.
- `runtime-directory-stream-child-allocation-limit-panic` denies child-path allocation
  after a `DIR*` has been opened and verifies panic-time native-resource cleanup under
  sanitizer checks.
- `runtime-process-run-output-allocation-limit-panic` denies captured stdout allocation
  after process temp `FILE*` handles have been opened and verifies panic-time
  native-resource cleanup under sanitizer checks.
- `runtime-stringbuilder-setlength-overflow-panic` verifies
  `StringBuilder.setLength(2_147_483_647)` fails with deterministic
  `string builder length overflow` before sanitizer-visible signed overflow.
- `native-library` acceptance and sanitizer probes verify C ABI `String` and `byte[]`
  inputs are copied into GC-managed Java values, survive caller-side buffer mutation when
  retained by exported Java code, and are reclaimed after exported Java clears the retained
  references.
- `RuntimeFilesTest` compiles direct C runtime-boundary probes with heap limits and
  `JAVAN_GC_STRESS=1` to verify `List.of` vararg element rooting, `ArrayList`,
  `HashMap`, and `StringBuilder` owned-buffer realloc validation, path normalization,
  path relativization suffix copies, `String` export copies, byte-array export copies,
  array copies, array range copies, directory-stream source paths, and process-result
  explicit free ownership when generated Java roots are not present.
- `panic-string-concat-temporary-root` verifies uncaught panic expressions root allocated
  string-concat operands under heap and safe-point stress before `javan_panic` cleanup.
- `heap-limit-live-root-panic` native-profile project runs with live generated roots,
  heap-limit denial, GC stress, and safe-point stress to verify deterministic panic
  cleanup with exact stderr and empty stdout.
- `allocation-path-gc` native-profile project runs with `JAVAN_HEAP_LIMIT_BYTES=4096`
  and verifies allocator-path collection retries preserve JVM-equivalent output.
- `negative-array-length`, `allocation-limit-panic`, `string-allocation-limit-panic`,
  `exception-catch-allocation-limit-panic`, `runtime-list-allocation-limit-panic`,
  `runtime-map-allocation-limit-panic`, `runtime-path-allocation-limit-panic`,
  `runtime-read-string-allocation-limit-panic`,
  `runtime-read-all-bytes-allocation-limit-panic`,
  `runtime-directory-stream-result-allocation-limit-panic`,
  `runtime-directory-stream-child-allocation-limit-panic`,
  `runtime-process-run-output-allocation-limit-panic`, and
  `array-copy-allocation-limit-panic` native-profile projects verify deterministic
  native panic output for invalid sizes and allocation denial with exact stderr and empty
  stdout.
- `.github/scripts/sanitizer-suite.sh` recompiles generated C with Address/Undefined sanitizer flags
  when supported by the host compiler. It covers normal completion, straight-line and
  CFG local root liveness, native panic, and `System.exit` cleanup paths. It also enables
  `JAVAN_GC_STRESS` metadata validation and `JAVAN_GC_SAFEPOINT_INTERVAL` collection stress by default. Leak detection is enabled by
  default; on macOS, where LeakSanitizer is not available, the suite falls back to
  `leaks --atExit`.
- `.github/scripts/sanitizer-suite.sh` runs `memory-soak` in counter-check mode with
  `JAVAN_HEAP_LIMIT_BYTES=32768`, `JAVAN_GC_STRESS=1`, and
  `JAVAN_GC_SAFEPOINT_INTERVAL=1`. The wrapper calls final `javan_gc_collect()`,
  validates heap metadata, requires zero final live allocations and bytes, caps peak
  live bytes at the heap limit, and requires minimum total/GC/collected allocation
  counters so the test cannot pass through a no-op workload. The script writes
  `.javan/reports/sanitizer-proof.json` and `.javan/reports/sanitizer-proof.md`
  with actual live allocation, live byte, peak byte, total allocation, GC collection,
  and collected allocation counters. The suite asserts the proof file for `memory-soak`
  and runs `javan report` to prove the unified report exposes the same sanitizer-proof
  family and zero-live-heap counters.
- `.github/scripts/sanitizer-library-smoke.sh` recompiles the native-library C ABI path with
  sanitizer flags and runs primitive, repeated `String`, and repeated byte-array exports
  plus `javan_free` ownership paths under a constrained heap. It also covers null
  `String` inputs, empty and negative `byte[]` inputs, structured borrowed
  `javan_last_error_*` fields, and last-error clear semantics. It also runs a counter
  probe that repeats C ABI `String` and `byte[]` exports, frees every Javan-owned
  result, calls final `javan_gc_collect()`, validates heap metadata, requires zero
  live allocations/bytes and zero open root frames, caps peak live bytes, and requires
  minimum total/GC/collected allocation counters. The script writes the same
  sanitizer proof report with actual live allocation, live byte, peak byte, GC, root
  frame depth, and frame-root counters. The suite asserts the native-library proof file
  and runs `javan report` to prove the unified report exposes zero-live-heap and
  zero-open-root counters for the library ABI path.
  The same smoke builds generated Rust/Go/Python binding packages; Python ownership runs when `python3` is available,
  and Rust/Go ownership runs when `rustc` or `go` are available. When
  `JAVAN_SANITIZER_REQUIRED=true`, missing language toolchains fail the gate.
- Sanitizer panic probes fail if stderr contains AddressSanitizer, LeakSanitizer, or
  UndefinedBehaviorSanitizer failure signatures even when the expected panic text is also
  present.
- CI and release verification run the suite with `JAVAN_SANITIZER_REQUIRED=true`, so
  unsupported sanitizer flags, an unavailable leak fallback, or missing generated
  binding toolchains fail the gate instead of silently skipping. CI and release jobs
  install Go `1.26.4` and Rust `1.96.0` before the required sanitizer suite.
- Runtime reports state the exact current memory model and sanitizer posture.

Required future gates:

- sanitizer/leak smoke on Windows once the runtime/linker port exists and release
  footprint jobs are wired.
- full eval-order validation for expression temporary roots across every supported IR
  shape not covered by the current hostile receiver, array-load, runtime-string,
  nested-container, caught-exception, and live-root panic probes.
- hostile-point GC collection stress across every supported allocation shape.
- full string object model for UTF-16, literal/borrowed ownership, and ABI input copying
  where needed.
- remaining exception-during-allocation behavior outside the current panic and
  same-method catch model.
- thread root stress once threads exist.
