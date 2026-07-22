# Javan Maven Plugin

This thin adapter runs the installed `javan` executable after Maven has produced
`target/classes`:

```xml
<plugin>
  <groupId>dev.javan</groupId>
  <artifactId>javan-maven-plugin</artifactId>
  <version>2026.6.14</version>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>
  </executions>
</plugin>
```

Use `-Djavan.executable=/path/to/javan` when the binary is not on `PATH`.

The `javan:build` goal uses the same inputs and produces the native artifact under
the project's `.javan` output directory.

The `javan:run` goal builds and executes the native application with inherited
standard output and error streams.

The `javan:test` goal delegates project test execution to Javan’s public test
command and fails the Maven lifecycle on a nonzero result.
