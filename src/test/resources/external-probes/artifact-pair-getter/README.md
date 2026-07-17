# External Pair Accessor Probe

Builds a tiny app against the currently pinned published pair-helper artifact and compiles it to a
native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

By default the script resolves the pinned artifact from `probe.properties` through
`JAVAN_MAVEN_REPO` or `~/.m2/repository`.
Override with `JAVAN_PROBE_ARTIFACT=/path/to/dependency.jar`.

```sh
./build-example.sh
```
