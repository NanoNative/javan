# External Nullable Record Probe

Builds a tiny app against the currently pinned published nullable-record helper artifact and
compiles it to a native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

By default the script uses the pinned Maven-cache jar. Override with either:

- `NANO_JAR=/path/to/nano.jar`
- `NANO_CLASSPATH=/path/to/dependency`
- `NANO_CLASSES=/path/to/compiled/classes`

```sh
./build-example.sh
```
