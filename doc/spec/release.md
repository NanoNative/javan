# Release

Javan releases are host-native binary releases. Each package is built on the operating
system and CPU architecture it claims to support.

## Local Gate

Run the same gate used by the release matrix:

```sh
.github/scripts/verify-release.sh
```

The gate runs:

- `mvn verify`
- `scripts/build.sh`
- `dist/javan doctor`
- `dist/javan --help`
- `dist/javan --version`
- `.github/scripts/acceptance.sh`
- `JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh`
- `.github/scripts/package-release.sh`
- `.github/scripts/verify-package.sh`

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
target mismatches and non-triplet versions.
