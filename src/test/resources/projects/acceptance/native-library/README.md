# Native Library

Builds a native static library without `Main.main`, exports Java methods through the C ABI,
and generates C/Rust/Go/Python bindings. The sample covers primitive, `String`, and
`byte[]` pointer/length ownership.

```sh
../../dist/javan build . --library --format static \
  --export com.acme.Math.add \
  --export com.acme.Text.greet \
  --export com.acme.Bytes.duplicate \
  --bindings c,rust,go,python
cc caller.c .javan/dist/libnative-library.a -o native-library-caller
JAVAN_HEAP_LIMIT_BYTES=2048 ./native-library-caller
```

Generated files:

- `.javan/dist/libnative-library.a`
- `.javan/dist/bindings/c/native-library.h`
- `.javan/reports/library-build.md`
- `.javan/reports/deduplication-plan.md`
