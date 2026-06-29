# javan Support Matrix

This matrix tracks named javan/JDK behavior scenarios. It is not a claim that every class or method in the scanned JDK is native-supported.

## Current Counts

| Measure | Count |
| --- | ---: |
| rows | 108 |
| done (`pass` + `rejected`) | 107 |
| left (`scoped` + `target`) | 1 |
| pass | 107 |
| scoped | 0 |
| target | 1 |
| rejected | 0 |

Status mapping:

| Matrix status | Roadmap status | Meaning |
| --- | --- | --- |
| `pass` | Done | Implemented and tested for the named scenario. |
| `scoped` | Partial | Supported subset exists; unsupported shapes must reject clearly. |
| `target` | Planned | Tracked as a goal, not claimed as supported yet. |
| `rejected` | Dismissed | Rejected by design for native output. |

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
| `boxed-primitive-gc` | pass |
| `boolean-basic` | pass |
| `static-fields` | pass |
| `string-concat` | pass |
| `exception-panic` | pass |
| `try-catch` | pass |
| `try-finally` | target |
| `enum-basic` | pass |
| `enum-ordinal` | pass |
| `enum-values` | pass |
| `enum-switch` | pass |
| `enum-value-of` | pass |
| `interface-dispatch` | pass |
| `polymorphic-virtual` | pass |
| `interface-polymorphic` | pass |
| `string-intrinsics` | pass |
| `non-ascii-string-semantic-rejection` | pass |
| `operand-object-compare-temporary-root` | pass |
| `operand-field-load-temporary-root` | pass |
| `operand-chained-field-load-temporary-root` | pass |
| `operand-chained-call-receiver-temporary-root` | pass |
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
| `library-binding-ownership-smoke` | pass |
| `library-retained-input-ownership` | pass |
| `library-last-error-abi` | pass |
| `library-c-result-wrapper-success` | pass |
| `library-c-result-wrapper-error` | pass |
| `library-c-result-wrapper-null-out` | pass |
| `library-c-result-wrapper-free` | pass |
| `library-rust-result-wrapper` | pass |
| `library-go-result-wrapper` | pass |
| `library-python-result-wrapper` | pass |
| `library-null-string-input` | pass |
| `library-empty-byte-array-input` | pass |
| `library-negative-byte-array-rejection` | pass |
| `library-structured-last-error-fields` | pass |
| `deduplication-plan` | pass |
| `hashmap-realloc-gc` | pass |
| `list-of-varargs-gc` | pass |
| `owned-buffer-realloc-validation` | pass |
| `stringbuilder-setlength-overflow-panic` | pass |
| `network-address-runtime` | pass |
| `network-tcp-client-socket` | pass |
| `network-tcp-server-socket` | pass |
| `network-tcp-socket-stream-io` | pass |
| `network-http-client-get-string` | pass |
| `network-http-client-post-string-byte-array` | pass |
| `network-http-client-put-byte-array` | pass |
| `platform-thread-construction` | pass |
| `platform-thread-empty-start-join` | pass |
| `platform-thread-runnable-start-join-single-threaded` | pass |
| `platform-thread-current-interrupt-state` | pass |
| `platform-thread-current-thread-root-gc-pressure` | pass |
| `platform-thread-runnable-target-root-gc-pressure` | pass |
| `platform-thread-current-thread-inventory` | pass |
| `platform-thread-live-root-registry` | pass |
| `platform-thread-finished-thread-reclaim` | pass |
| `platform-thread-sleep-uninterrupted` | pass |
| `platform-thread-sleep-entry-interrupted-same-method-catch` | pass |
| `platform-thread-join-entry-interrupted-same-method-catch` | pass |
| `platform-thread-current-thread-start-build-reject` | pass |
| `platform-thread-current-thread-join-build-reject` | pass |
| `platform-thread-duplicate-start-build-reject` | pass |
| `network-socket-rejection` | pass |
| `network-http-rejection` | pass |
| `network-runtime-feature-reporting` | pass |
| `typemap-pair` | pass |
| `nano-metric` | pass |
| `nano-duration` | pass |

`pass` means covered by the current deterministic verification suite for the active JDK.
`scoped` means a supported subset exists and unsupported shapes must be rejected clearly.
`target` means tracked for the milestone but not claimed as native-supported by this matrix.
JDK coverage accounting is planned: `done = supported variants + rejected variants`, and unknown leftovers must be `0` for a release-gated JDK.
