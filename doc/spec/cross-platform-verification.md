# Cross-Platform Verification

Status: active verification policy. This file defines how `javan` proves behavior across
hosts, targets, JDK releases, and toolchain layouts.

## Goal

`javan` must be reproducible before it is clever. Normal verification must use local files,
restored caches, and deterministic test projects. It must not depend on flaky network access,
mutable external services, or whatever tool version wandered into `PATH` this morning.

This policy covers:

- required local macOS native smoke
- CI-based Linux and macOS ARM64 package verification; slower host/generation comparisons
  remain available through the manual timing workflow
- Docker-based Linux x64 and Linux aarch64 verification where available
- future Windows verification
- JDK matrix checks
- negative tests
- global `~/.javan` cache usage
- CI stages and release acceptance gates

Feature work may be explored outside the main tree, but this file defines how those
slices are verified once they touch cross-platform behavior.

## Local Host Gate

The local macOS host gate remains required for every meaningful compiler, runtime, linker,
toolchain, packaging, or diagnostics change.

Required local commands:

```sh
.github/scripts/verify-release.sh
```

For focused local debugging, the release gate expands to:

```sh
./mvnw -q clean verify
scripts/build.sh
ARCHIVE=$(.github/scripts/package-release.sh "${JAVAN_VERSION:-}")
.github/scripts/verify-package.sh "$ARCHIVE"
# after extracting the archive:
bin/javan doctor
bin/javan --help
bin/javan --version
JAVAN_BIN=bin/javan .github/scripts/acceptance.sh
JAVAN_BIN=bin/javan JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh
```

The sanitizer suite includes generated-app counter proof, package-backed generated
self-host allocation/GC/no-residue proof, and native-library/binding ownership proof.
Local macOS aarch64 proof exists; remote Linux release-matrix validation remains the CI gate.
The self-host proof requires nonzero allocation and GC counters plus zero
final tracked heap/root residue.

Package verification composes existing proofs instead of rebuilding the whole compiler a
fourth time: the packaged binary checks the compiler classes and builds its self-JAR, while
the sanitizer reuses C emitted by the selected bootstrap generation. Archive verification
separately runs the packaged compiler against the native showcase and installed JDK facade.
For macOS generation 3, the Linux x64 proof uploads the portable C emitted by its verified
third-generation compiler build. A fresh macOS runner compiles that same C natively, then runs
the normal package and self-host proofs. This keeps the required macOS binary proof while
removing two redundant macOS compiler generations and their runner-load variance.

Library-output changes are covered by `.github/scripts/acceptance.sh`, including:

```sh
dist/javan build src/test/resources/projects/acceptance/native-library --library --format static --export com.acme.Math.add --bindings c,rust,go,python
```

Static-library consumers must not need `-lm` for `Math.floor(double)`: the generated
binary64 implementation is integer-mask based, and the local macOS static C consumer
uses `cc caller.c lib<name>.a -o caller` without it. Linux and Windows rows have not
been run for this change.

Static-library consumers that reach `Math.atan2(double, double)` must link the host
math library on Linux (`-lm`). Generated Rust and Go bindings declare that Linux-only
dependency; direct C consumers remain responsible for their linker command.

CI proves required host-native target rows. Docker proves extra Linux/container behavior.
Neither replaces the local host gate.

## Native Self-Host Timing

Run the manual `Timings` workflow when changing reachability, verification, code generation,
the native runtime, or packaging. It runs bootstrap generations 2 and 3 with the same package
proof on Linux x64, Linux ARM64, and macOS ARM64. Each lane retains its phase report for seven
days, and the final `Summary` job shows one Gen2/Gen3 comparison table. Timings are diagnostic;
they do not replace correctness, sanitizer, or fixed-point gates.

## CI Host-Native Matrix

Every runtime footprint that Javan claims as host-native must run through the same public
entrypoints on each required target.

| Target | Runner | Required checks |
| --- | --- | --- |
| linux-x64 | `ubuntu-24.04` | `mvn verify`, acceptance, host `--target`, sanitizer suite with self-host proof |
| linux-aarch64 | `ubuntu-24.04-arm` | `mvn verify`, acceptance, host `--target`, sanitizer suite with self-host proof |
| macos-x64 | `macos-15-intel` | disabled package row; historically slower architecture lane |
| macos-aarch64 | `macos-15` | enabled host-native package and platform proof |

Linux rows are required. macOS ARM64 is enabled; macOS x64 and Windows package targets remain
explicit `enabled: false` rows until their timing or native runtime blockers are resolved.

## Container Matrix

Docker is used for Linux OCI image release coverage and cross-architecture smoke tests.
Container verification uses the Linux release archives generated by the same workflow run.

| Environment | Status | Runner |
| --- | --- | --- |
| linux/amd64 Wolfi | required release image | Docker Buildx |
| linux/arm64 Wolfi | required release image | Docker Buildx/QEMU |
| linux/amd64 distroless | required release image | Docker Buildx |
| linux/arm64 distroless | required release image | Docker Buildx/QEMU |
| linux/amd64 scratch | required release image | Docker Buildx |
| linux/arm64 scratch | required release image | Docker Buildx/QEMU |
| windows-x64 | planned later | Windows CI runner |
| windows-aarch64 | planned later | Windows CI runner when practical |

Release Dockerfiles:

```text
packaging/containers/javan-wolfi.Dockerfile
packaging/containers/javan-distroless.Dockerfile
packaging/containers/javan-scratch.Dockerfile
```

Each image build runs `javan --version`. The post-release image workflow verifies that
each pushed manifest contains `linux/amd64` and `linux/arm64`. The default Wolfi image
reuses `.github/scripts/verify-showcase.sh` to build `example` from
compiled classes and run the generated native binary inside the image. Distroless and
scratch stay version-smoke-only until they have a linker path.

## JDK Matrix

`javan` should feel JDK-like: it can wrap or locate a normal JDK, delegate `javac`, add
native-subset checks, and expose metadata friendly enough for IDEs and build tools.

Matrix policy:

- normal development uses the active local JDK
- release candidates test configured JDK homes
- LTS JDKs are mandatory release candidates
- current and previous feature JDKs are tracked when available
- JDKs and toolchains are cached under `~/.javan`
- normal verification never downloads JDKs from the network
- networked setup jobs may prewarm caches explicitly
- every JDK inventory and bytecode-pattern report is deterministic

Expected global layout:

```text
~/.javan/
  settings.toml
  cache/
    artifacts/
    jdks/
    toolchains/
  jdks/
    jdk-21/
    jdk-25/
  reports/
```

Planned commands:

```sh
javan toolchain install jdk 21
javan toolchain install jdk 25
javan toolchain list
javan compat --jdk ~/.javan/jdks/jdk-21 --jdk ~/.javan/jdks/jdk-25 src/test/resources/projects/native-profile/jdk-intrinsics
```

Expected project reports:

```text
.javan/reports/jdk-21-inventory.json
.javan/reports/jdk-25-inventory.json
.javan/reports/bytecode-patterns-jdk-21.json
.javan/reports/bytecode-patterns-jdk-25.json
```

## Negative Tests

Negative tests are required for every unsupported path that can be reached from user code.
They prevent new JDK behavior from slipping through and generating corrupted native output.

Initial categories:

- unsupported opcode
- unsupported constant-pool tag
- unsupported classfile attribute
- unsupported bootstrap method shape
- unsupported JDK intrinsic overload
- unsupported reachable JDK API
- invalid export signature
- missing toolchain
- broken Maven wrapper
- broken Gradle wrapper
- dependency checksum mismatch
- unsupported target platform
- invalid array copy type
- runtime null dereference
- runtime bounds access

Each negative test asserts one failure reason and one diagnostic contract.

## Test Shape

Every test checks one assumption, scenario, or case.

Good tests:

- `Math.abs(int)` lowers and matches JVM output
- `Math.abs(float)` lowers and matches JVM output
- `System.arraycopy` rejects primitive type mismatch
- repository Maven wrapper is used instead of system Maven
- missing configured JDK cache entry fails with a toolchain diagnostic

Bad tests:

- one test covering unrelated intrinsics, Maven, Gradle, and Docker
- one test that only proves "something failed"
- one test whose expected output depends on wall-clock equality
- one test that downloads dependencies during normal verification

Shared setup is allowed. Shared assumptions are not.

## CI Stages

Current pull-request and `main` entry workflows delegate to
`.github/workflows/build-common.yml`. Its independent stages run in parallel:

1. generated compatibility-status verification on the canonical Linux x64 platform
2. core `mvn verify` with merged JaCoCo reporting
3. six CLI integration shards
4. Linux x64 and arm64 public acceptance proof
5. Linux x64 and arm64 sanitizer proof with required C/Rust/Go/Python bindings
6. Linux x64/arm64 and macOS ARM64 extracted self-host package proof with packaged `bin/javan`;
   disabled macOS x64 and Windows artifact rows remain visible in the same matrix
7. Java compiler/platform-contract smoke on Linux, macOS, and Windows for x64 and arm64
8. focused Windows x64 runtime cross-compilation probes

The native proofs remain separate reusable jobs so acceptance, sanitizer, and package
self-host work no longer wait on one another. Platform-contract rows have explicit
`enabled` flags; an unreliable or disproportionately slow row is disabled in place rather
than deleted.

The manual release workflow validates the branch and version, delegates to this same common
build, downloads its verified native package artifacts, then publishes release metadata and
the GitHub release. It does not rebuild packages through a second path.

Planned expanded CI stages:

1. formatting and static checks
2. `mvn verify`
3. local/native smoke for the host runner
4. Linux x64 Docker smoke
5. Linux aarch64 Docker/buildx smoke when available
6. JDK compatibility inventory matrix
7. bytecode-pattern probe matrix
8. negative compatibility probes
9. static/shared library output and binding smoke
10. package/archive smoke
11. release acceptance gate

Network access is allowed only in explicit setup or cache-warming jobs. Verification jobs use
the checkout plus restored `~/.javan` caches.

## Acceptance Gates

A milestone cannot be marked complete until:

- `mvn verify` passes
- local macOS native smoke passes
- changed examples compile and run
- changed feature areas have focused tests
- every new rejection path has a negative test
- Docker Linux x64 passes when Docker is available
- Docker Linux aarch64 passes when buildx/QEMU is available
- JDK compatibility reports are generated for every configured JDK
- reports are deterministic across repeated runs
- generated binaries or libraries are smoke-tested
- normal verification has no network dependency

Release candidates additionally require:

- downloadable archive smoke
- Homebrew formula smoke once the formula exists
- Maven plugin smoke once the plugin exists
- Gradle plugin smoke once the plugin exists
- IDE diagnostics smoke once IDE integration exists
- Windows smoke once Windows support is introduced

## Ecosystem Verification

Future distribution work must verify the ecosystem paths, not just document them:

- standalone `javan` executable
- JDK-like layout and `javac` wrapping
- globally cached JDKs and toolchains under `~/.javan`
- global settings in `~/.javan/settings.toml`
- Maven plugin
- Gradle plugin
- Homebrew formula
- JetBrains plugin if LSP/build-server metadata is not sufficient

Global installs must be explicit, versioned, checksummed, and reproducible.
