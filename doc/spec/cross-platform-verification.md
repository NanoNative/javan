# Cross-Platform Verification

Status: active verification policy. This file defines how `javan` proves behavior across
hosts, targets, JDK releases, and toolchain layouts.

## Goal

`javan` must be reproducible before it is clever. Normal verification must use local files,
restored caches, and deterministic fixtures. It must not depend on flaky network access,
mutable external services, or whatever tool version wandered into `PATH` this morning.

This policy covers:

- required local macOS native smoke
- Docker-based Linux x64 and Linux aarch64 verification
- future Windows verification
- JDK matrix checks
- negative tests
- global `~/.javan` cache usage
- CI stages and release acceptance gates

Feature incubation and agent handoff rules live in
[feature-lab-workflow.md](feature-lab-workflow.md). This file defines how those slices are
verified once they touch cross-platform behavior.

## Local Host Gate

The local macOS host gate remains required for every meaningful compiler, runtime, linker,
toolchain, packaging, or diagnostics change.

Required local commands:

```sh
scripts/verify-release.sh
```

For focused local debugging, the release gate expands to:

```sh
mvn verify
scripts/build-javan-native.sh
dist/javan doctor
dist/javan --help
scripts/acceptance.sh
scripts/package-release.sh
```

Library-output changes are covered by `scripts/acceptance.sh`, including:

```sh
dist/javan build examples/native-library --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
```

Docker proves other environments. It does not replace the host gate.

## Docker Matrix

Docker is used where practical for Linux host coverage and cross-architecture smoke tests.
Docker verification must run from the current checkout and restored caches.

| Environment | Status | Runner |
| --- | --- | --- |
| macos-aarch64 | required now | local host |
| macos-x64 | planned | CI runner or dedicated host |
| linux-x64 | required when Docker is available | Docker `linux/amd64` |
| linux-aarch64 | required when buildx/QEMU is available | Docker buildx `linux/arm64` |
| windows-x64 | planned later | Windows CI runner |
| windows-aarch64 | planned later | Windows CI runner when practical |

Future Docker entrypoints:

```sh
docker buildx inspect
docker buildx build --load --platform linux/amd64 -f scripts/docker/Dockerfile.verify -t javan-verify:linux-amd64 .
docker run --rm -v "$HOME/.javan:/root/.javan" javan-verify:linux-amd64 scripts/ci/verify-linux.sh

docker buildx build --load --platform linux/arm64 -f scripts/docker/Dockerfile.verify -t javan-verify:linux-arm64 .
docker run --rm -v "$HOME/.javan:/root/.javan" javan-verify:linux-arm64 scripts/ci/verify-linux.sh
```

If Docker, buildx, or QEMU is missing, the verification report must record a skipped check
with the exact reason. Silent absence is failure wearing a fake mustache.

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
javan compat --jdk ~/.javan/jdks/jdk-21 --jdk ~/.javan/jdks/jdk-25 examples/jdk-intrinsics
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
- `Math.abs(float)` fails as an unsupported overload
- `System.arraycopy` rejects primitive type mismatch
- Maven wrapper is preferred over system Maven
- missing configured JDK cache entry fails with a toolchain diagnostic

Bad tests:

- one test covering unrelated intrinsics, Maven, Gradle, and Docker
- one test that only proves "something failed"
- one test whose expected output depends on wall-clock equality
- one test that downloads dependencies during normal verification

Shared setup is allowed. Shared assumptions are not.

## CI Stages

Current CI stages in `.github/workflows/ci.yml`:

1. GraalVM 25 setup
2. `scripts/verify-release.sh`
3. host-native archive upload

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
