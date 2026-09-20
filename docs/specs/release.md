# Release

## Human Review

Owns release scope, candidate acceptance and publication. A passing historical run proves
its named artifact; release approval needs the selected candidate's evidence.
[ADR 0004](../adr/0004-release-evidence.md) records the rationale.

Public asset filtering has [local regression proof](../verification.md#release-asset-selection).
Publication still requires a clean candidate's complete host-native evidence; a passing upload
fixture does not certify the packages or publish a release.

Before publication, identify the candidate and verify its required target artifacts.
New release targets, static-linkage policy, Maven Central or Homebrew publication require
a separate explicit decision. This documentation change selects none of them.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| REL-001 | A claimed target MUST pass extraction and execution using the archive built for that OS/architecture. | [Package verifier](../../.github/scripts/verify-package.sh); first-release scope below. |
| REL-002 | A release candidate MUST retain checksum, self-host, acceptance, ABI and required sanitizer evidence from its packaged binary. | [Artifact rehearsal](../../.github/scripts/rehearse-release-artifact.sh), [package tests](../../src/test/java/javan/ReleasePackagingSurfaceTest.java); rerun for each candidate. |
| REL-003 | Main snapshots MUST publish to GitHub Packages without a release/tag; final manual publication MUST use the verified commit and date version. | [Snapshot workflow](../../.github/workflows/build-merge.yml), [release workflow](../../.github/workflows/release.yml). Central and Homebrew publication stay disabled. |
| REL-004 | Artifact rehearsal MUST avoid publication; release dispatch with `dry_run=true` MUST be described as real GitHub publication. | Rehearsal command below and dispatch policy tests; the input only reserves future irreversible publishers. |
| REL-005 | GitHub release assets MUST contain only the declared public deliverables, never internal rehearsal bundles or reports. | [Public asset selection](#public-release-assets), executable workflow regression cases in [release surface tests](../../src/test/java/javan/ReleasePackagingSurfaceTest.java). |

Javan releases are host-native binary releases. Each package is built on the operating
system and CPU architecture it claims to support. The primary deliverables are checksummed
archives containing the `javan` executable. Linux multi-architecture OCI images consume those
release assets under the [container contract](container-images.md); the Homebrew formula also
consumes the archives, while tap publication remains disabled.

## First Native Release Scope

This table is the package-scope contract. Runner selection belongs to the
[common workflow](../../.github/workflows/build-common.yml), not a second prose matrix.

| Host | Script target | First-release scope |
| --- | --- | --- |
| Linux x64 | `linux-x64` | Required |
| Linux ARM64 | `linux-aarch64` | Required |
| macOS ARM64 | `macos-aarch64` | Required |
| macOS x64 | `macos-x64` | Excluded; slower package lane retained but disabled |
| Windows x64 | `windows-x64` | Excluded; native package/self-host proof incomplete |
| Windows ARM64 | `windows-aarch64` | Excluded; native package/self-host proof incomplete |

A package must be extracted and exercised through its own `bin/javan` on the matching host.
Platform-contract smoke does not imply package support. Promoting an excluded row requires
the same package, self-host, ABI and sanitizer evidence as an existing release target.

Before a public release, run an artifact-only rehearsal from clean inputs on every required target.
A rehearsal uses package-verification commands and already-produced artifacts without credentials,
upload, tag or release API paths. Release dispatch, including `dry_run=true`, is publication;
see [Release versioning](#release-versioning).

The full package proof creates a matching, checksummed `-rehearsal.tar.gz` sidecar. It is
internal release evidence, never product content or a GitHub Release asset. The sidecar carries
only compiled Javan self-host input and deterministic fixtures, so the rehearsal runs in a clean
temporary directory without reading a source checkout. Run it with:

```sh
.github/scripts/rehearse-release-artifact.sh \
  --archive "dist/release/javan-<version>-<target>.tar.gz" \
  --target "<target>"
```

It writes `javan-<version>-<target>.rehearsal.json` and Markdown beside the archive. The report
records the built commit, target, C toolchain, completed package/self-host/acceptance/ABI/sanitizer
checks, known exclusions, and that publication is disabled. Full release-package CI runs this
proof for every required target before a release can publish.

## Candidate Evidence Procedure

This is the executable acceptance procedure for REL-001/002/004, not another implementation
project. Use one clean candidate commit across all required hosts and record its full SHA.
Historical runs and archives with unknown or different commit metadata cannot certify it.

1. Prepare matching host toolchains using [native host verification](testing.md#native-host-verification).
   Acquire candidate archives, checksums and matching rehearsal sidecars from a recorded full
   package build. A normal PR's `bootstrap` package is not sufficient evidence.
2. If that candidate has no full artifact set, run the existing package proof from its clean
   checkout on each required host. This example is for macOS ARM64; use the table's matching
   script target on the other hosts:

   ```sh
   JAVAN_PACKAGE_TARGET=macos-aarch64 JAVAN_PACKAGE_PROOF_SCOPE=full \
     sh .github/scripts/verify-ci-package-smoke.sh
   ```

   This builds the archive, proves native imports and self-host behavior, creates the sidecar,
   and runs artifact rehearsal without publication. Its default sanitizer scope is `full`.
   When reusing CI evidence, record the actual scope and accompanying sanitizer-job evidence;
   do not describe a `package-smoke` self-host probe as the full self-host workload.
3. Replay supplied archives with the rehearsal command above on their matching hosts. Keep
   both checksummed archives beside the command's JSON/Markdown result; the harness extracts
   into a temporary directory and uses the sidecar's compiled inputs, not source-checkout state.
4. Assemble one candidate record in [verification history](../verification.md), linking the
   artifacts and logs rather than copying their reports into specs. For each required target,
   check that the report says `status=pass`, identifies the candidate commit and target, lists
   `package,self-host,acceptance,abi,sanitizer`, and says `publication=disabled`. Retain import
   proof and the actual sanitizer scope/counters alongside it. Missing evidence stays missing.
5. If any check fails, preserve the command, target, toolchain and diagnostic; reduce it to a
   regression, fix that bounded behavior and rerun the affected proof. A changed candidate
   needs its own complete evidence set. Do not enable targets or publishers to bypass a failure.

Completion means one candidate has every required proof, with exclusions recorded and no
publication performed. It does not approve a release or claim broader Java compatibility.

## Public Release Assets

[native-proof.yml](../../.github/workflows/native-proof.yml) retains package artifacts separately
from internal rehearsal evidence. The [release upload step](../../.github/workflows/release.yml)
selects only `javan-<resolved-version>-<required-target>.tar.gz`, its `.sha256`, and the
previously generated/validated `javan.rb`. All seven files must be regular and nonempty before
any GitHub release call. The step logs the exact selected paths and uses the same selection on retry.
Keep its required targets aligned with [release scope](#first-native-release-scope); platform
smoke rows do not add deliverables.

| Given / when | Required result |
| --- | --- |
| Current-version native archives and checksums for every required target, plus the validated `javan.rb` formula | Only those public files reach the upload call. Formula generation is not Homebrew-tap publication. |
| Rehearsal archives/checksums, rehearsal JSON/Markdown and unrelated files in the same downloaded directory | None reach the upload call; retained CI evidence is untouched. |
| Any required file is missing or empty, or only another version's archive/checksum exists | Fail before contacting GitHub; do not silently publish a partial target set. |
| The GitHub release already exists | Reuse the same public asset selection for replacement; do not widen it on retry. |
| Release creation or upload fails | Preserve the failure exit; creation failure must not proceed to upload. |

The [executable tests](../../src/test/java/javan/ReleasePackagingSurfaceTest.java) run the actual
workflow shell body with a recording `gh` command in temporary directories. They prove selection,
ordering of validation and error propagation without network access or publication. Archive
content/checksum integrity and formula validity remain owned by preceding package/formula gates.
This fix does not remove files from existing remote releases. Actual publication still requires
candidate evidence and separate approval; `dry_run=true` is not the local test harness.

## Local Gate

For the combined local checkout gate, run:

```sh
.github/scripts/verify-release.sh
```

The [script](../../.github/scripts/verify-release.sh) owns the sequence: full Maven
verification, native build, archive extraction, packaged CLI checks, acceptance and sanitizers.
It does not create the candidate rehearsal sidecar; use the candidate procedure for that.
Routine edit feedback uses the depths in [Testing](testing.md#local-verification).

## Release Versioning

Maven owns versioning. Local builds keep the POM default `1.0.0` and need no source edit. CI
resolves the UTC date once, then runs Maven `versions:set` inside its disposable checkout before
building. Main builds use `YYYY.M.D-SNAPSHOT`; manual releases use `YYYY.M.D`. Month and day
have no leading zeroes, matching the shared Java workflows and SemVer numeric identifiers. The changed
POM exists only in that workflow workspace: CI does not commit or push the version change.
Maven filters the resulting `project.version` into the generated `javan.cli.Version` source.
`versions:set` is resolved only when CI invokes it, so the Versions plugin is not part of the
normal project build. No version-setting script or manual POM bump is required. The repository
wrapper owns the Maven version; `project.build.outputTimestamp` comes from the verified
Git commit time for reproducible archives. CI passes the same timestamp to the detached
publication job.

CI reads the project Java version through `java-info-action` and invokes the repository Maven
wrapper. [Testing](testing.md#generated-compatibility-status) owns compatibility-refresh rules.

| Trigger | Behavior |
| --- | --- |
| push to `main` | verifies and publishes the Maven snapshot to GitHub Packages without a Git tag or GitHub Release |
| manual dispatch from `main` | verifies, publishes Maven artifacts to GitHub Packages, and creates the matching tag and GitHub release; other branches fail before building |
| manual dispatch with `dry_run=true` | behaves identically on GitHub; the input is reserved for future Maven Central and Homebrew publication |

There is no version or tag input. The common workflow resolves the date once and passes it to
every artifact job. For example, a build on 31 July 2026 uses version and release tag
`2026.7.31`.

Snapshot and final Maven artifacts publish through the same GitHub Packages workflow using the
repository `GITHUB_TOKEN`. Final releases create the tag directly at the verified commit and
upload the native archives with that token. The release workflow then directly invokes container
publication because events created with `GITHUB_TOKEN` do not start another workflow. The release
does not commit version or changelog changes to `main`.

The [container contract](container-images.md#release-flow) owns image publication, digest proof
and replay after the GitHub release exists.

The weekly `Maintenance` workflow uses the shared Maven Wrapper updater with the repository
`GITHUB_TOKEN`; JavaN needs no PAT. It opens a maintenance pull request and runs the normal PR
verification. Organization-wide merging of green maintenance PRs and weekly release dispatch
belongs in one NanoNative automation repository, where a single organization token can be held.
Coverage reporting remains owned by [Testing](testing.md).

## CI Matrix

The [common workflow](../../.github/workflows/build-common.yml) owns runner names, enabled
flags and proof depths. [Testing](testing.md#ci-execution) explains the graph and its PR,
snapshot and manual-release differences. Required package claims are defined only in
[First native release scope](#first-native-release-scope).

Earlier target acceptance is recorded in [verification history](../verification.md#release-foundation).
An old green matrix is not evidence that the selected candidate passed. Manual releases
reuse verified common-build artifacts instead of rebuilding through a separate path.

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

The [acceptance harness](../../.github/scripts/acceptance.sh) owns executable probes.
[Testing](testing.md#test-shape) owns their required behavior coverage; [examples](examples-and-test-projects.md)
owns fixture placement and the external-probe boundary.

## Package Rules

Release archives contain only:

- `bin/javan`
- `README.md`
- `VERSION`
- `LICENSE` when present

Each archive has a SHA-256 file and is verified before upload. Package scripts reject
target mismatches and non-triplet versions. Verification must exercise the extracted
binary, not only the pre-package `dist/javan`.
