# External Static Duration Probe

Builds a small app against the currently pinned published duration-helper artifact and compiles it
to a native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe intentionally stays on one helper call. It does not start a service graph and does not
make any broader framework support claim.

By default the script resolves the pinned artifact from `probe.properties` through
`JAVAN_MAVEN_REPO` or `~/.m2/repository`.

Override with one of:

```sh
JAVAN_PROBE_ARTIFACT=/path/to/dependency.jar ./build-example.sh
JAVAN_PROBE_CLASSPATH=/path/to/classpath-entry ./build-example.sh
```

Run:

```sh
./build-example.sh
```

Expected output:

```text
1m 5s
```
