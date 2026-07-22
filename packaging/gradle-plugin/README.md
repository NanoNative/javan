# Javan Gradle Plugin

The thin `dev.javan` plugin adds `javanCheck`, `javanBuild`, `javanRun`, and `javanTest` to Java projects.
Both tasks depend on the `main` source set’s classes task and delegate to the
configured installed Javan executable:

```groovy
plugins {
    id 'java'
    id 'dev.javan' version '2026.6.14'
}
```

The `javanRun` task builds and executes the native application with inherited
standard output and error streams. Use `-Pjavan.executable=/path/to/javan` and
`-Pjavan.main=example.Main` when needed.

The `javanTest` task delegates project test execution to Javan’s public test command.
