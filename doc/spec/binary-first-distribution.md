# Binary-First Distribution

Status: active product direction.

## Goal

`javan` is first a standalone executable that works beside existing Java tools. It
consumes normal Java build output, writes native artifacts and reports, and stays useful
without replacing the user's JDK, Maven, Gradle, or IDE.

## Product Shape

Primary artifact:

- `javan` host-native executable
- checksummed release archives per OS/ARCH
- Linux multi-arch OCI images published to GHCR from release assets
- Homebrew formula consuming those archives

Thin integrations:

- Maven plugin invokes the installed/downloaded `javan` binary after Maven has produced
  classes.
- Gradle plugin invokes the installed/downloaded `javan` binary after Gradle has produced
  classes.
- IDE plugin reads stable report JSON and optionally adds a build button that runs the
  normal project build followed by `javan`.

Non-goal for the first release:

- a JDK-like SDK wrapper selected as the IDE's project SDK
- fake platform classes
- replacing `javac`

## Linux libc-free Runtime Footprint

Status: planned.

Javan should eventually offer a Linux-only footprint that avoids libc for constrained
native programs and calls kernel syscalls directly. This is a size/deployment option,
not the default runtime.

Scope:

- Linux only; macOS and Windows keep their platform APIs.
- Start with tiny app/runtime modules where direct syscalls are stable and testable.
- Keep DNS, certificates, HTTPS, locale/timezone, and full thread runtime outside the
  first syscall slice unless they are explicitly implemented and stress-tested.
- Report the active syscall/libc posture in runtime reports and container image reports.

Acceptance:

- generated syscall binaries list no libc dependency in the runtime report
- unsupported runtime modules fail before native codegen when syscall mode is selected
- sanitizer/leak and native showcase smoke pass for syscall-supported modules
- normal system-linked builds remain the default

## Detection Rules

The binary should use existing output first:

| Project type | Class output detection | Javan output target |
| --- | --- | --- |
| Maven single module | `target/classes` | `target/javan` |
| Maven multi-module | every module `target/classes` reachable from aggregator | each module `target/javan`, plus root summary |
| Gradle single module | `build/classes/java/main`, Kotlin class output when present | `build/javan` |
| Gradle multi-project | every subproject class output reachable from root | each subproject `build/javan`, plus root summary |
| Plain Java | explicit `--classes`, existing `classes`, or `javac` into `target/javan/classes` | `target/javan` |
| Jar input | jar as classpath/root input | sibling `target/javan` or explicit output |

If class output is missing, `javan` may invoke the detected build tool:

- Maven: wrapper first, then `mvn`, compile phase only.
- Gradle: wrapper first, then `gradle`, classes task only.
- Plain Java: `javac` into the Javan output folder.

`javan` reads class-file versions from `.class` files. It should not require the user to
provide a Java version for normal operation. JDK download/installation is only needed
when `javac`, Maven, or Gradle cannot run with the local JDK. If automatic install is
implemented later, it must be deterministic, checksummed, and stored under `~/.javan`.

## Default Build Behavior

`javan build` should do the useful cheap work by default:

- run analysis/checks
- write JSON and Markdown reports
- build a native app when exactly one supported `main` is reachable
- build native library output when exports are configured
- build jar output when jar packaging is requested by config or integration

Users should disable outputs through calm configuration, not by learning many flags.
CLI flags remain as explicit overrides for automation.

## Report Contract

Every build/check writes stable reports by default:

- Markdown for humans
- JSON for CI, build plugins, and IDEs
- a compiler-diagnostic text stream compatible with tools that parse `javac` warnings

The canonical bytecode graph is available as `.javan/reports/control-flow.json` with exact
blocks and typed edges, plus a concise `.javan/reports/control-flow.md` summary.
Legacy `jsr`, `jsr_w`, and `ret` subroutines are inlined at class-file ingress, so every
analysis and backend receives the same ordinary branch graph.

Class initialization stays lazy at runtime. `new`, static field access, static calls, main,
and library exports use one dependency graph with superclass-before-subclass and applicable
default-interface ordering. Recursive initialization by the same thread observes the JVM
default state; other threads wait for completion. The exact owners, dependencies, and active-use
sites are written to `.javan/reports/class-initialization.json`, with a concise Markdown summary.

Virtual and interface dispatch use a reachable-construction fixpoint. Concrete classes enter the
receiver set through reachable `new` sites, enum bootstraps, native substitutions, or externally
supplied entry receivers and parameters; materialized lambdas keep their existing exact target path.
Unknown external receivers remain conservative. The resulting receiver types and their origins are written
to `.javan/reports/instantiated-types.json` and `.javan/reports/instantiated-types.md`, and the C
backend consumes the same facts as reachability.

The C backend emits class structs and descriptors only for classes required by reachable code,
instantiated types, class initialization, and their hierarchies. Stable type IDs keep lowered
instructions correct when unused classes leave gaps. Incomplete analysis or reachable
`Class.forName(String)` retains the complete parsed class set conservatively.

Direct `Function.apply` and `Supplier.get` calls refine that global receiver set with exact types
tracked through locals, casts, final fields, direct arguments, returns, and control-flow merges. Sets
of up to four types are written to `.javan/reports/receiver-provenance.json` and its Markdown summary.
Unknown or larger sets automatically fall back to the global instantiated receiver set.

Lowered functions also use one conservative local-fact model for nullness, integer constants
and ranges, exact types, and array/string lengths. Debug builds report proof candidates without
rewriting code. Release builds consume those same facts to remove proven redundant plain
`Objects.requireNonNull` guards and dead branch paths. Decisions and source locations are written
to `.javan/reports/optimizations.json` and its Markdown summary.

That report also records transitive method effects: pure, may-throw, allocates, reads, writes,
and unknown. The compiler uses them conservatively so non-writing calls preserve mutable field
facts while writes and unknown calls discard those facts before further optimization.

The same report counts managed allocations classified as `NoEscape`, `ArgumentEscape`, or
`GlobalEscape`, including transitive capture through exact application calls. Release stack selection
uses a conservative 4 KiB budget per function for constant primitive arrays and application objects
proven `NoEscape` outside control-flow cycles. Stack-object runtime state and
managed-reference fields remain GC roots for the function lifetime; every other site keeps managed
allocation behavior.

The IDE plugin should render reports. It must not infer native support from source code
or JDK inventory by itself.

## Plugin Contract

Plugins are adapters:

- find or download the `javan` binary
- run the normal Java build first
- pass project output folders to `javan`
- attach generated artifacts to the Maven/Gradle build output
- surface diagnostics from the same report files as the CLI

Plugins must not duplicate compiler logic.

## First Release Gate

Before a first public binary release:

- Linux x64 archive builds and verifies remotely.
- macOS aarch64 archive builds and verifies remotely.
- Linux `amd64`/`arm64` Wolfi, distroless, and scratch images build remotely from
  published release assets.
- archives contain only UTC date versions in `YYYY.M.D` format without leading zeroes.
- archive verification extracts the package and proves packaged `bin/javan`.
- packaged `bin/javan --version`, `javan doctor`, `javan build example`,
  showcase report generation, stale-report-resistant self-check/report, package-built
  native Javan smoke, acceptance, and sanitizer/leak gates pass.
- unfinished Java support is visible in README, support matrix, and reports.

Pull-request CI runs a lighter extracted-package smoke on every Linux/macOS release row.
The full extracted-package acceptance and sanitizer/leak gate stays in the release
workflow, where longer runtime is acceptable and easier to replay.

Windows, Maven plugin, Gradle plugin, Homebrew tap, and IDE plugin may start from the
release-test branch, but they are not blockers for the first Linux/macOS binary archive.
Linux container images are produced by the post-release image workflow from those
archives.
