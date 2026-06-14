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

The compatibility flow is:

1. Compile the target project with its normal Java build path.
2. Scan project and dependency classfiles.
3. Scan the active JDK runtime image through `jrt:/`.
4. Record classfile major versions, modules, packages, classes, constructors, fields, methods, descriptors, flags, attributes, constant-pool tags, bootstrap methods, synthetic members, deprecated markers, and preview markers.
5. Classify every opcode as `NATIVE_SUPPORTED`, `RECOGNIZED_REJECTED`, or `UNKNOWN_FATAL`.
6. Write deterministic reports under `.javan/reports`, `.javan/jdk-inventory`, `.javan/bytecode-patterns`, and `doc/`.

Unknown bytecode is fatal. Recognized but unsupported bytecode remains rejected until native lowering and tests are added.

## Inventory Is Not Support

Inventory means `javan` can see the JDK surface:

- class file major versions
- modules
- packages
- classes
- methods
- fields
- constructors
- descriptors
- flags
- deprecated, synthetic, and preview markers

Native support means a reachable API or bytecode variant is either implemented or
deliberately rejected with a clear diagnostic. A release-gated JDK must have no unknown
leftovers:

```text
done = supported variants + rejected variants
leftovers = unknown variants
leftovers must be 0
```

The current committed matrix tracks deterministic scenario support for JDK 25. Full
per-JDK API variant accounting is planned and will report supported, rejected, and
unknown counts separately.
