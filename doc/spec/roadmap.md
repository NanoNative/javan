# javan Roadmap

## Scope

- status, counts, and honest target coverage: [`../status/roadmap-progress.md`](../status/roadmap-progress.md)
- cross-platform verification policy: [cross-platform-verification.md](cross-platform-verification.md)
- examples and acceptance projects: [examples-and-test-projects.md](examples-and-test-projects.md)
- sibling-product tracks such as Studio, UI, plugins, Homebrew, and IDE integrations stay
  outside the core compiler repo under `/Users/yuna/projects/javan-project/`

## 0.1 Native Hello

Real CLI, project detection, build invocation, class scanning, main detection, reachable
verification, C generation, native link, and integration tests.

## 0.2 Static Primitives

Implemented details:

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

Implemented details:

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
- non-ASCII string constants are rejected by `javan check` and native lowering when
  used with UTF-16-sensitive operations such as `length`, `charAt`, `substring`,
  `indexOf`, and `lastIndexOf`
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

- non-ASCII/full UTF-16 string runtime semantics beyond the current clear rejection
  for UTF-16-sensitive operations
- general try/catch/finally exception-handler lowering
- richer class initialization ordering across complex dependency graphs
- full `java.lang.Enum` object identity and initialization semantics beyond the current constant-as-string model

## 0.27 Deterministic Compatibility

Implemented details:

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

Implemented details:

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
- versioned C ABI header macros (`JAVAN_ABI_VERSION = 2`)
- ABI v1-compatible direct export symbols
- ABI v2 C `javan_try_*` result wrappers with owned `JavanResult` diagnostics
- Rust `try_javan_export_*` wrappers returning `Result<T, JavanError>`
- Go `TryJavanExport*` wrappers returning `(T, error)`
- Python `try_javan_export_*` wrappers returning Python-owned values or raising `JavanError`
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
- native-library sanitizer smoke counter-checks repeated C ABI `String` and `byte[]`
  export/free paths with final GC, heap metadata validation, zero live heap, zero open
  root frames, peak-live-byte ceiling, and minimum total/GC/collected counters
- ABI v1 headers and bindings expose borrowed structured `javan_last_error_*` fields
  beside the compatibility `javan_last_error()` message
- ABI v2 C result wrappers expose owned diagnostics through `JavanResult`, survive
  `javan_clear_error()`, and free through `javan_result_free`
- generated Rust, Go, and Python result wrappers copy diagnostics before freeing
  `JavanResult` and copy successful `String`/`byte[]` values before freeing
  Javan-owned native memory
- native-library probes cover null `String` input, empty `byte[]` input, negative
  `byte[]` length rejection, structured last-error fields, last-error clear semantics,
  try-wrapper success, try-wrapper error results, and result free semantics

Open acceptance criteria:

- annotation-driven exports
- full Java exception-to-result mapping beyond the current caught Javan runtime
  panic to borrowed last-error and C `JavanResult` ABI surfaces
- per-export thread/reentrancy reports
- richer object/record ABI models
- cross-target library linking
- Windows import-library details
- binding package manifests for Cargo, Go modules, and Python wheels
- direct LLVM/Cranelift backends after the C backend remains deterministic

## 0.285 JVM Jar And Resources

Implemented details:

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

Status: partial. The single-thread managed-heap slice is real, leak-tested, and reportable.
Full heap coverage, thread roots, and broader Java object semantics remain open. Deep
design and exhaustive test inventory live in
[memory-runtime-correctness.md](memory-runtime-correctness.md).

Implemented slice:

- allocation accounting, root tracking, safe points, direct-return protection, and GC retry
- collectibility for generated objects, arrays, boxed wrappers, runtime strings, and current
  runtime containers
- rooted native-library `String`/`byte[]` ABI paths and explicit ownership/free rules
- required sanitizer, soak, and proof reports for app and library paths
- unified report exposure for sanitizer-proof and live-heap counters

Open gates:

- operand/eval-order validation beyond the current hostile-root stress slices
- full Java heap mark/sweep beyond current generated/runtime allocation shapes
- hostile-point GC collection stress across every supported allocation shape
- full Java `String` object model and UTF-16 ownership
- exception semantics beyond direct same-method platform catch routing
- sanitizer/leak CI on Windows and release footprint jobs
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

Status: Partial. The CLI/reporting slice is implemented; build-plugin and
artifact-layout acceptance criteria remain open.

Goal:

- keep the CLI easy enough that users do not need to understand internal artifact kinds,
  Maven properties, or report file locations before building something useful

Implemented details:

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
- refresh unified `.javan/reports/report.md` and `.javan/reports/report.json`
  automatically from `check`, `build`, and `compat`

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

## 0.298 Linux Libc-Free Syscall Runtime

Status: planned external integration track.

Goal:

- provide an optional Linux runtime footprint that uses direct kernel syscalls instead
  of libc for small static/native programs

Rules:

- not the default runtime
- Linux only; macOS and Windows use platform APIs
- no silent fallback to libc when syscall mode is requested
- unsupported modules fail before code generation
- runtime reports state `syscall` versus `libc` posture

Initial scope:

- process exit
- stdout/stderr writes
- monotonic/realtime clock
- cwd and simple file reads/writes
- simple memory mapping only if the allocator needs it

Deferred:

- DNS
- certificates/TLS/HTTPS
- locale/timezone
- full virtual-thread scheduler and blocking I/O integration
- complex process spawning

Acceptance criteria:

- Linux syscall artifacts report no libc dependency
- syscall mode runs native showcase features that only use supported modules
- unsupported module selection fails with a source-focused diagnostic
- sanitizer/leak gates pass for all syscall-supported allocation paths

## 0.3 Go-Style Dependencies

Add `javan.mod` and `javan.lock`.

Initial shape:

```text
module com.acme.app
java 25

require main libs/runtime.jar
require main com.acme:math:1.2.3
require test libs/test-support.jar
require tool tools/codegen.jar
```

Behavior:

- current implementation resolves local jar/classes paths and direct coordinates from local
  Maven repositories
- local `main` dependencies enter plain `javac` and native app reachability
- local `test` and `tool` dependencies are locked but do not enter native app reachability
- missing local dependencies fail clearly
- resolve direct Maven coordinates without requiring Maven or Gradle
- respect configured local Maven repositories (`-Djavan.maven.localRepository`,
  `-Dmaven.repo.local`, then `~/.m2/repository`) before network fetches
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

Status: Partial. See [human-readable-exceptions.md](human-readable-exceptions.md).

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

Implemented reports:

- `.javan/reports/exceptions.json`
- `.javan/reports/exceptions.md`
- `.javan/reports/debug-map.json`

Open acceptance criteria:

- reachable call path rendering
- exact expression/range highlighting beyond whole-line source snippets
- expression-level runtime helper source mapping for null, bounds, string, cast, and arithmetic failures
- `--debug-native` native/generated frame expansion
- optimized/specialized method mapping after those optimizations are enabled

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

Current shared diagnostic reports:

- `.javan/reports/diagnostics.json` for the shared build/check diagnostic model
- `.javan/reports/diagnostics.md` for readable diagnostic details

Planned safety reports:

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

Status: in progress. Detailed requirements live in
[concurrency-runtime.md](concurrency-runtime.md). Existing research input lives in
`/Users/yuna/projects/javan-project/javan-virtual-threads-native-spike`.

Goal:

- support platform threads and virtual threads as first-class native runtime features
- analyze reachable thread usage for correctness, blocking behavior, scalability, and
  pointless overhead

Must ship:

- platform-thread lifecycle: `start`, `join`, sleep interruption, roots, cleanup
- virtual-thread runtime: scheduler, carriers, broader `Thread.ofVirtual` object flows
- thread diagnostics: blocking, CPU-bound, tiny-task, pinning, `ThreadLocal`, flood risk
- report outputs: `.javan/reports/threads.*` and `.javan/reports/virtual-threads.*`

Acceptance:

- claimed virtual-thread slice supported with unsupported broader runtime shapes rejected clearly
- diagnostics emitted only for reachable code
- reports stay stable in JSON and Markdown
- tests cover platform threads, virtual threads, blocking, pinning, join/sleep/interrupt,
  and profiling

## 0.35 JDK Coverage Accounting

Status: in progress.

Goal:

- make every supported JDK deterministic enough that inventory count, native support,
  deliberate rejection, and unknown leftovers are all visible

Per release-gated JDK, the compatibility inventory must expose:

- inventory counts for classes, methods, fields, constructors, and observed bytecode variants
- native coverage counts for supported, rejected, and unknown variants
- stable JSON/Markdown output that the status page can summarize without reinterpreting it

Release rule:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0 for a release-gated JDK
```

Inventory is not support. A class being inventoried only means `javan` can see it.
Native coverage is only claimable when the unknown bucket is zero for the release-gated slice.

## 0.36 IDE Diagnostics Through Javac Wrapper

Status: planned.

Goal:

- let IDEs and build tools surface `javan check` diagnostics in compiler-style source form
  while real Java compilation still runs through the original `javac`

Core contract:

- `javan javac` delegates to the selected original `javac`
- javac failures pass through unchanged
- successful compilation may run native-profile checks on emitted classes
- diagnostics map back to Java source and hide generated/native frames by default
- stable report JSON remains the machine-readable source of truth

IDE plugins and build-tool integrations stay outside the core compiler dependency graph.

## 0.37 Go And Rust Translator / Binary Experiments

Status: external research track.

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

Target flow:

```text
javan source
-> javac class files
-> javan IR
-> C backend
-> native linker
-> javan executable
```

Core gate:

- the native `javan` binary must rebuild Javan from compiled Javan classes and then pass
  the same smoke/report/build acceptance expected from the JVM-hosted path

Notes:

- LLVM and Cranelift remain future backend experiments after the C backend is deterministic
  enough for self-hosting and release gates
- remote cross-OS/architecture proof stays tracked in the status page and release docs, not here

Production acceptance:

- normal documented build commands produce a working Javan executable
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

## Flagship: Javan Studio With JavanUI

Status: external flagship track.

The core repo only owns the integration contract:

- Studio consumes stable `javan` report formats
- generated output must remain normal Java and explicit project files
- no Studio/UI implementation may add hidden runtime coupling to the core compiler

Detailed product planning belongs in the sibling `javan-studio` and `javan-ui`
workspaces under `/Users/yuna/projects/javan-project/`.

Keep JavanUI and Javan Studio out of the core compiler dependency graph. They integrate
through normal Java APIs, generated source, stable reports, and explicit build contracts.
