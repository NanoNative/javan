# Nano MetricUpdate Probe

Builds a tiny app against local Nano classes and compiles it to a native executable with `javan`.

By default the script uses `../../../nano/target/classes`.
Override with `NANO_CLASSES=/path/to/nano/classes`.

Nano must already have compiled classes. If local Nano packaging fails because its TypeMap dependency is out of sync, use the existing `target/classes` from the last successful Nano build or fix the Nano dependency first.

```sh
./build-example.sh
```
