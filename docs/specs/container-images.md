# Container Images

## Human Review

Owns Linux image composition and proof from released archives. Host-native artifact gates
remain with [release](release.md). Image construction is implemented; no current remote
publish success is certified by this documentation update.

Before changing bases, trust/certificate contents or static linkage, agree the resulting
artifact contract. Existing open container issues need evidence reconciliation, not assumed
completion from the presence of workflow code.

## Requirements And Acceptance

| ID | Contract | Public evidence / gap |
| --- | --- | --- |
| IMG-001 | An image MUST derive from checksummed matching Linux release archives and report its actual platform composition. | [Container workflow](../../.github/workflows/container-images.yml), [package surface tests](../../src/test/java/javan/ReleasePackagingSurfaceTest.java); publication evidence still needs reconciliation. |
| IMG-002 | Image acceptance MUST prove the immutable published digest and the documented capabilities of each variant. | Default image builds/runs the showcase; linker-less variants only claim version smoke. [Open evidence](../verification.md#evidence-still-needed). |

Status: post-release image pipeline implemented locally; remote GHCR publish is
unverified until one successful release-triggered run publishes the images.

## Goal

Publish Javan as Linux OCI images that developers can run from Linux, macOS, and Windows
through a Linux-container runtime.

The images always contain `javan`. There is no option to remove it.

## Targets

| Image | Base | Platforms | Purpose |
| --- | --- | --- | --- |
| `ghcr.io/nanonative/javan:<version>` | Chainguard gcc-glibc | `linux/amd64`, `linux/arm64` | default image with C linker; expects compiled class output |
| `ghcr.io/nanonative/javan:<version>-wolfi` | Chainguard gcc-glibc | `linux/amd64`, `linux/arm64` | explicit default variant |
| `ghcr.io/nanonative/javan:<version>-distroless` | distroless C runtime | `linux/amd64`, `linux/arm64` | minimal runtime image without shell |
| `ghcr.io/nanonative/javan:<version>-scratch` | scratch plus copied dynamic runtime libs | `linux/amd64`, `linux/arm64` | smallest runtime image |

`latest`, `wolfi`, `distroless`, and `scratch` tags are pushed by the post-release image
workflow, never by artifact rehearsal. Release dispatch with `dry_run=true` still invokes
this publication path, as defined in [Release](release.md#release-versioning).

The default image intentionally does not include a JDK yet. It is meant to run after
`javac`, Maven, or Gradle has produced classes, while still containing the C toolchain
needed for native linking.

## Host Behavior

The images are Linux images.

| Developer host | Runtime behavior |
| --- | --- |
| Linux x64 | runs `linux/amd64` directly |
| Linux arm64 | runs `linux/arm64` directly |
| macOS Intel | runs `linux/amd64` through Docker Desktop |
| macOS Apple Silicon | runs `linux/arm64`; can force `linux/amd64` through emulation |
| Windows x64 | runs `linux/amd64` through Docker Desktop/WSL2 |
| Windows arm64 | runs `linux/arm64` when available; can force `linux/amd64` through emulation |

## Release Flow

The [release workflow](../../.github/workflows/release.yml) invokes the reusable container
workflow after publishing the GitHub release assets; image work does not delay archive
publication but remains part of the overall release run. The container workflow also accepts
manual replay with an existing release tag and `release.published` events for releases
created outside that workflow. Events created with `GITHUB_TOKEN` do not start a second run.

1. Checks out the release tag.
2. Downloads the released Linux x64 and Linux arm64 archives plus checksums.
3. Verifies the downloaded archive checksums.
4. Builds Wolfi, distroless, and scratch variants with Docker Buildx.
5. Runs `javan --version` during each image build.
6. Pushes multi-platform manifests to GHCR.
7. Verifies every pushed tag contains `linux/amd64` and `linux/arm64`.
8. Reuses `.github/scripts/verify-showcase.sh` with `JAVAN_IMAGE` set to the default
   Wolfi image, builds `example` from compiled classes, and runs the
   produced native binary inside the image.
9. Resolves each immutable image digest and retains JSON evidence for 90 days identifying
   the release version, both Linux archives/checksums, verified platforms and published digest.

Distroless and scratch images remain version-smoke images until they include or can
locate a native linker. The showcase build is deliberately tied to the default image
because that is the image that contains the C toolchain today.

## Scratch Honesty

The current scratch image is not a single-static-binary image. It copies the Linux
dynamic loader and shared libraries reported by `ldd` into a scratch root filesystem.
The build verifies that root filesystem with `chroot` and then verifies the final
scratch image with `javan --version`.

When Javan can produce fully static self-contained Linux binaries, the scratch image
should switch to binary-only plus required certificates.
