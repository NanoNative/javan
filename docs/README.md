# JavaN Documentation

Start with the [roadmap](roadmap.md) for what to do next and the
[compiler contract](specs/compiler.md) for what JavaN promises to users.

| Location | Owns |
| --- | --- |
| [Root README](../README.md) | Introduction, installation and first commands. |
| [Specifications](#specifications) | Observable behavior, boundaries, requirement IDs and acceptance. |
| [Decisions](#decisions) | Architectural choices, alternatives and consequences. |
| [Roadmap](roadmap.md) | Delivery order, dependencies, status and unresolved decisions. |
| [Verification history](verification.md) | Dated evidence; never a claim that current main passed. |
| [Generated support matrix](status/support-matrix.md) and [JDK inventory](status/jdk-compatibility.md) | Compiler-owned accounting; inventory does not mean native support. |
| [External readiness](status/real-project-readiness.md) | Evidence for pinned dependency probes, separate from language support. |

## Specifications

Read the human-review and acceptance sections before changing behavior.
Detailed existing contracts remain with their owning capability.

| User question | Owner |
| --- | --- |
| What can I compile, and what happens when it fails? | [Compiler](specs/compiler.md) |
| Which analysis can remove work safely? | [Analysis and optimization](specs/analysis-and-optimization.md) |
| What keeps objects alive and releases native resources? | [Memory and runtime](specs/memory-runtime-correctness.md) |
| Which threading behavior is proven or still proposed? | [Concurrency](specs/concurrency-runtime.md) |
| How do Java and C exchange values and failures? | [Native ABI](specs/native-abi.md) |
| How do I understand compiler and runtime failures? | [Diagnostics](specs/diagnostics.md) |
| How are dependencies, resources and licenses accounted for? | [Dependencies and licenses](specs/dependency-and-license-reports.md) |
| Which runtime features enter the binary? | [Runtime selection](specs/runtime-feature-selection.md) |
| How do JavaN, my JDK and build tool work together? | [Compiler inputs](specs/compiler.md#build-inputs-and-outputs), [toolchains](specs/toolchains.md) |
| What must pass before shipping? | [Release](specs/release.md) |
| Which tests run locally and on each native host? | [Testing](specs/testing.md) |
| What do examples and containers prove? | [Examples](specs/examples-and-test-projects.md), [containers](specs/container-images.md) |

## Decisions

- [ADR 0001](adr/0001-core-repo-boundary.md): core compiler repository boundary.
- [ADR 0002](adr/0002-documentation-layout.md): one owner for behavior, decisions, progress and evidence.
- [ADR 0003](adr/0003-static-native-compilation.md): closed-world native compilation with runtime-owned Java state.
- [ADR 0004](adr/0004-release-evidence.md): scenario-bounded releases proven through extracted artifacts.

## Updating These Documents

Change requirements and acceptance in the owning spec first. Keep IDs stable; describe an
open choice there and link the affected roadmap milestone. Record material architectural
decisions in an ADR with the actual source of acceptance. A proposal is not an accepted decision.

Use `Done`, `Partial`, `In progress`, `Planned`, `Blocked` or `Dismissed` for delivery status.
`Done` requires linked public-entrypoint evidence for the named scope. Test source proves
that a check exists; a dated passing run proves it ran. Neither grants broader compatibility.

Maven refreshes the tracked compatibility files automatically; see
[the generation contract](specs/testing.md#generated-compatibility-status).
Do not hand-edit generated counts. The CLI's existing `doc/status/*` outputs remain stable;
Maven copies those outputs into this repository's `docs/status/*` owner.

Studio, UI, build plugins and other sibling products keep their own specifications.
Their integration boundary is [ADR 0001](adr/0001-core-repo-boundary.md).
