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
lower-bound progress signal. Full supported/rejected/unknown JDK API variant accounting
is still planned.

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
| exact supported JDK callable classes | 459 |
| exact supported JDK constructors | 778 |
| exact supported JDK methods | 287 |
| exact supported JDK callables | 1065 / 267886 (0.3%) |

Release-gated JDKs must report:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0
```

Compatibility reports are generated under `.javan/reports`, `.javan/jdk-inventory`, and `.javan/bytecode-patterns`.
New opcodes, constant-pool tags, attributes, and bootstrap patterns must be classified before native code generation accepts them.

Current full-first-JDK progress remains `0.0%` for the actual release gate because
supported/rejected/unknown accounting is not complete yet. The exact supported callable
ledger above is a lower-bound progress signal, not a complete JDK support claim.
