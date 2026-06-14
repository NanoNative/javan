# javan Support Matrix

This matrix tracks named javan/JDK behavior scenarios. It is not a claim that every
class or method in the scanned JDK is native-supported.

| feature | JDK25 |
| --- | --- |
| `println-string` | pass |
| `println-int` | pass |
| `println-boolean` | pass |
| `println-long` | pass |
| `println-float` | pass |
| `println-double` | pass |
| `int-arithmetic` | pass |
| `int-bitwise-and` | pass |
| `long-basic` | pass |
| `float-double` | pass |
| `boolean-basic` | pass |
| `static-fields` | pass |
| `string-concat` | pass |
| `exception-panic` | pass |
| `try-catch` | scoped |
| `enum-basic` | scoped |
| `enum-ordinal` | scoped |
| `enum-values` | scoped |
| `enum-switch` | scoped |
| `enum-value-of` | rejected |
| `interface-dispatch` | scoped |
| `polymorphic-virtual` | scoped |
| `interface-polymorphic` | scoped |
| `string-intrinsics` | scoped |
| `jdk-intrinsics-math-abs-min-max` | pass |
| `jdk-intrinsics-objects-require-non-null` | pass |
| `jdk-intrinsics-system-time` | pass |
| `jdk-intrinsics-system-arraycopy` | pass |
| `jdk-intrinsics-arrays-copy-of` | pass |
| `jdk-intrinsics-number-to-string` | pass |
| `if-else` | pass |
| `while-loop` | pass |
| `records-basic` | pass |
| `object-fields` | pass |
| `object-array` | pass |
| `int-array` | pass |
| `long-array` | pass |
| `primitive-array-variants` | pass |
| `object-array-clone` | pass |
| `int-array-clone` | pass |
| `main-args` | pass |
| `jar-output` | pass |
| `jar-main-manifest` | pass |
| `resource-file-copy` | pass |
| `resource-stale-removal` | pass |
| `native-resource-distribution` | pass |
| `library-static-int-export` | pass |
| `library-string-export` | pass |
| `library-byte-array-export` | pass |
| `library-without-main` | pass |
| `library-c-bindings` | pass |
| `library-rust-binding-smoke` | pass |
| `library-go-binding-smoke` | pass |
| `library-python-ctypes-smoke` | pass |
| `deduplication-plan` | pass |
| `typemap-pair` | optional-probe |
| `nano-metric` | optional-probe |

`pass` means covered by the current deterministic verification suite for the active JDK.
`scoped` means a supported subset exists and unsupported shapes must be rejected clearly.
`optional-probe` means the local probe exists but is not release-gated native support yet.
`target` means tracked for a milestone but not claimed as native-supported by this matrix.
`rejected` means javan detects the feature deterministically and stops before codegen.
JDK coverage accounting is planned: `done = supported variants + rejected variants`, and
unknown leftovers must be `0` for a release-gated JDK.
