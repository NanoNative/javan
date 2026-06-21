# ADR 0001: Core Repository Boundary

## Status

Accepted

## Context

`javan` is the core compiler/runtime/CLI repository, but adjacent product tracks also
exist in the workspace: Studio, UI, Maven/Gradle integrations, optional SDK wrapping,
Homebrew packaging, and cross-language experiments.

When all of those roadmaps live in the core repo, the documentation reads like one large
unbounded backlog instead of one shippable product.

## Decision

The `javan` repository owns only:

- compiler frontend and verifier
- IR and native backend
- runtime model and ABI
- CLI and report formats
- package/release/container verification contracts
- core compatibility accounting

Sibling-product details belong in sibling workspaces and repositories. The core repo may
reference those tracks, but it should not carry their full product roadmaps.

## Consequences

- core docs stay focused on the production `javan` binary
- plugin, Studio, UI, and optional SDK work can evolve without bloating the core repo
- integration happens through explicit contracts, not hidden internal coupling
