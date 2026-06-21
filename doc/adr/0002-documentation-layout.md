# ADR 0002: Documentation Layout

## Status

Accepted

## Context

The repository accumulated public docs, status ledgers, process notes, and future-product
plans in one flat namespace. That makes it slow to understand what is a contract, what is
generated status, and what is just working policy.

## Decision

Documentation is grouped by purpose:

- `doc/status/` for support accounting and release-readiness tracking
- `doc/spec/` for stable core-product contracts
- `doc/process/` for working agreements and lab policies
- `doc/adr/` for architecture decisions

The root README remains the public front door, not the full internal encyclopedia.

## Consequences

- new docs must choose a purpose up front instead of landing in a flat pile
- status files can evolve quickly without being mistaken for stable API/spec contracts
- architecture rationale is easier to find than scattered roadmap prose
