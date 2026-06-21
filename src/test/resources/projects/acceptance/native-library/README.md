# Native Library

Builds a native static library without `Main.main`, exports Java methods through the C ABI,
and generates C/Rust/Go/Python bindings. The sample covers primitive, `String`, and
`byte[]` pointer/length ownership, including null `String` input, empty and invalid
`byte[]` input, retained Java references to copied C inputs, and structured last-error
fields.

```sh
../../dist/javan build . --library --format static \
  --export com.acme.Math.add \
  --export com.acme.Text.greet \
  --export com.acme.Bytes.duplicate \
  --export com.acme.Store.rememberString \
  --export com.acme.Store.lastString \
  --export com.acme.Failures.failInt \
  --bindings c,rust,go,python
cc caller.c .javan/dist/libnative-library.a -o native-library-caller
JAVAN_HEAP_LIMIT_BYTES=2048 ./native-library-caller
```

Generated files:

- `.javan/dist/libnative-library.a`
- `.javan/dist/bindings/c/native-library.h`
- `.javan/reports/library-build.md`
- `.javan/reports/deduplication-plan.md`

Failure handling:

- generated exports return safe zero/null/default values for caught Javan runtime panics
- callers inspect `javan_last_error()` or borrowed structured `javan_last_error_*`
  fields and clear them with `javan_clear_error()`
- C callers can use `javan_try_*` wrappers for owned `JavanResult` diagnostics and
  release them with `javan_result_free()`
