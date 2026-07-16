# External Pair Accessor Probe

Builds a tiny app against the currently pinned published pair-helper artifact and compiles it to a
native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

By default the script uses the pinned Maven-cache jar.
Override with `TYPEMAP_JAR=/path/to/type-map.jar`.

```sh
./build-example.sh
```
