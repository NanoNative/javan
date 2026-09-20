# ADR 0002: Documentation Layout

## Status

Accepted. Updated on 2026-09-20 by the user's request to adopt the specification,
decision and roadmap structure demonstrated in Railix. The original ADR ID is retained.

## Context

The repository accumulated public docs, status ledgers, process notes, and future-product
plans in one flat namespace. That makes it slow to understand what is a contract, what is
generated status, and what is just working policy.

## Decision

Documentation has one owner per kind of information:

- `docs/specs/` owns behavior, stable requirement IDs and acceptance scenarios.
- `docs/adr/` owns significant decisions, acceptance sources, alternatives and consequences.
- `docs/roadmap.md` owns delivery order, dependencies, progress and links to unresolved choices.
- `docs/verification.md` owns dated proof and measurements when they need a separate record.
- `docs/status/` owns generated compatibility accounting and the external-probe ledger.

The root README remains the public front door, not the full internal encyclopedia.

This replaces the earlier repository `doc/spec`, `doc/adr` and `doc/status` layout.
The existing public `javan compat` output paths remain `doc/status/*`; Maven's repository
refresh maps those generated files into `docs/status/*`. There is one tracked copy and
one canonical generator, with no user migration or duplicate report implementation.

The documentation index links to owners. It does not repeat their requirements. A closed
issue or historical green run is not fresh acceptance for a different release candidate.

## Consequences

- new docs must choose a purpose up front instead of landing in a flat pile
- status files can evolve quickly without being mistaken for stable API/spec contracts
- architecture rationale is easier to find than scattered roadmap prose
- Git and pull requests retain implementation history; verification records keep only
  evidence needed to assess a claim, with dates and scope.
- No new framework, language rule or product boundary is inherited from Railix.
