# Testing Policy

`mvn verify` enforces:

- line coverage >= 95%
- branch coverage >= 90%

The enforced JaCoCo gate is not a whole-repository quality number yet. It is scoped to
deterministic compiler-core behavior that runs inside the Maven test JVM:

- reachability
- static verification
- C code generation
- native linker success/failure handling
- diagnostics
- compatibility bytecode support classification

The following areas are covered mainly by public-entrypoint integration tests that spawn
child JVMs or native binaries. The Maven JaCoCo agent does not count those child-process
paths yet, so they are excluded from the numeric gate until child JVM/native coverage is
captured honestly:

- build-tool invocation adapters
- host filesystem and process adapters
- project detection permutations
- raw JVM classfile parser switch tables
- compatibility inventory readers and report renderers
- CLI text routing
- pure data records

Next gate: run child JVMs with JaCoCo agent output merged into the main report, then remove
each exclusion instead of lowering the threshold.

## Test Shape

Every test checks exactly one assumption, scenario, or case.

Use shared setup when it keeps test projects readable, but split unrelated expectations into
separate tests. A failing test name should identify the broken promise without reading a
large assertion bundle.

Required behavior coverage for feature slices:

- one success case per supported shape
- one negative case per unsupported reachable shape
- one report-content case per generated report contract
- one public-entrypoint case for user-visible behavior
- one regression case per fixed bug

Feature labs and agent work follow the same rule before migration into the main suite. See
[feature-lab-workflow.md](feature-lab-workflow.md).
