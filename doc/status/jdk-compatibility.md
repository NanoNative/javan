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

- scanned java: `25.0.1`
- scanned JDK: `JDK25`
- project classfile majors: `[69]`
- JDK classfile majors: `[53, 55, 61, 65, 69]`
- JDK modules: `84`

## Inventory Totals

| item | count |
| --- | ---: |
| classes | 32482 |
| fields | 118632 |
| constructors | 35209 |
| methods | 232677 |

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
| support rows | 108 |
| pass rows | 107 |
| scoped rows | 0 |
| target rows | 1 |
| rejected rows | 0 |
| accounted rows | 107 |
| unaccounted rows | 1 |
| exact supported JDK callable classes | 457 |
| exact supported JDK constructors | 678 |
| exact supported JDK methods | 290 |
| exact supported JDK callables | 968 / 267886 (0.3%) |
| exact explicit rejected JDK callables | 5035 |
| exact done JDK callables | 6003 / 267886 (2.2%) |
| exact unknown JDK callables | 261883 |
| exact supported JDK callables left | 266918 |
| flow-qualified reachable current-thread lifecycle rejects | 0 |
| flow-qualified unreachable current-thread lifecycle rejects | 0 |
| flow-qualified reachable thread-builder receiver-shape rejects | 0 |
| flow-qualified unreachable thread-builder receiver-shape rejects | 0 |
| flow-qualified reachable virtual-thread factory-shape rejects | 0 |
| flow-qualified unreachable virtual-thread factory-shape rejects | 0 |
| flow-qualified reachable executor receiver-shape rejects | 0 |
| flow-qualified unreachable executor receiver-shape rejects | 0 |
| flow-qualified rejected JDK call shapes total | 0 |

Release-gated JDKs must report:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0
```

The exact supported and done JDK callable counts above are lower-bound progress signals.
The current explicit rejected callable set now includes deterministic forbidden APIs plus
exact verifier-backed monitor/concurrency rejects such as `Object.wait/notify`,
unsupported `Executors` single/cached pool factories, `InheritableThreadLocal.<init>()`,
the deliberate `jdk.jfr.*` owner family, and `sun.misc.Unsafe`.
Flow-qualified rejected JDK call shapes above are diagnostic-shape accounting only.
They are tracked separately because they depend on receiver or call-flow facts rather than raw member inventory.
Unknown callables still include everything not yet counted as supported or explicitly rejected,
so this is not a full JDK completion claim.

Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.
New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.

Current full-first-JDK progress remains `0.0%` for the strict release gate because
explicit rejected callable-member coverage is still incomplete and unknown callables are
not zero.
