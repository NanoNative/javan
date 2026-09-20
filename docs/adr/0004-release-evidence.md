# ADR 0004: Scenario-Bounded Releases And Artifact Evidence

## Status

Accepted existing direction, recorded on 2026-09-20 from the release contract at
main `0bd68804` and closed [REL-SCOPE-01](https://github.com/NanoNative/javan/issues/103).
This is not approval to publish a new release.

## Context

A compiler can pass tests in its checkout while its packaged binary is incomplete.
Likewise, an inventoried JDK class or a platform smoke pass can be mistaken for native
support. The first release needs a finite claim and evidence from the artifact users receive.

## Decision

Claim only the documented scenarios on Linux x64, Linux ARM64 and macOS ARM64.
Prove each extracted host-native package through its own executable, self-host, ABI,
acceptance and sanitizer checks. Keep unsupported scope explicit. Reuse the same proof
commands in local, PR and release workflows at their documented verification depths.

The [release spec](../specs/release.md) owns exact acceptance and publication behavior.
Snapshots update GitHub Packages on main; final tags/releases use manual publication.
Maven Central and Homebrew publication remain disabled. Artifact rehearsal is separate
from release dispatch because `dry_run=true` still performs GitHub publication.

## Alternatives And Consequences

- Waiting for complete JDK support would turn a useful bounded release into an unbounded
  compatibility project. Broader support follows demonstrated programs and regression proof.
- Platform smoke alone cannot certify a native archive, ABI or memory behavior.
- Emulating an architecture is not the normal self-host release proof; use native runners.
- Rehearsal adds work, but tests the actual archive without publishing it. Historical passes
  and issue closure never replace fresh evidence for the selected candidate.
