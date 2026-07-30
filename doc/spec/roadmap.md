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
- object-array, int-array, and generated `Cloneable` object `clone()` lowering
- object reference compare branches and dense/sparse integer switch bytecode

Current gates:

- support-matrix scenarios for records, fields, arrays, enums, dispatch, string
  intrinsics, concat, static fields, switch bytecode, and scoped catch handling
- native-profile typed-catch scenarios for first-handler miss, runtime superclass,
  IO superclass, util runtime superclass, and Error not matching Exception
- unsupported reachable object, exception, dispatch, and string bytecode must reject
  before native code generation

Current limitation: generic generated records do not yet have universal native
`equals`/`hashCode` value semantics. Separately created but field-equal records used as
collection keys may therefore fail to match. Compiler-owned `MethodRef` lookup compares
the record fields explicitly; application-level support remains open.

Open acceptance criteria:

- generated-record `equals`/`hashCode` value semantics, including use as collection
  keys; until then, compiler internals must compare record fields explicitly and reachable
  application shapes that require missing record value semantics must fail before C generation
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
- full Maven verification refreshes the tracked compatibility status from already compiled
  classes through the same `javan compat` entrypoint
- Eclipse Temurin 25.0.1 on Linux x64 is the pinned reference for the tracked JDK inventory,
  with a dedicated strict CI lifecycle gate

Current gates:

- active-JDK inventory and bytecode-pattern scans write deterministic reports
- unknown opcodes remain fatal instead of being ignored or treated as best effort
- support matrix records named pass/scoped/target scenarios without claiming full JDK API
  support
- stale support-matrix Markdown or JSON is regenerated automatically on every full JDK 25
  Maven verification and fails once with an exact rerun instruction
- the tracked JDK page is also regenerated and gated on the pinned reference JDK; other JDK
  25 vendors and patches generate their active report without modifying the reference page
- CI provisions and requires the exact reference vendor, version, OS, and architecture
  before running the same `mvn verify` lifecycle; there is no separate generation,
  comparison, or copy command

Open acceptance criteria:

- multiple configured JDK homes in one run
- direct `javac --release` probe compilation matrix
- committed test-project baselines per JDK release
- API inventory diffs between JDK releases
- bootstrap-method shape policy gates beyond reporting

### Fail-Fast And Compiler-Owned Repair Policy

Javan should keep ordinary Java source ordinary. When the compiler can preserve Java
semantics, the default is an automatic lowering or runtime adaptation rather than a request
for the user to rewrite application code. Such repairs are compiler-owned, always enabled,
and reported. They do not require another feature flag.

Automatic repair is allowed only with a proof for initialization timing, evaluation order,
identity, exceptions, threading, and GC visibility. Application initializers are never run
at compile time. If that proof is unavailable, `check`, `compat`, or `build` must fail before
C generation with a stable diagnostic that names the unsupported shape and the compiler
capability still required. Users should not discover it later as a C compiler error, linker
error, or unexplained runtime panic.

For example, future support for:

```java
static final SecureRandom RANDOM = new SecureRandom();
```

must leave object construction at the JVM-equivalent active class-initialization trigger.
Native startup may prepare an unobservable random runtime module, but it may not allocate a
shared Java object, consume entropy, or eagerly execute `<clinit>`. The required lazy
class-initialization state machine is planned below; the exact `SecureRandom` runtime
substitution remains planned in the
[optimizer roadmap](optimizer-roadmap.md). Until both exist,
unsupported random shapes must reject clearly rather than be silently hoisted.

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

- zero-registration resource discovery across application outputs and resolved dependency
  directories/jars, in deterministic classpath order; no user-authored resource configuration
  file is part of the product contract
- an ordered resource index records normalized names, origins, sizes, checksums, duplicates,
  and classpath shadowing before native code generation. Filesystem symlinks are resolved and
  flattened at build time: the link path remains the logical resource name, target file bytes or
  directory descendants are indexed beneath that name, and no symlink identity reaches the
  packaged artifact. Resolution is cycle-safe and records the logical path, link path, resolved
  source, and checksum; dangling, cyclic, unreadable, or non-file/non-directory targets fail with
  a stable diagnostic. Targets outside declared resource roots are allowed with a provenance
  warning and participate fully in build fingerprints. Distinct logical aliases of the same real
  target are retained independently; cycle detection follows the active traversal chain instead
  of globally deduplicating resolved targets
- virtual directories are synthesized from indexed descendants, and empty directories exist only
  when an explicit directory entry is present and retained
- reachable constant and finite-set lookups through `Class.getResource*`,
  `ClassLoader.getResource*`, and `Module.getResourceAsStream` retain exactly the matching
  resources, including requested `.class` entries, while preserving package-relative, absolute,
  module, first-match `getResource`, all-match `getResources`, and classpath-order rules
- `META-INF/**` participates in the same index and standard lookup rules as every other resource.
  Acceptance covers `Class.getResource*("/META-INF/...")`,
  `ClassLoader.getResource*("META-INF/...")`, and module lookup, plus immutable `Files`/`Path`/
  `File` reads and synthesized directory listings when resource intent is proven.
  `META-INF/services` remains both readable resource data and service-provider metadata.
  `META-INF/MANIFEST.MF` exposes the effective bytes for each origin after documented Javan
  synthesis or merging; signature entries remain opaque readable bytes, without claiming native
  signature verification or that embedding them signs the executable
- reachable directory enumeration retains the matching descendants; a genuinely unbounded name
  retains the finite resolved resource universe with a warning and complete size report instead
  of requesting registration
- generated C tables provide embedded read-only bytes, lookup/enumeration metadata, streams,
  URLs, and a resource lookup ABI for native library consumers
- statically proven resource intent through `Path` and `File` is redirected automatically to a
  read-only resource view. Proof starts with known resource source roots or standard resource
  lookup results and propagates through assignments, casts, supported calls, branches, finite
  `Path.of`, `File`, URL/URI conversion, `resolve`, and normalization flows. The resulting runtime
  value remains tagged as resource-backed; `..` escape is rejected, unproven paths keep normal
  filesystem semantics, and a coincidental matching basename is never enough
- finite mixed resource/host flows preserve both identities and dispatch on the runtime tag; if
  an operation cannot preserve that distinction it fails at build time. Host-file existence never
  chooses the mode: proven resource intent wins for supported reads, while unproven intent remains
  normal filesystem access even when an indexed resource has the same name
- the read-only resource view covers immutable byte/text/stream reads, existence/type/size
  queries, and directory listing/walking through the supported `Files`, `Path`, and `File` APIs
- `Path.toFile` and `File.toPath` preserve the resource tag. Resource URL/URI conversion must use
  an explicit resource identity rather than masquerading as `file:`; canonical/real paths,
  host-filesystem identity, and cross-filesystem path resolution/comparison remain rejected until
  separately specified
- every automatic filesystem-to-resource redirect emits a build warning and report entry with
  the source call site, original path flow, selected resource origin, and JVM/jar semantic
  difference
- a flattened build-input link appears as an ordinary read-only file or directory at runtime:
  `Files.isSymbolicLink` returns false and `Files.readSymbolicLink` throws `NotLinkException`.
  Writes, deletes, moves, link creation, watches, locks, memory mapping, permission
  changes, and other host-filesystem-only operations on a proven resource path fail at build time
  with a stable diagnostic, or deterministically at runtime when only tagged runtime provenance
  is available. Copying an embedded resource out to a writable filesystem path may be supported
  as explicit materialization; mutation of the embedded resource may not
- acceptance proves standard `Class`, `ClassLoader`, and `Module` lookups return indexed bytes and
  classpath precedence from plain class output, packaged jars, and native executables, including
  dependency-jar resources and duplicate names. Resources unchanged by packaging have equivalent
  bytes across corresponding JVM and native forms; artifact-owned metadata such as an effective
  manifest is checked against its documented artifact-specific bytes. Native-only `Path`/`File`
  adaptation, nested listings, and unsupported mutation diagnostics are verified separately and
  always report the deliberate JVM/jar semantic difference; native listing order is a
  deterministic Javan contract, not a claim about unspecified JVM/jar enumeration order
- deterministic resource compression and checksum reporting

## 0.285 Memory And Runtime Correctness

Status: partial. The managed-heap slice and registered platform-worker root frames are real,
leak-tested, and reportable. Full heap coverage, general concurrent mutator/collector
synchronization, and broader Java object semantics remain open. Deep design and exhaustive
test inventory live in
[memory-runtime-correctness.md](memory-runtime-correctness.md).

Implemented slice:

- allocation accounting, root tracking, safe points, caller-owned lock-published generated
  object returns, registered platform-worker frame roots, and GC retry
- collectibility for generated objects, arrays, boxed wrappers, runtime strings, and current
  runtime containers
- rooted native-library `String`/`byte[]` ABI paths and explicit ownership/free rules
- required sanitizer, soak, and proof reports for app and library paths
- unified report exposure for sanitizer-proof and live-heap counters

Open gates:

- operand/eval-order validation beyond the current hostile-root stress slices
- full Java heap mark/sweep beyond current generated/runtime allocation shapes
- hostile-point GC collection stress across every supported allocation shape
- general concurrent local/field/static/runtime-container pointer publication beyond the
  generated paths and supported atomic operations
- caller-owned result-slot conversion for concurrent opaque runtime-helper object returns
- atomic admission for concurrent starts of the same `Thread` object
- full Java `String` object model and UTF-16 ownership
- exception semantics beyond direct same-method platform catch routing
- sanitizer/leak CI on Windows and release footprint jobs

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

### Analysis Roadmap

Javan does not need Graal-scale analysis by default. Analysis must stay deterministic,
automatic, and bounded enough to preserve fast builds. Priority below is acceptance order,
not a requirement to implement every row before Javan is useful.

The current baseline is an entry-point-rooted method worklist with class-hierarchy-style
dispatch plus a separate CFG-aware GC-root liveness pass. Both are real analyses, but they
do not yet provide one shared Java semantic fact model.

| Priority / analysis | Status | Smallest useful Javan scope | Acceptance gate |
| --- | --- | --- | --- |
| P0: canonical bytecode CFG | Planned | Replace the current separate control-flow scans with one shared basic-block graph covering normal successors, branches, switches, stack merges, and supported exception edges. | Invalid targets and incompatible merges fail deterministically; existing supported programs keep native/JVM parity; a stable report lists blocks and edges. |
| P0: class-initialization trigger graph | Planned | Extend direct `<clinit>` edges with superclass-before-subclass ordering, applicable interface rules, lazy JVM triggers, re-entry, and cycles. Keep application initialization at runtime. | Public CLI fixtures prove JVM/native ordering for `new`, `getstatic`, `putstatic`, `invokestatic`, inheritance, interfaces, re-entry, and supported cycles; unsupported cycles fail before C generation. |
| P1: closed-world instantiated-type analysis (RTA) | Planned | Record types created by reachable bytecode, materialized lambdas, substitutions, and runtime factories, then intersect virtual/interface targets with those instantiated types. Unknown or externally supplied receivers remain conservative. | Uninstantiated subclasses disappear from dispatch reports and generated stubs, every constructible receiver remains, and native/JVM parity covers direct, inherited, lambda, runtime-created, and unknown receiver paths. |
| P1: bounded receiver and callable provenance | Planned | Track exact allocation type, checked-cast refinement, small merged type sets, and unknown through locals plus direct arguments/returns. Use the same bounded flow for supported SAM/lambda targets; fields remain unknown initially. | Exact `new`, local, cast, stored-lambda, passed-callback, and returned-callback cases resolve without API-specific guesses; merges and unknown values fall back conservatively with stable diagnostics. |
| P1: local CFG value facts | Planned | Per-block nullness, constants, integer ranges, exact types, and array/string lengths. Do not retain mutable-field facts across unknown calls. Start with reporting and unreachable-branch diagnostics before removing checks. | Facts and merge results are deterministic; only proven unreachable unsupported code is ignored; each removed guard has a proof record; debug behavior and native/JVM semantics remain unchanged. |
| P1: method effect and throw summaries | Planned | Use a small explicit lattice for pure/non-throwing, pure/may-throw, allocates, reads, writes, and unknown. Begin with compiler-owned IR operations and registered intrinsics rather than inference across every JDK method. | Mutation invalidates affected facts, known-pure calls preserve them, and evaluation-order, null, bounds, division, allocation-failure, and exception probes remain JVM-equivalent. |
| P2: intraprocedural escape classification | Planned | Report `NoEscape`, `ArgumentEscape`, and `GlobalEscape` for supported allocations. Do not change allocation strategy in the first slice. | Reports are stable first; stack or arena allocation lands only after identity, monitor, exception, GC-root, sanitizer, and allocation-count gates prove the exact transformed shapes. |

Analysis rules:

- prefer class-hierarchy analysis plus RTA and bounded provenance over a general points-to engine
- centralize recursive superclass/interface assignability before narrowing dispatch
- represent unknown facts explicitly and fall back conservatively
- emit analysis evidence before allowing an optimization to consume it
- measure compile time and peak memory on the showcase and self-host gates for every new
  default analysis; simplify the pass if its build cost is not justified by current behavior
- do not add analysis-specific user flags; supported builds choose the safe path automatically

Dismissed from the current C-backend analysis scope:

- full context-sensitive or object-sensitive points-to and global alias analysis
- general symbolic execution, memory SSA, speculative optimization, and JIT-style tiering
- profile-guided compiler specialization before a measured public workload requires it
- arbitrary reflection, proxy, JNI, or runtime class-loader discovery
- build-time execution of application initializers and Graal-style application image heaps

These items may be reconsidered only when a named public-entrypoint program proves the
bounded analyses insufficient. Detailed optimizer facts, safety rules, and intrinsic status
remain canonical in [optimizer-roadmap.md](optimizer-roadmap.md).

## 0.295 CLI UX Consolidation

Status: Partial. The CLI/reporting slice is implemented; attached execution,
runtime-input, incremental-reuse, build-plugin, and artifact-layout acceptance criteria
remain open.

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
- pass ordinary non-option app arguments after an explicit target, for example
  `javan run . Alice 42`; keep `--` as the unambiguous boundary for option-shaped arguments
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

Open core Javan acceptance criteria:

- make app and library builds produce all cheap useful artifacts into predictable
  subfolders; users should not have to choose internal artifact kinds when the cost is low
- make `javan run` an attached process: stream stdin/stdout/stderr live, preserve stderr as
  stderr, forward interruption and termination, return the child exit code, and impose no default
  application timeout. Bounded capture remains appropriate for build subprocesses
- launch `javan run` from the detected project root so `user.dir` and relative paths match normal
  project execution; a directly launched native binary continues to use its caller's working
  directory
- when no target is supplied, search parents for the nearest unambiguous Javan, Maven, Gradle, or
  plain source project root and report the inference. An explicit target always wins; competing
  roots at the same level fail clearly instead of relying on marker order
- make parsing command-aware: unknown commands fail with typo suggestions, every command supports
  focused `--help`, irrelevant options and trailing arguments fail, conflicting artifact selectors
  fail, and tokens after the app-argument `--` boundary are never interpreted by Javan
- for `run`, interpret Javan options and generated-launcher inputs only until the first ordinary
  app token after the optional target; that token implicitly starts `main(String[])` arguments and
  everything following it is literal. An explicit `--` starts app arguments when the first one is
  option-shaped or when the project root is inferred, for example `javan run -- Alice`
- support compiler-derived startup properties without requiring `-D` on every value. Generated
  native launchers accept Java-compatible leading `-Dkey` or `-Dkey=value` and a friendly
  `--properties key=value... --` zone ending at an exact standalone `--` or end of input; every
  token inside the zone must be a valid assignment, and an ordinary token is an error rather than
  an implicit app-argument boundary. `javan run` accepts the same forms, consumes them before
  `main(String[])`, and preserves a literal escape through the app-argument `--` boundary. Missing
  and explicit-empty `-D` values both produce an empty string
- never reinterpret arbitrary bare `key=value` or `--key=value` application arguments as system
  properties. Startup assignments split at the first `=`, allow empty values, reject empty keys,
  and use deterministic last-assignment-wins precedence
- consume process arguments as already-tokenized `argv` values without a second quote or escape
  parser. Each property assignment occupies one argument and preserves spaces, quote characters,
  and additional `=` characters after the first `=`. Shell quotes group a value and are removed by
  the shell; quote characters that survive into `argv` are literal data. As with JVM and native
  process arguments generally, a NUL character cannot be represented
- analyze reachable `System.getProperty` and `System.getenv` calls and write
  `.javan/reports/runtime-inputs.json` plus `.md` with constant/finite/dynamic keys, defaults, call
  sites, and requiredness only when it is provable. Supplied values and secrets never enter reports
- for a finite property-key set, reject unknown friendly-zone keys with typo suggestions; a
  reachable dynamic property key enables arbitrary startup properties and is reported as dynamic.
  Java-compatible `-D` accepts any non-empty key even when unused. Environment variables remain a
  separate input channel and are never silently promoted to properties. Mutable
  `System.setProperty`, `clearProperty`, and `getProperties` semantics remain a separate
  compatibility decision
- consolidate Javan-owned runtime switches into the bounded launcher preamble or an out-of-band
  channel so profiling and diagnostics cannot consume a legitimate application argument
- reuse native outputs when a deterministic fingerprint of class/resource bytes, ordered
  dependencies, build options, target, and compiler/runtime identity is unchanged. Cache hits and
  misses report the reused stages and exact invalidation reason; uncertain state rebuilds instead
  of risking a stale binary

Planned launcher examples:

```sh
javan run . Alice 42
javan run -- Alice 42
javan run . -- --port 8080
javan run . -Dmode=prod input.txt
javan run . --properties mode=prod server.port=8080 -- input.txt
javan run . input.txt -Dmode=prod
javan run . -- --properties mode=prod
.javan/bin/app --properties mode=prod -- input.txt
javan run . "Alice Smith" 42
javan run . -- --message "hello world"
javan run . -Dmessage="hello world" input.txt
javan run . --properties 'display.name=Alice Smith' 'url=https://example.test/?a=b' empty= -- input.txt
javan run . --properties 'message=He said "hello"' -- input.txt
```

The sixth form passes `-Dmode=prod` literally because `input.txt` already started app arguments;
the seventh passes `--properties mode=prod` literally through the explicit boundary. In the
quoted forms, the shell passes each grouped value as one argument; the URL is split only at its
first `=`, and the double quotes inside the final single-quoted value remain part of that value.

Open external-integration acceptance criteria:

- make build plugins expose normal plugin configuration instead of requiring users to pass
  Maven `-D...` properties for ordinary app arguments

Rules:

- no hidden behavior changes based only on filename
- every alias maps to one explicit internal build plan
- launcher-owned arguments are consumed only inside the documented preamble; after the app
  boundary, bytes belong to `main(String[])`
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

- host-native CI covers `linux-x64` and `linux-aarch64`; the required local host gate
  covers macOS aarch64, while `macos-x64` remains deferred
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

- Javan generates its own closed-world reflection plan; developers do not maintain
  `reflection.json` or equivalent registration files
- class literals, constant and finite-set `Class.forName`, constant member lookups, and finite
  receiver-class flows are resolved automatically from reachable bytecode. Constant reflection
  must work without annotations or configuration, and `ldc` class constants feed the same finite
  class-value analysis
- arbitrary runtime class definition/loading, custom class loaders, runtime bytecode generation,
  unbounded proxies, instrumentation, `setAccessible`, reflective field mutation, and loader flows
  that may introduce classes from outside the resolved build classpath remain rejected when
  reachable

Build-time metadata acceptance criteria:

- retain one deterministic class index for names, access flags, hierarchy, interfaces, member
  signatures, and classpath origin instead of reparsing partial, inconsistent models
- stop discarding reflection-relevant classfile attributes. Decode class, field, method,
  parameter, and type annotations (including values and defaults), generic signatures, declared
  exceptions, method parameters, inner/enclosing and nest metadata, record components, and
  permitted subclasses as their supported reflection APIs require them
- preserve Java retention, inheritance, and repeatable-annotation rules: runtime-invisible
  annotations may inform build-only analysis but must not become visible through standard Java
  reflection; nested annotations and array, enum, class-literal, primitive, and string values
  retain their declared types
- value-bearing standard reflection returns generated immutable annotation implementations, not
  dynamic proxies. Acceptance covers `annotationType`, defaults, nested/array values, repeatable
  containers, `@Inherited`, `equals`, `hashCode`, `toString`, defensive array returns, and the
  standard missing-type, missing-enum, incomplete, and type-mismatch failures
- select emitted metadata by the intersection of reachable operation, finite receiver-class set,
  and requested capability: identity/name, hierarchy, annotation presence, annotation values,
  member descriptors, or invocation. Classpath-wide subtype or annotation queries may inspect
  the closed-world index, but emit only their results and the payloads actually read at runtime
- expand reflection queries to a fixed point with ordinary reachability. A reflected method body
  becomes executable reachability only when construction or invocation is reachable; metadata
  inspection alone must not retain method bodies or trigger class initialization
- make lazy, once-only class initialization a prerequisite for runtime reflection: class literals
  and metadata reads do not initialize a class, while the initializing `Class.forName` overload
  and reflective invocation do when Java requires it
- preserve lookup failures at runtime: a missing closed-world class/member produces the applicable
  `ClassNotFoundException`, `NoSuchMethodException`, or `NoSuchFieldException`, not a build error.
  The supported one- and three-argument `Class.forName` forms preserve the initialization flag and
  accept only statically known bootstrap/application loader identities
- every retained target and capability appears in deterministic reflection reports with its
  source call site, inference path, reason, exact emitted metadata/thunk bytes, and an attributed
  executable-size estimate whose method is reported. Machine-readable output is compiler evidence,
  not developer input
- when a data-dependent name cannot be reduced further, retain the requested capability across
  the finite resolved closed world with a warning and complete size report when that preserves
  exact semantics. Fail with a stable diagnostic only when an open-world loader/class-definition
  flow is reachable or the requested operation itself is unsupported; never suggest a handwritten
  JSON escape hatch

Staged runtime surface:

1. Read-only class metadata: identity, names, modifiers, hierarchy/assignability, enums, arrays,
   annotation presence, and declared/public field, method, and constructor descriptors for finite
   classes.
2. Value-bearing annotations through the generated immutable annotation contract above.
3. Closed-world lookup queries: exact name/signature lookups plus subtype and annotation result
   sets derived from the resolved classpath index. Because Java has no standard “all subtypes” or
   “all annotated classes” API, this requires a separately specified query surface or recognized
   adapter and does not promise compatibility with arbitrary classpath-scanner libraries.
4. Reflective method invocation as a non-hot-path side project: generate typed invocation thunks
   only for a finite target set and preserve access checks, argument conversion, boxing/unboxing,
   varargs, virtual dispatch, class initialization, return conversion, and
   `InvocationTargetException`. Invocation sites and retained code size are reported; no private
   access bypass or fake partial semantics is accepted.
5. Constructor invocation and read-only field access follow only after their Java semantics and
   reachability effects have equivalent acceptance coverage; field mutation remains rejected
   unless a separate roadmap decision defines a safe need and complete semantics.
6. Optional build-only hints such as `@JavanReflect` may later live in a separate companion
   library for the rare bounded target set that bytecode analysis cannot infer. Hints are not
   required for constant calls, cannot authorize unsupported behavior, and do not replace
   automatic analysis with registrations.

Service-loading strategy:

- resolve reachable `ServiceLoader.load` calls from the same closed-world class/resource index;
  developers do not maintain separate reflection or resource registrations for service providers
- honor standard `META-INF/services` entries and module `provides ... with ...` declarations,
  preserving specified declaration/classpath order and Java-compatible duplicate suppression;
  where cross-module order is unspecified, use and report one deterministic Javan order. Do not
  silently register every concrete subtype because that would change `ServiceLoader` semantics
- constant and finite service classes retain exactly their declared providers. A data-dependent
  service class may retain the finite declared service universe with a warning and size report when
  semantics remain exact; unsupported custom loader or module-layer behavior fails clearly
- provider descriptors are metadata reachability; provider constructors, static `provider()`
  methods, and implementation bodies become executable reachability only when the supported
  iteration/instantiation path requires them
- preserve lazy provider creation, provider ordering, reload over the immutable native provider
  table, validation, and `ServiceConfigurationError` behavior before claiming runtime support
- report every service, declaration origin, selected provider, duplicate, lookup site,
  reachability reason, and retained size. An undeclared service yields an empty loader; malformed
  declarations or missing referenced provider classes receive source-oriented diagnostics and
  preserve `ServiceConfigurationError` at the supported lazy access point rather than failing as a
  linker or startup accident

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
