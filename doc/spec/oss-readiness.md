# OSS Readiness

This file tracks the non-code requirements for publishing `javan` as an open-source
repository.

## Ready

- Core repo lives at `javan-project/javan`.
- The default branch is `main`.
- Version is `0.1.0`, not a snapshot.
- Local release gate is `scripts/verify-release.sh`.
- Release package smoke extracts the archive, verifies SHA-256, runs `bin/javan --version`,
  and rejects snapshot archives.
- CI and release workflows exist for Linux and macOS host-native builds.
- Community templates live in `.github/`.

## Required Before Public Release

- Choose and add a `LICENSE`.
- Configure the remote URL.
- Update Maven metadata with the final GitHub URL and SCM coordinates.
- Run GitHub Actions on the actual remote.
- Confirm Linux release archive from CI.
- Decide whether TypeMap and Nano compatibility probes should be pinned for remote CI or
  remain local-optional.
- Decide whether Windows is a supported release target for this version or explicitly
  mark it unsupported in the release notes.

## Commit Policy

- Use semantic commits.
- Squash local work before pushing when push permission is granted.
- Do not include tool, model, or generated-by attribution in commits, PRs, changelogs,
  release notes, or repository docs.
