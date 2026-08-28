# Release

Javan releases are host-native binary releases. Each package is built on the operating
system and CPU architecture it claims to support.

## First Native Release Scope

The current first-release contract claims host-native packages for Linux x64, Linux ARM64, and
macOS ARM64. A package must be extracted and exercised through its own `bin/javan`; compiling on
one target and relabeling the result for another is never release evidence.

macOS x64 and Windows x64/ARM64 package rows remain outside this contract until their matching
host proves package extraction, application build/run, self-host, ABI ownership, and sanitizer
behavior. Their platform-contract smoke rows are useful evidence, but do not imply package
support. The first-release rehearsal records those exclusions instead of silently treating them
as passes.

Before a public release, run an artifact-only rehearsal from clean inputs on every declared target.
It must preserve the package, checksum, toolchain, acceptance, self-host, and sanitizer evidence
described below without invoking a publication workflow. The current `dry_run=true` workflow
dispatch is not this rehearsal: it deliberately behaves like a real GitHub release. A rehearsal
may use only package-verification commands and already-produced artifacts, without credentials,
upload, tag, or release API paths.

The full package proof creates a matching, checksummed `-rehearsal.tar.gz` sidecar. It is
internal release evidence, never product content or a GitHub Release asset. The sidecar carries
only compiled Javan self-host input and deterministic fixtures, so the rehearsal runs in a clean
temporary directory without reading a source checkout. Run it with:

```sh
.github/scripts/rehearse-release-artifact.sh \
  --archive dist/release/javan-<version>-<target>.tar.gz \
  --target <target>
```

It writes `javan-<version>-<target>.rehearsal.json` and Markdown beside the archive. The report
records the built commit, target, C toolchain, completed package/self-host/acceptance/ABI/sanitizer
checks, known exclusions, and that publication is disabled. Full release-package CI runs this
proof for Linux x64, Linux ARM64, and macOS ARM64 before a release can publish.

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

Maven owns versioning. Local builds keep the POM default `1.0.0` and need no source edit. CI
resolves the UTC date once, then runs Maven `versions:set` inside its disposable checkout before
building. Main builds use `YYYY.M.D-SNAPSHOT`; manual releases use `YYYY.M.D`. Month and day
have no leading zeroes, matching the shared Java workflows and SemVer numeric identifiers. The changed
POM exists only in that workflow workspace: CI does not commit or push the version change.
Maven filters the resulting `project.version` into the generated `javan.cli.Version` source.
`versions:set` is resolved only when CI invokes it, so the Versions plugin is not part of the
normal project build. No version-setting script or manual POM bump is required. The repository wrapper pins Maven
3.9.16, while
`project.build.outputTimestamp` comes from the verified Git commit time for reproducible
archives; CI passes that same timestamp into the detached publication job.

All jobs read the Java version from `java-info-action` and invoke the repository Maven wrapper,
so the project declaration remains the source of truth. One Linux x64 job is the canonical owner of
the checked-in JDK compatibility snapshot. The snapshot records the Java feature contract,
not the current vendor, patch, or host stamp. Other operating-system and architecture jobs
still verify Javan, but cannot rewrite that snapshot with platform-dependent JDK inventory data.

| Trigger | Behavior |
| --- | --- |
| push to `main` | resolves the current UTC date as `YYYY.M.D-SNAPSHOT`, runs the common verification graph, uploads verified native artifacts, and publishes the Maven snapshot to GitHub Packages; Maven Central remains hard-disabled |
| manual dispatch | uses the current UTC date as `YYYY.M.D` and automatically creates the matching tag and GitHub release after verification |
| manual dispatch with `dry_run=true` | behaves identically on GitHub; the input is reserved for future Maven Central and Homebrew publication |

The `main` push path publishes only the Maven snapshot to GitHub Packages. It does not create
a Git tag or GitHub Release. Final publication is an explicit manual dispatch from `main`;
dispatches from other branches fail before the shared build starts.

There is no version or tag input. The common workflow resolves the date once and passes it to
every artifact job. For example, a build on 31 July 2026 uses version and release tag
`2026.7.31`.

Snapshot and final Maven artifacts publish through the same GitHub Packages workflow using the
repository `GITHUB_TOKEN`. Final releases create the tag directly at the verified commit and
upload the native archives with that token. The release workflow then directly invokes container
publication because events created with `GITHUB_TOKEN` do not start another workflow. The release
does not commit version or changelog changes to `main`.

Container images are published by the reusable `Container Images` workflow after a GitHub
release exists. It downloads the released Linux archives and can also be replayed manually with
the release tag. Its `release.published` trigger remains available for releases created outside
the automated release workflow.

The weekly `Maintenance` workflow uses the shared Maven Wrapper updater with the repository
`GITHUB_TOKEN`; JavaN needs no PAT. It opens a maintenance pull request and runs the normal PR
verification. Organization-wide merging of green maintenance PRs and weekly release dispatch
belongs in one NanoNative automation repository, where a single organization token can be held.
Coverage remains a Maven lifecycle output in `target/site/jacoco`; workflows neither upload
partial JaCoCo artifacts nor print partial job summaries.

## CI Matrix

The CI platform-contract matrix defines these rows; enabled rows run in parallel:

| Target | Runner | Status | Proof |
| --- | --- | --- | --- |
| `linux-x64` | `ubuntu-24.04` | Done | Remote Java build and compiler/platform contracts |
| `linux-arm64` | `ubuntu-24.04-arm` | Done | Remote Java build and compiler/platform contracts |
| `macos-x64` | `macos-15-intel` | Done | Remote Java build and compiler/platform contracts |
| `macos-arm64` | `macos-15` | Done | Remote Java build and compiler/platform contracts |
| `windows-x64` | `windows-2025` | Done | Remote Java build and compiler/platform contracts |
| `windows-arm64` | `windows-11-arm` | Blocked | Temurin 25 is unavailable on the GitHub-hosted runner |

These rows are explicit in `.github/workflows/build-common.yml`, including their `enabled`
flags. A problematic Windows row or a disproportionately slow secondary Linux/macOS
architecture is disabled by changing its flag to `false`, not by deleting it. A row becomes
`Done` only after remote CI evidence exists; even then it does not claim native packaging on
that platform.

Native artifact rows:

| Target | Runner | Status |
| --- | --- | --- |
| `linux-x64` | `ubuntu-24.04` | Done; remote package-backed self-host sanitizer proof passed |
| `linux-aarch64` | `ubuntu-24.04-arm` | Done; remote package-backed self-host sanitizer proof passed |
| `macos-x64` | `macos-15-intel` | Blocked; slower architecture row retained with `enabled: false` |
| `macos-aarch64` | `macos-15` | Done; remote package-backed self-host sanitizer proof passed |
| `windows-x64` | `windows-2025` | Blocked; row retained with `enabled: false` |
| `windows-aarch64` | `windows-11-arm` | Blocked; row retained with `enabled: false` |

The [accepted Snapshot run 32689384718](https://github.com/NanoNative/javan/actions/runs/32689384718)
proves every currently enabled platform-contract and package row. The slower macOS x64
package row remains disabled. Windows package rows remain disabled until native linker and
`.exe` package proof work on the matching host; Windows ARM64
platform proof is also blocked until the hosted runner supplies Temurin 25. The platform
matrix does not itself claim native package support.

The common workflow runs the Maven, acceptance, sanitizer, platform, and package proofs as
independent jobs. Manual releases call that same common workflow and download its verified
artifacts instead of rebuilding them in a second release-only path. The
package smoke builds the native `javan` binary, packages it, extracts the archive,
verifies package metadata, clears stale `target/.javan` state, runs packaged `bin/javan`
against the showcase, runs packaged `bin/javan check` and `javan report` on Javan's own
class files, uses the packaged binary to build a second native Javan smoke binary that
must start with the same version, and runs package-backed self-host sanitizer proof. The
package-backed sanitizer leg reuses the generated self-host C output from the immediately
preceding packaged self-build. The accepted Snapshot run supplies timing and package evidence
for every currently enabled package row.

## Maven Central

Maven Central publication is `Planned` and deliberately hard-disabled. The complete
`.github/workflows/publish-central.yml` workflow and its `build-merge.yml` call site remain
present with literal `if: false` gates. Behind the gate, the workflow downloads the verified
`build-workspace`, validates `GPG_PASSPHRASE`, `GPG_SIGNING_KEY`, `OSSH_PASS`, and
`OSSH_USER`, configures Maven/GPG, and runs the `publish` profile. Local bundle packaging is
verified with `-DskipPublishing=true`; remote publication remains unclaimed. Enabling it
requires an explicit reviewed source change; deleting the workflow is not the disable
mechanism.

## Acceptance Coverage

`.github/scripts/acceptance.sh` runs public-entrypoint checks over:

- native app parity against JVM output
- resource distribution
- jar output
- native-library C ABI smoke
- negative rejection projects
- native-profile runtime/codegen probes
- optional external smoke probes when local artifacts exist

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
