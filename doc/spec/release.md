# Release

`javan` releases are host-native. GraalVM Native Image builds binaries for the
operating system and CPU architecture that run the build, so release automation
must build on each target runner instead of pretending cross-compilation is free.

## Local Gate

Run the same gate used by CI:

```sh
scripts/verify-release.sh
```

The gate runs:

- `mvn verify`
- `scripts/build-javan-native.sh`
- `dist/javan doctor`
- `dist/javan --help`
- `dist/javan --version`
- `scripts/acceptance.sh`
- `scripts/package-release.sh`
- `scripts/verify-package.sh`

## Acceptance Projects

`scripts/acceptance.sh` treats the checked-in `examples/` projects as acceptance
projects. For supported app projects it builds with `javan`, runs the same
classes on the JVM, runs the native executable, and compares output exactly.

It also checks:

- static library C ABI smoke test
- missing `Main.main` rejection
- multiple-main rejection
- reachable reflection rejection
- optional TypeMap and Nano probes when their local artifacts are available

## GitHub Actions

`.github/workflows/ci.yml` runs the local release gate on Linux x64 and macOS aarch64 for
pushes, pull requests, and manual dispatches.

`.github/workflows/release.yml` builds release packages on Linux x64 and macOS aarch64. A
tag matching `v*.*.*` publishes a GitHub Release. Manual dispatch packages a specific
version without publishing unless it is run from a tag.

## Platform Plan

Current guaranteed release targets:

- Linux x64 or aarch64 when a matching GitHub runner is configured
- macOS x64 or aarch64 when a matching GitHub runner is configured

Windows is intentionally not in the first release gate. The compiler runtime and
script surface still need a Windows-native linker path, `.exe` packaging, and
PowerShell-free verification before it belongs in the required matrix.
