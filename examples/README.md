# javan Examples

The examples are public-entrypoint acceptance projects for the supported native subset.
`scripts/acceptance.sh` builds supported app examples, runs the generated classes on the
JVM, runs the native executable, and compares stdout exactly.

Negative examples prove deterministic rejection:

- `no-main`
- `multiple-main`
- `unsupported-reflection`

TypeMap and Nano examples are optional compatibility probes until their external inputs are
pinned for remote CI. When the local artifacts are unavailable, the acceptance runner skips
them with an explicit reason.
