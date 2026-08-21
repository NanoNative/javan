# JDK Compatibility

`javan` reads classfile versions directly from `.class` files. Users should not need
to pass a Java version for supported classfiles; the compiler either understands the
bytecode pattern or rejects it before native code generation.

| JDK | Class file major | Release-gate status |
| --- | ---: | --- |
| 21 | 65 | planned matrix target |
| 22 | 66 | planned matrix target |
| 23 | 67 | planned matrix target |
| 24 | 68 | planned matrix target |
| 25 | 69 | integrated local gate |

## Active Scan

- scanned JDK: `JDK25`
- project classfile majors: `[69]`
- JDK classfile majors: `[52, 69]`
- JDK modules: `69`

## Inventory Totals

| item | count |
| --- | ---: |
| classes | 27045 |
| fields | 108599 |
| constructors | 29945 |
| methods | 204648 |

## Inventory Is Not Support

Inventory means `javan` can see the JDK surface: modules, packages, classes,
methods, fields, constructors, descriptors, flags, attributes, constant-pool
tags, bootstrap methods, synthetic members, deprecated markers, and preview
markers.

Native support means a reachable API or bytecode variant is either implemented
or deliberately rejected with a clear diagnostic. A release-gated JDK must have
no unknown leftovers.

## Support Accounting

Inventory is implemented. Exact supported callable-member accounting is implemented as a
lower-bound progress signal. Exact explicit rejected and unknown callable counts are now
reported as a baseline, but full member-by-member rejection accounting is still planned.

Current support ledger for the active JDK 25 evidence set:

| Measure | Count |
| --- | ---: |
| support rows | 308 |
| pass rows | 308 |
| scoped rows | 0 |
| target rows | 0 |
| rejected rows | 0 |
| accounted rows | 308 |
| unaccounted rows | 0 |
| exact supported JDK callable classes | 146 |
| exact supported JDK constructors | 171 |
| exact supported JDK methods | 754 |
| exact supported JDK callables | 925 / 234593 (0.3%) |
| exact explicit rejected JDK callables | 142104 |
| exact done JDK callables | 143029 / 234593 (60.9%) |
| exact unknown JDK callables | 91567 |
| exact supported JDK callables left | 233668 |
| flow-qualified reachable current-thread lifecycle rejects | 0 |
| flow-qualified unreachable current-thread lifecycle rejects | 0 |
| flow-qualified reachable thread-builder receiver-shape rejects | 0 |
| flow-qualified unreachable thread-builder receiver-shape rejects | 0 |
| flow-qualified reachable virtual-thread factory-shape rejects | 0 |
| flow-qualified unreachable virtual-thread factory-shape rejects | 0 |
| flow-qualified reachable executor receiver-shape rejects | 0 |
| flow-qualified unreachable executor receiver-shape rejects | 0 |
| flow-qualified rejected JDK call shapes total | 0 |

This ledger excludes external example or library probes. Those stay in `doc/status/real-project-readiness.md`
and never define a supported JDK member count.

Release-gated JDKs must report:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0
```

The exact supported and done JDK callable counts above are lower-bound progress signals.
Flow-qualified rejected JDK call shapes above are diagnostic-shape accounting only.
They are tracked separately because they depend on receiver or call-flow facts rather than raw member inventory.
Unknown callables still include everything not yet counted as supported or explicitly rejected,
so this is not a full JDK completion claim.

Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.
New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.
