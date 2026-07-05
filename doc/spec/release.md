# Release

Javan releases are host-native binary releases. Each package is built on the operating
system and CPU architecture it claims to support.

## Local Gate

Run the same gate used by the release matrix:

```sh
.github/scripts/verify-release.sh
```

The gate runs:

- `mvn clean verify`
- `scripts/build.sh`
- `.github/scripts/package-release.sh`
- `.github/scripts/verify-package.sh`, which extracts the archive and builds/runs
  `example` with packaged `bin/javan`
- packaged `bin/javan doctor`
- packaged `bin/javan --help`
- packaged `bin/javan --version`
- `.github/scripts/acceptance.sh` with `JAVAN_BIN` set to packaged `bin/javan`
- `JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh` with
  `JAVAN_BIN` set to packaged `bin/javan`

## Release Versioning

The workflow owns versioning. No manual `pom.xml` version bump is required.

| Trigger | Behavior |
| --- | --- |
| push to `main` | computes today's `vYYYY.M.D`, updates `pom.xml` and `CHANGELOG.md`, tags, and publishes |
| manual dispatch | uses the provided `tag`, or today's `vYYYY.M.D` when empty |
| manual dispatch with `dry_run=true` | builds and verifies packages without committing, tagging, or publishing |

Release tags must match `vYYYY.M.D`, for example `v2026.6.14`.

Non-dry-run publishing requires the repository `BOT_TOKEN` secret. The release workflow
must create the GitHub release with that bot token so GitHub emits the follow-up
`release.published` event used by image publication.

Container images are published by the separate `Container Images` workflow after a
GitHub release exists. That workflow downloads the released Linux archives and can be
replayed manually with the release tag.

## CI Matrix

Required release package targets:

| Target | Runner |
| --- | --- |
| `linux-x64` | `ubuntu-24.04` |
| `linux-aarch64` | `ubuntu-24.04-arm` |
| `macos-aarch64` | `macos-15` |
| `macos-x64` | `macos-15-intel` |

Windows is tracked but not in the first release gate. It still needs a native linker path,
`.exe` package verification, and CI coverage before it is claimed.

The release matrix is configured so every CI row runs the Maven suite, public acceptance
suite, sanitizer suite, host-target native build check, and self-host package smoke. The
package smoke builds the native `javan` binary, packages it, extracts the archive,
verifies package metadata, clears stale `target/.javan` state, runs packaged `bin/javan`
against the showcase, runs packaged `bin/javan check` and `javan report` on Javan's own
class files, uses the packaged binary to build a second native Javan smoke binary that
must start with the same version, and runs package-backed self-host sanitizer proof. The
package-backed sanitizer leg reuses the generated self-host C output from the immediately
preceding packaged self-build, and existing `platform-smoke` rows keep the self-host
proof but narrow its probes to `--version` plus the tiny build/check loop instead of
rerunning the full packaged `check/report target/classes` cycle. For
M13R, remote validation remains 0/4 completed until those rows pass with package-backed
sanitizer proof.

## Acceptance Coverage

`.github/scripts/acceptance.sh` runs public-entrypoint checks over:

- native app parity against JVM output
- resource distribution
- jar output
- native-library C ABI smoke
- negative rejection projects
- native-profile runtime/codegen probes
- optional TypeMap and Nano probes when local artifacts exist

Test-only projects live under `src/test/resources/projects`, not `examples`.

## Package Rules

Release archives contain only:

- `bin/javan`
- `README.md`
- `VERSION`
- `LICENSE` when present

Each archive has a SHA-256 file and is verified before upload. Package scripts reject
target mismatches and non-triplet versions. Verification must exercise the extracted
binary, not only the pre-package `dist/javan`.
