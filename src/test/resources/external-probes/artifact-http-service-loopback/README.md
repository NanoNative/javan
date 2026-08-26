# External HTTP Service Probe

Builds a small loopback HTTP service against the currently pinned bundled handler artifact and
compiles it to a native executable with `javan`.

This is external compatibility smoke only. The durable compiler regression for this shape lives in
the generic Javan test line and does not name the upstream project.

The probe starts a native HTTP server, invokes an `HttpHandler` supplied by the dependency jar,
makes a loopback request, prints the response, and stops the server cleanly.

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
200
external-pong
```
