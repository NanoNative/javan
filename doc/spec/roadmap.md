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

## 0.1 Native Hello

Real CLI, project detection, build invocation, class scanning, main detection, reachable
verification, C generation, native link, and integration tests.

## 0.2 Static Primitives

Done:

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

Remaining:

- richer loop/control-flow validation
- primitive widening, narrowing, and casts

## 0.25 Simple Objects

Done:

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
- direct same-method platform exception catch lowering
- basic enum constants with `name()` and `toString()`
- monomorphic interface dispatch
- closed-world virtual dispatch tables
- closed-world polymorphic interface dispatch tables
- `String.length`, `String.isEmpty`, `String.charAt`, and `String.equals` intrinsics
- javac `StringConcatFactory` string concatenation
- static fields and reachable class initializers for supported bytecode
- long, float, and double primitive arrays

Remaining:

- non-ASCII/full UTF-16 string runtime semantics
- general try/catch/finally exception-handler lowering
- richer class initialization ordering across complex dependency graphs
- full enum initialization beyond basic constant names

## 0.27 Deterministic Compatibility

Done:

- `javan compat`
- active JDK inventory through the `jrt:/` image
- project/dependency bytecode pattern inventory
- explicit opcode support classification
- fatal unknown-opcode policy
- generated compatibility summary reports
- generated support matrix docs

Remaining:

- multiple configured JDK homes in one run
- direct `javac --release` probe compilation matrix
- committed fixture baselines per JDK release
- API inventory diffs between JDK releases
- bootstrap-method shape policy gates beyond reporting

## 0.28 Native Library Output

Done:

- build kinds: `app`, `jar`, `staticlib`, and `sharedlib`
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
- library metrics reports
- deduplication planner reports after reachability

Remaining:

- annotation-driven exports
- stable ABI version sections
- richer object/record ABI models
- cross-target library linking
- Windows import-library details
- binding package manifests for Cargo, Go modules, and Python wheels
- direct LLVM/Cranelift backends after the C backend remains deterministic

## 0.285 JVM Jar And Resources

Done:

- `javan build --kind jar`
- jar builds without requiring `Main.main`
- optional jar manifest `Main-Class` through `--main`
- jar builds bypass native-profile verification and keep normal JVM bytecode
- plain `javac` resource copying from `src/main/resources` and `resources`
- resource inclusion in generated jars
- native app/library resource preservation under `.javan/resources` and `.javan/dist/resources`
- `.javan/reports/resources.md`
- `.javan/reports/resources.json`

Remaining:

- native `ClassLoader.getResource*` and `getResourceAsStream` runtime API
- generated C resource tables for embedded read-only resources
- resource lookup ABI for native library consumers
- resource compression and checksum reporting

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
- cache shared downloaded artifacts under `~/.javan/cache`
- keep project dependency decisions deterministic through `javan.lock`
- write deterministic lock files
- verify checksums
- keep Maven/Gradle import/export commands for existing projects

## 0.31 CLI Profiles, Test Command, And Target Surface

Done as CLI surface:

- `javan test`
- `javan build --release`
- `javan build --target linux-aarch64`
- `javan build --profile core`
- `javan build --profile service`
- `javan build --profile library`
- `javan build --profile strict`
- Maven, Gradle, and plain Java autodetection stays automatic
- main class, classpath, dependency indexes, target, and binary names remain inferred by default

Remaining:

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

Planned CLI:

- `javan check`
- `javan check --strict`
- `javan check --warnings-as-errors`
- `javan explain <diagnostic-id>`

Planned reports:

- `.javan/reports/safety-warnings.json`
- `.javan/reports/safety-warnings.md`

## 0.4 Bundled Toolchains

Status: planned. See [toolchain-distribution-roadmap.md](toolchain-distribution-roadmap.md).

Add `javan toolchain install`, `javan toolchain use`, and `javan doctor`.

The distribution should become JDK-like from the user's point of view: it can wrap an
original JDK, route `javac` through javan checks where useful, and expose enough stable
metadata that IDEs can understand the supported native subset quickly.

The distribution may wrap or manage:

- a JDK for `javac`
- a C compiler toolchain per platform where licensing permits
- optional GraalVM `native-image` for building `javan` itself
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
