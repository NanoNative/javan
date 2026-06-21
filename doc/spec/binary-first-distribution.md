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
- archives contain only final numeric versions.
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
