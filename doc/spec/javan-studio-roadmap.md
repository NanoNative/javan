# Javan Studio Roadmap

Status: flagship roadmap track. This file describes planned behavior and does not claim
current implementation.

## Goal

Build Javan Studio as a professional visual app builder using JavanUI itself. Studio must
create Java-native apps, Railix flows, web frontend/backend projects, desktop apps, and
native libraries from a versioned `AppModel`.

This is a dogfood track: JavanUI must become production-grade enough to host Studio, and
Studio must be buildable by `javan`.

## Product Principles

- Javan Studio is built with JavanUI.
- JavanUI must be production-grade enough to host Studio before Studio is called real.
- A versioned `AppModel` is the structural source of truth.
- Generated output is normal Java, Railix code, DTO/schema code, and target-specific
  project files.
- Business logic remains editable Java.
- No reflection, no runtime scanning, and no hidden editor-only behavior.
- Generated code must be deterministic and reviewable.
- Studio must show the same reports that the CLI writes, not infer a second truth.
- Studio must be buildable by `javan` and display its own `javan` reports.

## JavanUI Foundation

JavanUI is the production UI layer required to host Studio.

Required foundation:

- retained versioned component model
- deterministic rendering model
- state binding
- action binding
- layout engine with responsive constraints
- accessibility metadata and checks
- keyboard navigation
- focus management
- resource management
- preview/runtime separation
- native desktop target first
- web target later only when the shared model stays deterministic

Non-goals for JavanUI:

- no reflection-based component discovery
- no runtime classpath scanning
- no editor-only widgets that cannot be represented in the model
- no hidden UI state that cannot round-trip through the `AppModel`

## AppModel

`AppModel` is the versioned source of truth for Studio projects.

It must include:

- model version
- project identity
- modules
- Java packages
- UI trees
- Railix flow graphs
- DTO/schema definitions
- resources
- actions and bindings
- target exports
- build profiles
- accessibility metadata
- report references

Compatibility rules:

- every model version has deterministic migration rules
- unsupported future model versions fail clearly
- migrations are tested one version step at a time
- generated output is reproducible from model plus source inputs
- business logic source remains user-owned Java and is not overwritten blindly

## Generated Output

Studio may generate:

- normal Java source
- Railix flow code
- DTO/schema code
- JavanUI view code
- target-specific project files
- native app project layouts
- web frontend/backend project layouts
- backend service project layouts
- native library export configuration

Generated output must stay ordinary enough for build tools and IDEs. The editor must not
hide application behavior in private Studio-only state.

## Report Visualization

Studio must visualize `javan` reports:

- reachability
- build metrics
- safety warnings
- readable exceptions
- dependency usage
- optimizations
- native readiness
- accessibility

Report panels should link back to source locations, model nodes, flow nodes, UI nodes, and
generated outputs when mappings are available.

## Studio Shell

Studio must include:

- workspace shell
- project tree
- command palette
- diagnostics panel
- report viewer
- flow editor
- UI editor
- inspector
- preview surface
- build center

The shell is a workbench, not a marketing page. It should be dense, predictable, keyboard
friendly, and suitable for repeated professional use.

## Railix Flow Editor

The flow editor must support Railix-style flows:

```text
actor -> validate -> map -> branch -> side effect -> terminal result
```

Initial node families:

- actor/input
- validation
- mapping
- branching
- side effect
- error mapping
- terminal success
- terminal failure

Rules:

- flow graph is stored in `AppModel`
- generated flow code is ordinary Java/Railix code
- business logic hooks remain editable Java
- side effects are explicit
- validation and mapping nodes generate deterministic code
- unsupported node combinations fail at generation time

## UI Editor

The UI editor must support:

- responsive layouts
- accessibility checks
- state binding
- action binding
- resource management
- preview states
- component inspector
- generated JavanUI code

The editor must be able to round-trip model changes without corrupting user Java code.

## UI-To-Flow Binding

Bindings connect UI events and state to Railix flows.

Required binding shape:

- event source
- input mapping
- validation path
- flow call
- success mapping
- failure mapping
- loading state
- accessibility feedback

Bindings are part of the `AppModel`; generated code is deterministic Java.

## Export Targets

Planned export targets:

- native desktop app
- web frontend plus backend separation
- backend service
- native shared/static library with bindings later

Every target needs a deterministic project generator and one-scenario tests for generated
files, build commands, reports, and negative cases.

## Dogfood Gate

Studio is not accepted as real until:

- Studio is built with JavanUI
- Studio can be built by `javan`
- Studio displays its own `javan` reports
- Studio can open its own `AppModel`
- Studio can regenerate its own stable generated code
- Studio can show native-readiness diagnostics for itself
- generated Studio output passes the same compatibility gates as other apps

This is the part where the tool eats its own cooking. Politely, but thoroughly.

## Execution Order

1. JavanUI production foundation.
2. Studio shell.
3. Versioned `AppModel`.
4. Railix flow editor.
5. UI editor.
6. UI-to-flow binding.
7. Multi-target export.
8. Full report/build center.
9. Self-build and dogfooding.
10. Professional editor features.

## Independent Work Tracks

Studio should be developed in separate feature contexts until each slice is ready to
migrate or link:

- JavanUI foundation
- Studio shell
- AppModel schema and migration
- Railix flow editor
- UI editor
- target generators
- report/build center
- accessibility analyzer
- dogfood/self-build harness

Each track gets its own context file, public-entrypoint tests, negative tests, and explicit
integration contract. See [independent-workspaces.md](independent-workspaces.md).
