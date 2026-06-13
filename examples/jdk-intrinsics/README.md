# jdk-intrinsics

Tracks narrow JDK method substitutions.

This example exercises integer `Math.abs`, `Math.min`, `Math.max`, `Objects.requireNonNull`,
`System.arraycopy`, `Arrays.copyOf`, `Integer.toString`, and `Long.toString`.
The test suite also covers long math intrinsics, time intrinsics, all supported `Arrays.copyOf`
array variants, and unsupported overload rejection.

```sh
dist/javan build examples/jdk-intrinsics
examples/jdk-intrinsics/.javan/bin/jdk-intrinsics
```
