# javan Roadmap

## Feature Lab Workflow

Large features should be built as small isolated slices in temporary labs, agent work
areas, or spike branches, then reviewed and migrated into `javan`. See
[feature-lab-workflow.md](feature-lab-workflow.md).

## Cross-Platform Verification

Docker, JDK matrix, cache, negative-test, and release-gate policy lives in
[cross-platform-verification.md](cross-platform-verification.md).

## Independent Workspaces

Large standalone tracks such as JavanUI, Javan Studio, Java SDK wrapping, build plugins,
Homebrew packaging, and IDE integration may live in separate repositories or ignored labs
with their own context files. See [independent-workspaces.md](independent-workspaces.md).

## Examples And Test Projects

Public examples, acceptance projects, and test-only test projects are intentionally separate.
See [examples-and-test-projects.md](examples-and-test-projects.md).

## Status Language

Roadmap sections use production-slice language:

- `Implemented slice`: behavior available and verified for the stated scope.
- `Current gates`: checks that keep the slice honest.
- `Open acceptance criteria`: work still required before the umbrella feature is complete.

Broad Java features are not complete until their open acceptance criteria are empty and
the support matrix has no unknown leftovers. An implemented slice must not imply full
Java compatibility for the whole umbrella feature.

## 0.1 Native Hello

Real CLI, project detection, build invocation, class scanning, main detection, reachable
verification, C generation, native link, and integration tests.

## 0.2 Static Primitives

Implemented slice:

- int locals
- int arithmetic
- static int arguments and returns
- `System.out.println(int)`
- scalar boolean values
- `System.out.println(boolean)`
- scalar long values
- scalar float values
- scalar double values
- int comparisons
- long, float, and double comparisons
- simple `if/else`
- mutable int locals
- `i++` / `i--`
- simple `while`

Current gates:

- active JDK support-matrix scenarios for primitive printing, arithmetic, comparisons,
  `if/else`, and `while`
- native-profile verification for the supported scalar bytecode shapes emitted by `javac`

Open acceptance criteria:

- richer loop/control-flow validation
- primitive widening, narrowing, and casts

## 0.25 Simple Objects

Implemented slice:

- object allocation for known application classes
- constructor calls
- int instance fields
- object instance fields
- field reads and writes
- object/string returns
- null constants and null branches
- exact instance calls on final classes and concrete classes with no known subclass
- object references through locals and static helper parameters
- object arrays and `arraylength`
- int primitive arrays
- boolean, byte, short, and char primitive arrays
- runtime `String[] args`
- simple records
- uncaught platform exception throws as deterministic native panic
- default-constructed platform throwable `getMessage()` preserves JVM-style `null`
- unsupported platform throwable constructor signatures reject during `check`
- direct same-method platform exception catch lowering
- typed same-method platform catch routing for exact, broad, runtime, IO, util runtime,
  and Error-vs-Exception cases covered by the supported throwable hierarchy
- basic enum constants with `name()` and `toString()`
- compiler-emitted `Enum.<init>(String,int)` supported as a no-op superclass constructor
- enum `ordinal()`, `values()`, and javac enum switch-map lowering
- unreachable javac-generated enum `valueOf(String)` boilerplate is recognized without
  claiming support, while reachable enum `valueOf(String)` is rejected explicitly
- monomorphic interface dispatch
- closed-world virtual dispatch tables
- closed-world polymorphic interface dispatch tables
- `String.length`, `String.isEmpty`, `String.charAt`, and `String.equals` intrinsics
- javac `StringConcatFactory` string concatenation
- static fields and reachable class initializers for supported bytecode
- long, float, and double primitive arrays
- object-array and int-array `clone()` lowering
- object reference compare branches and dense/sparse integer switch bytecode

Current gates:

- support-matrix scenarios for records, fields, arrays, enums, dispatch, string
  intrinsics, concat, static fields, switch bytecode, and scoped catch handling
- native-profile typed-catch scenarios for first-handler miss, runtime superclass,
  IO superclass, util runtime superclass, and Error not matching Exception
- unsupported reachable object, exception, dispatch, and string bytecode must reject
  before native code generation

Open acceptance criteria:

- non-ASCII/full UTF-16 string runtime semantics
- general try/catch/finally exception-handler lowering
- richer class initialization ordering across complex dependency graphs
- full `java.lang.Enum` object identity and initialization semantics beyond the current constant-as-string model

## 0.27 Deterministic Compatibility

Implemented slice:

- `javan compat`
- active JDK inventory through the `jrt:/` image
- project/dependency bytecode pattern inventory
- explicit opcode support classification
- fatal unknown-opcode policy
- generated compatibility summary reports
- generated support matrix docs

Current gates:

- active-JDK inventory and bytecode-pattern scans write deterministic reports
- unknown opcodes remain fatal instead of being ignored or treated as best effort
- support matrix records named pass/scoped/target scenarios without claiming full JDK API
  support

Open acceptance criteria:

- multiple configured JDK homes in one run
- direct `javac --release` probe compilation matrix
- committed test-project baselines per JDK release
- API inventory diffs between JDK releases
- bootstrap-method shape policy gates beyond reporting

## 0.28 Native Library Output

Implemented slice:

- build kinds: `app`, `jar`, `library`, `staticlib`, and `sharedlib`
- library mode without requiring `Main.main`
- reachability roots from explicit exports
- CLI exports with `--export`
- `javan.toml` exports through `[exports].methods`
- generated C ABI wrappers
- C header generation
- Rust FFI binding generation
- Go cgo binding generation
- Python ctypes loader generation
- `javan_free` ownership hook for javan-owned exported memory
- `String` export as UTF-8 `char*`
- `byte[]` export as pointer+length
- versioned C ABI header macros (`JAVAN_ABI_VERSION = 1`)
- generated C ABI compile tests
- reported string ownership, byte-array ownership, error/result ABI, exception mapping,
  and thread/runtime rules
- library metrics reports
- deduplication planner reports after reachability
- friendly `javan build --library`
- `--format static|shared|both`
- per-language package folders under `.javan/dist/lib/<name>/<language>`

Current gates:

- app, JVM jar, and native library outputs remain distinct supported outputs
- support-matrix scenarios cover static int exports, `String` exports, `byte[]` exports,
  no-main library builds, and C/Rust/Go/Python binding smoke checks
- library metrics and deduplication reports are generated from the same reachability
  model as app builds

Open acceptance criteria:

- annotation-driven exports
- stable `JavanResult` error/result ABI
- exception-to-result mapping for library mode
- per-export thread/reentrancy reports
- richer object/record ABI models
- cross-target library linking
- Windows import-library details
- binding package manifests for Cargo, Go modules, and Python wheels
- direct LLVM/Cranelift backends after the C backend remains deterministic

## 0.285 JVM Jar And Resources

Implemented slice:

- `javan build --kind jar`
- jar builds without requiring `Main.main`
- optional jar manifest `Main-Class` through `--main`
- jar builds bypass native-profile verification and keep normal JVM bytecode
- plain `javac` resource copying from `src/main/resources` and `resources`
- resource inclusion in generated jars
- native app/library resource preservation under `.javan/resources` and `.javan/dist/resources`
- `.javan/reports/resources.md`
- `.javan/reports/resources.json`

Current gates:

- support-matrix scenarios cover jar output, jar manifest output, resource copy, stale
  resource removal, and native resource distribution
- resources are supported as artifacts today: jars include them and native app/library
  builds preserve them beside generated artifacts

Open acceptance criteria:

- native `ClassLoader.getResource*` and `getResourceAsStream` runtime API
- generated C resource tables for embedded read-only resources
- resource lookup ABI for native library consumers
- resource compression and checksum reporting

## 0.285 Memory And Runtime Correctness

Status: implemented process-lifetime cleanup, allocation metadata/accounting, generated
type descriptors, generated static-root inventory, generated local/parameter root frames,
statement/label safe points, protected direct object returns, safe-point mark/sweep for
generated objects, object arrays, primitive arrays, and runtime-owned strings, generated
expression temporary root frames, conservative runtime-container pinning, reporting,
soak, and sanitizer-stress slice. Full managed heap
coverage is still planned. See
[memory-runtime-correctness.md](memory-runtime-correctness.md).

Current slice:

- runtime reports state the active allocation model, ownership rules, GC/root status, and
  sanitizer posture
- generated runtime tracks `javan_alloc` allocations with kind/type/runtime-kind/mark/
  collectibility metadata plus live/total/peak accounting and frees remaining allocations
  during shutdown
- generated app/library code registers static object-field roots before class
  initializers run
- generated app/library code registers class type descriptors and object-field offsets
- generated functions push object parameter/local root frames and pop them before
  generated returns
- generated object-returning functions publish direct return values through a
  single-threaded static return root until callee safe points and frame pop complete
- generated object-producing expression temporaries are rooted through statement/return
  expression root frames for nested object call arguments, store operands, print operands,
  array operands, and return operands
- generated safe points can mark static roots, frame roots, generated object fields,
  object-array elements, primitive-array leaf allocations, runtime-owned strings,
  runtime containers, and owned container storage
- generated safe points are emitted at entry/init, labels, non-terminal statement
  boundaries, protected object returns, and expression-rooted statement/return operands;
  allocator paths now retry GC under heap pressure before deterministic out-of-memory
  failure for generated object/array/string allocation
- generated objects, object arrays, primitive arrays, runtime-owned strings,
  runtime containers, and owned container storage unreachable at safe points are swept;
  explicit runtime temporaries and FFI exports are excluded from GC
- native library byte-array ABI inputs are rooted while wrapper-created Java byte arrays
  are live
- native library object return values are rooted until `String`/`byte[]` ABI export copies
  complete
- explicit `javan_free` of process results releases owned stdout/stderr runtime strings
- native acceptance runs `memory-soak`, a repeated allocation JVM-vs-native equivalence
  project, `static-root-inventory`, `root-frame-stack`, `gc-generated-object-graph`, plus
  `object-registry-gc`, `protected-object-return`, `operand-call-temporary-roots`,
  `large-arrays`, `primitive-array-gc`, `string-static-root`, `string-growth-limit`,
  `runtime-container-live-roots`, `runtime-list-reclaim`, `runtime-map-reclaim`,
  `runtime-optional-reclaim`, `runtime-iterator-reclaim`,
  `runtime-stringbuilder-reclaim`, `runtime-list-of-array-gc`,
  `runtime-list-copy-gc`, `runtime-map-copy-gc`, `runtime-map-values-gc`,
  `runtime-realloc-growth-fit`, `operand-call-receiver-temporary-root`,
  `operand-array-load-temporary-root`, `runtime-string-temporary-root`,
  `runtime-string-substring-source-root`, `runtime-string-replace-source-root`,
  `runtime-string-from-chars-source-root`, `runtime-string-char-array-copy-gc`,
  `runtime-stringbuilder-append-source-root`, `runtime-nested-container-reclaim`,
  `runtime-directory-stream-source-root`, `exception-catch-heap-pressure`,
  `typed-catch-specific-miss`, `typed-catch-runtime-superclass`,
  `typed-catch-io-superclass`, `typed-catch-util-runtime-superclass`,
  `typed-catch-error-not-exception`,
  `exception-default-message-null`, `exception-default-panic`,
  `panic-string-concat-temporary-root`, `heap-limit-live-root-panic`,
  `allocation-path-gc`, `negative-array-length`, `allocation-limit-panic`,
  `string-allocation-limit-panic`, `exception-catch-allocation-limit-panic`,
  `runtime-list-allocation-limit-panic`, `runtime-map-allocation-limit-panic`,
  `runtime-path-allocation-limit-panic`,
  `runtime-read-string-allocation-limit-panic`,
  `runtime-read-all-bytes-allocation-limit-panic`,
  `runtime-directory-stream-result-allocation-limit-panic`,
  `runtime-directory-stream-child-allocation-limit-panic`,
  `runtime-process-run-output-allocation-limit-panic`,
  `array-copy-allocation-limit-panic`, and `system-exit`
- `.github/scripts/sanitizer-suite.sh` recompiles generated C with Address/Undefined sanitizer
  flags and leak detection when the host compiler supports them; it enables
  `JAVAN_GC_STRESS` metadata validation plus `JAVAN_GC_SAFEPOINT_INTERVAL` collection
  stress and covers normal completion, static roots, root frames, generated object graph
  collection, object-registry GC, protected object returns, operand/call expression
  temporary roots, large arrays, primitive-array GC, native-library byte-array FFI
  ownership, runtime list/map copy and view helper rooting under heap pressure,
  runtime UTF-8 string helper source rooting for substring, replace, char-array
  construction, copy, concat, StringBuilder append, path helper, and export-copy
  allocation paths, `FILE*`/`DIR*` panic-time cleanup, directory-stream source path
  rooting, directory-stream result-allocation denial, directory-entry child allocation denial,
  process-run output capture allocation denial,
  panic-expression temporary rooting, array-copy source rooting,
  deterministic allocation-denial probes for string/list/map/path/read-file/directory-stream/process/array-copy/catch paths,
  repeated native-library export/free ownership stress, sanitizer failure-signature rejection,
  `realloc` heap-limit growth accounting, hostile receiver/array-load/runtime-string/
  nested-container/catch/live-root panic allocation stress, allocator-path GC retry,
  deterministic panic cleanup, and `system-exit` cleanup
- CI and release verification run sanitizer checks in required mode

Open gates:

- remaining operand/eval-order validation across supported IR shapes beyond the current
  hostile receiver, array-load, runtime-string, nested-container, caught-exception, and
  live-root panic probes
- full Java heap mark/sweep beyond generated objects/arrays
- hostile-point GC collection stress across every supported allocation shape
- string allocation ownership and collection
- exception semantics beyond direct same-method platform catch routing
- sanitizer/leak CI on Windows and release footprint jobs
- FFI ownership/free-path sanitizer tests for strings
- thread roots once threads exist

## 0.29 Optimizer Foundation

Done:

- deterministic post-reachability `DeduplicationPlanner`
- reports for runtime module families, duplicate string literals, array helper families, and bounds helper families

Planned release-mode passes:

- smart dead-code elimination for classes, methods, fields, constructors, runtime modules, intrinsics, string constants, vtables, and dispatch tables
- safe redundant-check elimination using CFG facts
- method specialization when callers provide stronger facts than the normal method contract
- devirtualization for closed-world concrete targets
- escape analysis and stack allocation for non-escaping objects
- arena allocation for request-scoped temporary objects
- generic specialization where monomorphization is provably bounded
- boxing elimination for non-escaping wrapper values
- string optimizations: literal deduplication, concat lowering, StringBuilder elimination, ASCII/UTF-8 fast paths, and constant folding
- intrinsic substitution for JDK hotspots such as `Objects.requireNonNull`, `Math.abs/min/max`, `System.arraycopy`, `Arrays.copyOf`, `Integer.toString`, `Long.toString`, `System.nanoTime`, `System.currentTimeMillis`, `SecureRandom.nextBytes`, and `UUID.randomUUID`

Safety rules:

- never remove checks with visible side effects
- never remove side-effecting message suppliers or logging validations
- keep public/exported method guards unless specialization or inlining proves the caller facts
- invalidate mutable object-field facts after unknown calls
- treat volatile, synchronized, and thread-visible state conservatively
- debug builds keep most checks
- release builds may remove only proven redundant checks

Reports:

- `.javan/reports/optimizations.json`
- `.javan/reports/optimizations.md`

## 0.295 CLI UX Consolidation

Status: implemented CLI/reporting slice with open build-plugin and artifact-layout
acceptance criteria.

Goal:

- keep the CLI easy enough that users do not need to understand internal artifact kinds,
  Maven properties, or report file locations before building something useful

Implemented slice:

- keep `javan build` as the default native app path
- add `javan build --jar` as the friendly JVM jar path
- add `javan build --library` as the friendly native library path
- keep internal output formats explicit: app executable, JVM jar, static library, shared
  library, and combined library package
- keep `--kind app|jar|staticlib|sharedlib` as a stable advanced interface or compatibility
  alias
- add `--format static|shared|both`
- keep app runtime arguments behind `--`, for example `javan run . -- Alice 42`
- detect class directories and jars as already-built inputs without a special reuse flag
- add one bounded `javan report` reader over existing `.javan/reports` files; feature
  tracks add diagnostics and sections to that model, not new public report/check commands

Current gates:

- one calm report command/report output: `javan report` reads and summarizes existing
  report families without inventing missing diagnostics
- simpler build UX: app builds default to native executable output, `--jar` keeps JVM jar
  output, and `--library` builds native library packages; advanced `--kind` values remain
  compatibility aliases
- generated report files remain stable even when CLI presentation changes
- "easy" commands may infer; reports must say exactly what was inferred

Open acceptance criteria:

- make app and library builds produce all cheap useful artifacts into predictable
  subfolders; users should not have to choose internal artifact kinds when the cost is low
- make build plugins expose normal plugin configuration instead of requiring users to pass
  Maven `-D...` properties for ordinary app arguments

Rules:

- no hidden behavior changes based only on filename
- every alias maps to one explicit internal build plan
- generated report files remain stable even when CLI presentation changes
- "easy" commands may infer; reports must say exactly what was inferred
- JSON and Markdown reports stay on disk for humans, CI, IDEs, and build plugins

## 0.296 Runtime Module Selection And Footprint

Status: implemented reporting and disabled-module enforcement slice. See
[runtime-feature-selection.md](runtime-feature-selection.md).

Goal:

- let users reduce binary size, deployment weight, and diagnostic overhead while keeping
  the default build automatic

User model:

- default `javan build` links only reachable runtime modules
- advanced users configure runtime posture in `javan.toml`
- profiles describe intent; reports describe exact linked reality
- disabled features are hard build contracts, not suggestions
- current native builds write `runtime-footprint.json` and `.md`
- `--target` is a host-target assertion until cross-linking is implemented
- current checks write `runtime-features.json` and `.md`
- disabled reachable runtime modules fail before native codegen
- disabled unused runtime modules are reported without failing

Planned configuration:

```toml
[build.runtime]
containment = "system"
optimize = "size"
debug = false
profiling = false
disabled = ["thread-profiling", "reflection-metadata"]
```

Trade-offs:

| Choice | Short trade-off |
| --- | --- |
| system-linked | smaller; requires compatible OS libraries |
| self-contained | easier to distribute; larger and platform-dependent |
| `runtime.optimize = "size"` | smaller; may skip speed helpers and metadata |
| `runtime.optimize = "speed"` | faster hot paths; larger binary |
| debug off | smaller; less source/native mapping |
| profiling off | smaller; no live profiling hooks |
| disabled feature | smallest when unused; build fails if reachable code needs it |

Acceptance criteria:

- host-native CI covers `linux-x64`, `linux-aarch64`, `macos-aarch64`, and `macos-x64`
- runtime footprint reports list host target, requested target, actual target, artifact
  bytes, footprint statuses, and OS/ARCH coverage rows
- mismatched `--target` fails before native codegen until cross-linking is implemented
- runtime reports list requested containment, actual linkage, included modules, omitted
  modules, disabled modules, debug/profiling posture, and sanitizer posture
- disabled unreachable features are omitted and reported
- disabled reachable features fail before native codegen with a source-focused diagnostic
- self-contained builds either succeed or fail with a clear platform-specific reason
- `runtime.optimize` choices produce deterministic reports explaining binary-size trade-offs

## 0.297 Windows Runtime And Linker Port

Status: planned. Windows targets are tracked in runtime-footprint reports but are not
implemented native targets yet.

Implementation order:

1. centralize platform artifact naming for executables, shared libraries, static libraries,
   and import libraries
2. introduce native linker strategies for POSIX GCC/Clang, MinGW, and later MSVC
3. fail clearly on Windows when no supported linker strategy exists
4. gate generated runtime includes and APIs behind `_WIN32`
5. port time, cwd, environment, filesystem, process execution, and directory iteration
6. update package verification for `javan.exe`
7. add Windows CI as non-release verification
8. promote Windows x64 to release-gated only after app, jar, staticlib, sharedlib, ABI
   tests, resources, and reports pass

Open technical risks:

- process execution currently depends on POSIX `fork`, `execvp`, and `waitpid`
- static library generation currently assumes `ar` and `.a`
- shared library generation currently assumes POSIX/macOS flags
- full self-contained Windows packaging needs explicit CRT policy

## 0.3 Go-Style Dependencies

Add `javan.mod` and `javan.lock`.

Initial shape:

```text
module com.acme.app
java 25

require org.nanonative:nano 2025.11.3131219
require berlin.yuna:type-map 2026.05.1481042
```

Behavior:

- resolve Maven coordinates without requiring Maven or Gradle
- respect the local Maven cache (`~/.m2/repository`) before network fetches
- resolve from Maven Central only when enabled by policy
- resolve from configured Maven/Ivy repositories and authenticated mirrors
- resolve from GitHub Packages, GitHub releases, or Git source dependencies when declared
- cache shared downloaded artifacts under `~/.javan/cache`
- keep production, test, and tool dependencies separate
- keep project dependency decisions deterministic through `javan.lock`
- write deterministic lock files with checksums and source provenance
- record dependency licenses and usage in reports
- report unused declared dependencies and unreachable classpath dependencies
- keep Maven/Gradle import/export commands for existing projects
- redact credentials from lock files and reports

Detailed dependency and license reporting rules live in
[dependency-and-license-reports.md](dependency-and-license-reports.md).

## 0.31 CLI Profiles, Test Command, And Target Surface

Implemented CLI surface:

- `javan test`
- `javan build --release`
- `javan build --target linux-aarch64`
- `javan build --profile core`
- `javan build --profile service`
- `javan build --profile library`
- `javan build --profile strict`
- Maven, Gradle, and plain Java autodetection stays automatic
- main class, classpath, dependency indexes, target, and binary names remain inferred by default

Current gates:

- profile, release, and target flags are accepted and reflected in reports without
  pretending profile-specific lowering or cross-target release gating is complete
- Maven, Gradle, plain Java, class-directory, and jar inputs stay auto-detected by default

Open acceptance criteria:

- profile-specific verifier policy
- release-mode optimization changes
- cross-target linker selection and release gating

## 0.32 Human-Readable Exceptions

Status: planned. See [human-readable-exceptions.md](human-readable-exceptions.md).

Goal:

- make build-time and runtime failures explainable from Java source, without exposing
  generated/native stack details by default

Planned diagnostics:

- stable error code
- short problem summary
- Java class, method, source file, and line when available
- highlighted source line when source is available
- plain-language reason
- concrete fix suggestion
- reachable call path
- generated/internal names hidden by default
- generated/native frames shown only with `--debug-native`
- optimized and specialized method names mapped back to the original Java source through a debug map

Planned reports:

- `.javan/reports/exceptions.json`
- `.javan/reports/exceptions.md`
- `.javan/reports/debug-map.json`

## 0.33 Compile-Time Runtime-Risk Warnings

Status: planned. See [runtime-risk-warnings.md](runtime-risk-warnings.md).

Goal:

- warn during `javan check` and `javan build` when reachable code may fail at runtime
- never claim a path is safe unless the IR analysis proves the required facts

Initial planned checks:

- possible null dereference
- unsafe array index
- unsafe `String.charAt` and `String.substring`
- `List.get(0)` without non-empty proof
- `Optional.get` without `isPresent` proof
- `Iterator.next` without `hasNext` proof
- division or modulo by possible zero
- unsafe casts without `instanceof` proof
- uncaught panic-style exception paths
- redundant checks that can later feed release optimization

Planned reports:

- `.javan/reports/safety-warnings.json`
- `.javan/reports/safety-warnings.md`

Unified report reader:

- `.javan/reports/report.json`
- `.javan/reports/report.md`

CLI policy:

- normal `javan check` and `javan build` run the enabled diagnostics
- `javan report` reads and summarizes the generated report model
- strictness, warnings-as-errors, and feature toggles belong in project/global settings
  first; public flags are added only when a workflow truly needs them

## 0.34 Full Concurrency Runtime And Thread Analysis

Status: planned. See [concurrency-runtime.md](concurrency-runtime.md).

Existing spike/lab input:

- `/Users/yuna/projects/javan-project/javan-virtual-threads-native-spike`

The spike is useful migration material only. It does not change production status until
slices are migrated into `javan`, tested, and accepted through the normal gates.

Goal:

- support Java virtual threads as a first-class native runtime feature
- analyze all reachable thread usage for correctness, blocking behavior, scalability, and
  pointless overhead

Scope:

- virtual threads
- platform threads
- `ExecutorService`
- virtual-thread executors
- blocking calls
- CPU-bound tasks
- tiny tasks
- `synchronized` and locks
- `ThreadLocal`
- sleep, join, interrupt
- park and unpark

Runtime requirements:

- virtual scheduler
- carrier thread pool
- virtual `Thread` object model
- `Thread.startVirtualThread(...)`
- `Thread.ofVirtual()`
- `Executors.newVirtualThreadPerTaskExecutor()`
- sleep, join, interrupt, park, and unpark
- `ThreadLocal`
- uncaught exception handling
- readable virtual-thread stack traces
- scheduler-aware blocking I/O
- pinning and blocking diagnostics

Compiler analysis:

For every reachable thread or task root, build a `ThreadTaskSummary`:

- root method
- reachable call graph
- estimated instruction count
- allocation count
- loop presence
- blocking calls
- I/O calls
- sleep, park, and join usage
- synchronized and lock usage
- `ThreadLocal` usage
- native or unknown calls
- possible carrier pinning
- CPU-bound score
- tiny-task score

Task classification:

- `IO_BOUND`
- `BLOCKING_WAIT`
- `CPU_BOUND`
- `TINY_CPU_TASK`
- `MIXED`
- `UNKNOWN`
- `PINNING_RISK`

Diagnostics:

| Diagnostic | Level | Meaning |
| --- | --- | --- |
| `JAVAN-THREAD-SMALL` | info | Task is very small, CPU-only, and has no blocking or I/O. Suggest inline execution or batching. |
| `JAVAN-THREAD-CPU` | warning | Virtual threads do not improve CPU throughput. Suggest a bounded platform-thread executor near CPU core count. |
| `JAVAN-THREAD-BLOCKING` | info/warning | Platform thread performs blocking I/O; suggest virtual threads when concurrency is high. |
| `JAVAN-THREAD-UNKNOWN-BLOCK` | warning | Scheduler-safe blocking behavior cannot be proven. |
| `JAVAN-THREAD-PIN` | warning | `synchronized`, native, or unknown blocking may pin a carrier. |
| `JAVAN-THREAD-FLOOD` | warning | Many short-lived threads or tasks detected. |
| `JAVAN-THREAD-LOCAL` | info | Report memory and lifecycle implications for many virtual threads. |

Rules:

- definite correctness issue is an error
- likely scalability or performance issue is a warning
- possibly pointless usage is info
- be conservative
- do not fail builds for style or performance opinions unless `--warnings-as-errors` is enabled
- never claim a task is safe unless proven

Runtime profiling:

Thread profiling is a report mode, not a separate public run command by default. The
normal `javan run`/`javan build` pipeline should emit thread diagnostics when the project
settings enable the thread analysis/profiling track.

Collect:

- virtual threads created
- platform threads created
- average task duration
- blocked time
- CPU time
- pinning events
- scheduler queue delay
- carrier utilization
- `ThreadLocal` count
- blocking call sites

Reports:

- `.javan/reports/threads.json`
- `.javan/reports/threads.md`
- `.javan/reports/virtual-threads.json`
- `.javan/reports/virtual-threads.md`

CLI policy:

- normal `javan check` and `javan build` feed thread diagnostics into the unified report
- normal `javan report` shows the thread sections when present
- do not add thread-specific public flags as first-choice UX unless settings and report
  defaults prove insufficient

Acceptance:

- full virtual-thread APIs are supported
- thread diagnostics are emitted only for reachable code
- blocking, platform-thread, and virtual-thread usage is analyzed
- reports are stable JSON plus readable Markdown
- diagnostics include where, why, fix, and reachable path
- tests cover virtual threads, platform threads, tiny tasks, CPU-bound tasks, blocking
  tasks, pinning risk, `ThreadLocal`, join, sleep, interrupt, and profiling

## 0.35 JDK Coverage Accounting

Status: in progress.

Goal:

- make every supported JDK deterministic enough that inventory count, native support,
  deliberate rejection, and unknown leftovers are all visible

The compatibility inventory should produce per-JDK statistics:

| Statistic | Meaning |
| --- | --- |
| classes inventoried | JDK classes discovered in the runtime image |
| methods inventoried | methods plus descriptors and flags |
| fields inventoried | fields plus descriptors and flags |
| constructors inventoried | constructors plus descriptors and flags |
| bytecode variants observed | opcodes, constant-pool tags, attributes, bootstrap shapes, and invokedynamic shapes |
| supported variants | variants with native lowering, intrinsic, or substitution coverage |
| rejected variants | variants that fail clearly with a stable diagnostic |
| unknown variants | variants neither supported nor rejected |

Release rule:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0 for a release-gated JDK
```

This is separate from "32k classes inventoried". A class being inventoried only means
`javan` can see it. It does not mean every method in that class can be lowered to native
code.

## 0.36 IDE Diagnostics Through Javac Wrapper

Status: planned.

Goal:

- let IDEs and build tools see `javan check` warnings as normal compiler-style warnings
  while still delegating real Java compilation to the original `javac`

Planned behavior:

1. `javan javac` delegates to the selected original JDK `javac`.
2. If `javac` fails, preserve its exact exit code and diagnostics.
3. If `javac` succeeds and native-profile checking is enabled, run `javan check` on the
   emitted class files.
4. Emit diagnostics in javac-style source form:

```text
src/main/java/com/acme/Main.java:42: warning: [JAVAN-THREAD-CPU] virtual thread task is CPU-bound
```

5. Also write stable report JSON for IDE plugins and language servers.

Rules:

- do not replace javac
- do not emit fake Java compiler errors for unsupported native-only restrictions unless
  the user enabled the javan native profile
- generated/internal names must be hidden by default
- source mapping must use the same diagnostic model as human-readable exceptions

## 0.37 Go And Rust Translator / Binary Experiments

Status: standalone lab track.

Goal:

- explore whether `javan` IR can feed Go/Rust source generation or runtime/library
  integration without bloating the core compiler

Candidate directions:

- Java `.class` -> javan IR -> generated Rust crate -> native binary/library
- Java `.class` -> javan IR -> generated Go module -> native binary/library
- Java exports -> C ABI -> generated Rust/Go wrappers
- Rust/Go libraries -> C ABI -> Java-native bindings

Boundaries:

- this must live outside the core repo until the IR/backend contract is stable
- it must not weaken native Java safety rules
- generated code must be deterministic
- every supported mapping needs JVM-equivalence tests or explicit rejection tests
- if the experiment becomes real, it should integrate through a backend interface, not by
  smuggling language-specific hacks into bytecode lowering

## 0.38 Self-Hosting Bootstrap

Status: implemented bootstrap slice; production gate still in progress.

Goal:

- make `javan` capable of building the `javan` CLI itself through Javan's own
  bytecode -> IR -> C/native path

Current state:

- `javan` already compiles small Java applications and libraries through its own bytecode
  to IR to C/native path
- `javan build target/classes --main javan.Main --jar` builds Javan's JVM jar from
  already compiled classes
- `javan build target/classes --main javan.Main` now builds a self-hosted native Javan
  bootstrap binary on the current host
- the production self-host gate covers jar/report acceptance under the rebuilt binary
  locally and still needs broader cross-OS/architecture verification

Target flow:

```text
javan source
-> javac class files
-> javan IR
-> C backend
-> native linker
-> javan executable
```

LLVM and Cranelift remain future backend experiments after the C backend is deterministic
enough for self-hosting and release gates.

Bootstrap stages:

1. make `javan check` report every unsupported reachable feature in the `javan` codebase
2. either implement support or deliberately reject/replace each unsupported pattern
3. compile `javan` with `javan` on the host platform
4. compare behavior against the JVM jar
5. add cross-platform self-host smoke tests
6. make Javan-built binaries the only release artifacts

Production acceptance:

- normal documented build commands produce a working Javan executable
  native-image
- the Javan-built executable can rebuild Javan from Javan class files
- `dist/javan --version`, `doctor`, `check`, `build`, `run`, `compat`, `report`, and
  `toolchain` smoke tests pass
- generated executable can build app, jar, resource, and native-library acceptance
  test projects
- unsupported self-host gaps are tracked as deterministic diagnostics, not discovered by
  linker crashes

## 0.4 Bundled Toolchains

Status: deferred behind the binary-first distribution. See
[binary-first-distribution.md](binary-first-distribution.md) and
[toolchain-distribution-roadmap.md](toolchain-distribution-roadmap.md).

Keep `javan doctor` and read-only toolchain inspection, but do not make SDK wrapping the
first-release path.

The distribution should first be a standalone binary that consumes normal Java build
outputs. Maven, Gradle, Homebrew, and IDE support should be thin integrations around that
binary and its reports.

Later, if needed, the distribution may manage:

- a JDK for `javac`
- a C compiler toolchain per platform where licensing permits
- globally installed dependencies and toolchains under the user's home `.javan` directory
- global settings for default JDKs, targets, profiles, caches, and download policy
- Maven and Gradle plugins for build integration without hard manual wiring
- Homebrew packaging for macOS installation
- optional JetBrains plugin support if LSP-style diagnostics are not enough

See [cross-platform-verification.md](cross-platform-verification.md) for Docker, JDK matrix,
cache, and acceptance-gate strategy.

## 0.5 JVM Compatibility Expansion

Records, enums, simple allocation, arrays, simple virtual dispatch, exceptions, minimal
collections, JSON without reflection, file IO, and HTTP runtime.

Reflection strategy:

- arbitrary reflection, runtime scanning, dynamic class loading, proxies, `setAccessible`,
  and custom class loaders remain rejected when reachable
- limited reflection can be supported later only when it is closed-world and explicit:
  class literals, constant-string `Class.forName`, class/method/constructor metadata for
  known classes, and generated invocation tables for statically known targets
- reflection metadata must be linked only when reachable or explicitly requested
- every reflected target must appear in reports with size impact and source provenance
- dynamic or data-dependent reflection that cannot be resolved at build time must fail
  with a stable diagnostic rather than silently generating a broken binary

## 0.6 IDE Feedback

Generate machine-readable diagnostics and an LSP-compatible profile so IDEs can flag APIs
that cannot enter the static native subset.

## Flagship: Javan Studio With JavanUI

Status: planned flagship track. See [javan-studio-roadmap.md](javan-studio-roadmap.md).

Javan Studio is a professional visual app builder built with JavanUI itself. It creates
Java-native apps, Railix flows, web frontend/backend projects, desktop apps, and native
libraries from a versioned `AppModel`.

Core requirements:

- Javan Studio is built with JavanUI.
- JavanUI must be production-grade enough to host Studio.
- A versioned `AppModel` is the structural source of truth.
- Generated output is normal Java, Railix code, DTO/schema code, and target-specific
  project files.
- Business logic remains editable Java.
- No reflection, runtime scanning, or hidden editor-only logic.
- Studio visualizes javan reports: reachability, build metrics, safety warnings, readable
  exceptions, dependency usage, optimizations, native readiness, and accessibility.
- Studio includes workspace shell, project tree, command palette, diagnostics panel, report
  viewer, flow editor, UI editor, inspector, preview surface, and build center.
- Flow editor supports `actor -> validate -> map -> branch -> side effect -> terminal result`.
- UI editor supports responsive layouts, accessibility checks, state binding, action
  binding, and resource management.
- Export targets include native desktop app, web frontend/backend separation, backend
  service, and native shared/static library with bindings later.
- Studio must be buildable by `javan` and display its own `javan` reports.

Execution order:

1. JavanUI production foundation.
2. Studio shell.
3. Versioned `AppModel`.
4. Railix flow editor.
5. UI editor.
6. UI-to-flow binding.
7. Multi-target export.
8. Full report/build center.
9. Self-build and dogfooding.
10. Professional editor features.

## UI Separation Rule

Keep JavanUI and Javan Studio out of the core compiler dependency graph. They are flagship
tracks, but they should live as separate workspaces and integrate through normal Java APIs,
generated source, stable reports, and explicit build contracts.
