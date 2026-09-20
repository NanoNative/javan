# Contributing to javan

javan is a minimal native-first Java toolchain. Contributions should keep Java source normal, native behavior deterministic, and unsupported bytecode explicit.

## Before You Start

- Search existing issues and pull requests.
- Keep changes small and tied to one user-visible behavior.
- Do not add dependencies unless the standard library or existing build cannot solve the problem cleanly.
- Do not add or change license files, headers, or license terms unless maintainers explicitly request it.

## Development Setup

- Use Java 25. Compatibility verification rejects another feature release before it can
  rewrite versioned matrix keys.
- Use Maven for local verification. The canonical quick, standard, and full commands are in
  [Testing Policy](docs/specs/testing.md#local-verification).
- Install a native C toolchain when testing generated binaries or the full release gate.
- The release gate builds Javan through Javan's own native backend.

```sh
./mvnw -Pstandard verify
```

Every `verify` command also generates the active-JDK compatibility reports and refreshes the tracked
support matrix through the compiler's canonical report generator. If tracked status changed,
Maven writes the current files and fails once so the generated change can be reviewed; rerun
the same command to confirm it is stable. No separate report command or manual copy step is
required.

When a tested behavior becomes supported, register its named scenario with the appropriate
`pass(...)`, `scoped(...)`, or `target(...)` entry in
`CompatibilityReports.supportRows()` and cover that entry in `CompatibilityReportsTest`.
Do not edit `docs/status/support-matrix.md` or `docs/status/support-matrix.json` directly:
`mvn verify` generates both files from that canonical ledger. Commit the generated changes,
then rerun `mvn verify`; the second run must report that compatibility status is current.

The tracked JDK inventory describes Java 25 and is refreshed only on canonical Linux x64.
Other Java 25 environments produce active-JDK reports without rewriting that snapshot.
It is not pinned to a vendor or patch version. The complete source/destination and
stale-report behavior lives in [Testing Policy](docs/specs/testing.md#generated-compatibility-status).

Full local release gate:

```sh
sh .github/scripts/verify-release.sh
```

## Specifications And Decisions

Start with the [documentation map](docs/README.md) and [current roadmap](docs/roadmap.md).
Behavior and acceptance belong in `docs/specs/`; significant rationale belongs in `docs/adr/`.
Keep requirement and decision IDs stable. Mark a proposal or unresolved choice explicitly;
do not turn a passing implementation test into approval for a new product contract.
The roadmap links those requirements and owns delivery sequence. Historical measurements
belong in `docs/verification.md`, with their commit, command, scope and limitations.

Generated support files remain owned by `CompatibilityReports.supportRows()` and Maven
verification. Moving documentation does not change `javan compat`'s public output paths.

## Code Standards

- Prefer plain Java, small functions, immutable data, and explicit results.
- Do not use reflection or parallel streams.
- Public methods should not return `null`.
- Keep timestamps and generated output deterministic.
- Keep MongoDB, cloud, or service-specific behavior out of generic compiler and toolchain code.

## Tests

- Add a failing regression test before fixing a bug.
- Test behavior through public entrypoints such as CLI commands or generated artifacts.
- Cover success, unsupported input, invalid input, and repeat runs where reachable.
- Keep tests deterministic and parallel-safe.

## Pull Requests

- Explain the behavior change and why it belongs in javan.
- Include the commands you ran.
- Note intentionally uncovered behavior or skipped verification.
- Keep generated build output out of the pull request.
