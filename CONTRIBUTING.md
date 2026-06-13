# Contributing to javan

javan is a minimal native-first Java toolchain. Contributions should keep Java source normal, native behavior deterministic, and unsupported bytecode explicit.

## Before You Start

- Search existing issues and pull requests.
- Keep changes small and tied to one user-visible behavior.
- Do not add dependencies unless the standard library or existing build cannot solve the problem cleanly.
- Do not add or change license files, headers, or license terms unless maintainers explicitly request it.

## Development Setup

- Use Java 25 or newer.
- Use Maven for local verification.
- Install a native C toolchain when testing generated binaries.
- Install GraalVM `native-image` when running the full release gate.

```sh
mvn verify
```

Full local release gate:

```sh
sh scripts/verify-release.sh
```

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
