# Container Images

Status: post-release image pipeline implemented; remote GHCR publish still needs one
successful release-triggered run.

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
workflow, not by release-package dry runs.

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

The release workflow:

1. Builds Linux x64, Linux arm64, macOS x64, and macOS arm64 release archives.
2. Publishes those archives as immutable GitHub release assets.
3. Stops. Image publication is not on the package-publish critical path.

The container image workflow then runs after the GitHub release is published. It can also
be manually replayed with an existing release tag.

1. Checks out the release tag.
2. Downloads the released Linux x64 and Linux arm64 archives plus checksums.
3. Verifies the downloaded archive checksums.
4. Builds Wolfi, distroless, and scratch variants with Docker Buildx.
5. Runs `javan --version` during each image build.
6. Pushes multi-platform manifests to GHCR.
7. Verifies every pushed tag contains `linux/amd64` and `linux/arm64`.

## Scratch Honesty

The current scratch image is not a single-static-binary image. It copies the Linux
dynamic loader and shared libraries reported by `ldd` into a scratch root filesystem.
The build verifies that root filesystem with `chroot` and then verifies the final
scratch image with `javan --version`.

When Javan can produce fully static self-contained Linux binaries, the scratch image
should switch to binary-only plus required certificates.
