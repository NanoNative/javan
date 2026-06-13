# Testing Policy

`mvn verify` enforces:

- line coverage >= 95%
- branch coverage >= 90%

The enforced JaCoCo gate is scoped to deterministic compiler-core behavior:

- reachability
- static verification
- C code generation
- native linker success/failure handling
- diagnostics
- compatibility bytecode support classification

The full JaCoCo report still includes every class. The following areas are covered by
public-entrypoint integration tests but excluded from the hard ratio gate until they can be
made deterministic across machines:

- build-tool invocation adapters
- host filesystem and process adapters
- project detection permutations
- raw JVM classfile parser switch tables
- compatibility inventory readers and report renderers
- CLI text routing
- pure data records

When one of those areas becomes stable enough to test exhaustively, remove the exclusion
instead of lowering the gate. The gate is a blade, not wall art.

## Test Shape

Every test checks exactly one assumption, scenario, or case.

Use shared setup when it keeps fixtures readable, but split unrelated expectations into
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
