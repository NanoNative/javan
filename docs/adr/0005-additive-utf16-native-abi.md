# ADR 0005: Additive UTF-16 Native String Interface

## Status

Accepted on 2026-11-19.

## Context

The existing native ABI passes strings as null-terminated UTF-8. It cannot represent an
embedded zero code unit or an isolated UTF-16 surrogate. Java `String` values may contain
both. Changing the existing representation would break callers and generated bindings.

## Decision

Keep ABI v2 and its existing symbols. Generate additional C result wrappers for exports
with string parameters or results. These wrappers transfer UTF-16 code units with explicit
lengths. They preserve null and empty strings as distinct values, validate structural
input, and follow the existing borrowed-input and owned-output conventions. The
[native ABI contract](../specs/native-abi.md) owns the exact types and failure rules.

## Consequences

C callers can transfer all Java string code units without changing existing callers. The
existing Rust, Go and Python wrappers continue to use the UTF-8 interface and do not yet
expose the additional code-unit interface. This decision does not require a particular
internal string representation or broaden dynamic class loading.
