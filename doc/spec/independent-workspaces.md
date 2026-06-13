# Independent Workspaces

Status: process policy. This file describes how large standalone tracks can be split across
repositories or folders so other Codex instances can work independently.

## Goal

Large features such as Java SDK wrapping, Javan Studio, JavanUI, Railix integration,
plugins, installers, and IDE support should not all be implemented directly inside the core
compiler tree at once.

Use independent repositories or local folders with context files when a feature has its own
architecture, tests, and release shape. Migrate only stable integration contracts into
`javan`.

## Workspace Options

Recommended shapes:

```text
<workspace>/javan/                 core compiler and CLI
<workspace>/javan-ui/              JavanUI runtime and components
<workspace>/javan-studio/          visual app builder
<workspace>/javan-java-sdk/        JDK wrapper/layout experiments
<workspace>/javan-maven-plugin/    Maven plugin
<workspace>/javan-gradle-plugin/   Gradle plugin
<workspace>/javan-jetbrains/       optional JetBrains plugin
<workspace>/javan-homebrew/        formula and release packaging
```

If a separate repository is too early, use ignored local labs:

```text
<workspace>/javan/.javan/labs/javan-ui/
<workspace>/javan/.javan/labs/studio-shell/
<workspace>/javan/.javan/labs/app-model/
```

Labs are temporary. Repositories are for durable standalone products.

## Context Files

Every independent workspace must have a short context file:

```text
AGENTS.md
doc/spec/context.md
doc/spec/integration-contract.md
doc/spec/testing.md
```

The context file must say:

- owner track
- purpose
- current status
- public entrypoints
- generated outputs
- integration contract with `javan`
- unsupported cases
- verification commands
- one-scenario test policy
- what must not be implemented in that workspace

Keep context files short. Deep design belongs in `doc/spec/`.

## Integration Contract

Every standalone workspace must define how it touches `javan`.

Examples:

- JavanUI provides Java APIs, generated code shape, runtime assets, and accessibility reports.
- Javan Studio consumes and produces `AppModel`, Java source, Railix code, and `javan` reports.
- Java SDK wrapper provides launcher layout, toolchain metadata, and `javac`/`java`
  delegation contracts.
- Maven plugin invokes `javan` CLI and consumes reports.
- Gradle plugin invokes `javan` CLI and consumes reports.
- JetBrains plugin reads stable report JSON and never reimplements compiler analysis.

No workspace should silently depend on private core internals. If it needs a new core
contract, add that contract deliberately.

## Suggested Track Ownership

Independent tracks:

- `javan-core`: compiler, CLI, reports, native backend, compatibility gates
- `javan-java-sdk`: JDK-like wrapper layout, launcher scripts, SDK metadata
- `javan-ui`: component model, renderer, layout, accessibility, event/state binding
- `javan-studio`: shell, editors, build center, report visualization, dogfood app
- `javan-railix`: flow graph model and Java/Railix generation contracts
- `javan-maven-plugin`: Maven lifecycle integration
- `javan-gradle-plugin`: Gradle task integration
- `javan-homebrew`: formula, bottles, archive smoke
- `javan-jetbrains`: optional IDE plugin reading report JSON

Agents should receive one track and one slice, not a whole empire.

## Migration Rules

Before moving work into `javan`:

- define the public contract
- add one-scenario tests in the source workspace
- add one-scenario integration tests in `javan`
- prove deterministic generated outputs
- document unsupported paths
- remove lab-only scaffolding
- avoid broad refactors bundled with the migration

The core repo should accept contracts and stable slices, not half-finished product trees.

## Verification Policy

Each workspace must have:

- focused unit tests where useful
- public-entrypoint integration tests
- negative tests
- deterministic report/output tests
- no network dependency in normal verification
- documented Docker/JDK matrix when platform behavior is claimed

Every test checks exactly one assumption, scenario, or case.

## When To Split A Repository

Split into a separate repository when:

- the feature has its own release artifact
- the test/runtime dependencies would bloat `javan-core`
- the feature has a different platform matrix
- the feature has a separate user workflow
- multiple Codex instances need long-running parallel ownership

Keep it as a lab when:

- the interface is still unknown
- the implementation is throwaway
- the work is a short isolated experiment
- no durable API exists yet

## First Recommended Splits

1. `javan-ui`: production UI foundation.
2. `javan-studio`: shell and `AppModel` once JavanUI can render a real app.
3. `javan-java-sdk`: wrapper SDK layout and launcher experiments.
4. `javan-maven-plugin` and `javan-gradle-plugin`: build integrations after CLI reports
   stabilize.

Core `javan` remains the compiler, CLI, reports, compatibility engine, and native backend.
