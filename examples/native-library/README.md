# Native Library

Builds a native static library without `Main.main`, exports a Java method through the C ABI,
and generates C/Rust/Go/Python bindings.

```sh
../../dist/javan build . --kind staticlib --export com.acme.Math.add --bindings c,rust,go,python
```

Generated files:

- `.javan/dist/libnative-library.a`
- `.javan/dist/bindings/c/native-library.h`
- `.javan/reports/library-build.md`
- `.javan/reports/deduplication-plan.md`
