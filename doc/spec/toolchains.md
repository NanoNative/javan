# Toolchains

## Current Behavior

`javan` currently uses local tools:

- `javac` for plain Java compilation
- Maven or Maven wrapper for Maven projects
- Gradle or Gradle wrapper for Gradle projects
- `cc`, `clang`, or `gcc` for C linking
- GraalVM `native-image` only for building `javan` itself as a native executable

Read-only toolchain inspection is available now:

```sh
javan doctor
javan toolchain doctor
javan toolchain list
```

The first wrapper command is also available:

```sh
javan javac [javac args...]
```

It delegates to the host `javac` and preserves the compiler exit code, stdout, and stderr.

`doctor` reports the resolved `~/.javan` home, `java.home`, `java.version`, `javac`,
native C compiler, `native-image`, and global settings status. `toolchain list` reads
`~/.javan/toolchains/<id>/toolchain.toml` when present and never installs or mutates
global state.

## Gradle And Java 25

Gradle must be new enough to run on the active Java runtime. The official Gradle
compatibility matrix lists Java 25 support for running Gradle starting at Gradle 9.1.0:

https://docs.gradle.org/current/userguide/compatibility.html

If a machine has an older system Gradle and Java 25, use a project wrapper:

```sh
./gradlew wrapper --gradle-version=9.1.0
```

`javan` prefers `./gradlew` over `gradle`, so a compatible wrapper is the cleanest path.

## Production Direction

The production distribution should grow toward the JDK-like wrapper model in
[toolchain-distribution-roadmap.md](toolchain-distribution-roadmap.md).

Near-term commands:

- `javan toolchain doctor` (implemented as a read-only alias)
- `javan toolchain list` (implemented as read-only local inventory)
- `javan toolchain install jdk`
- `javan toolchain install c`
- `javan toolchain install gradle`
- deterministic project selection stored in `.javan/toolchain.lock.json`
- user-global installs and settings under `~/.javan`

Bundling should be optional and explicit. Silent global toolchain mutation is how build
systems learn to bite.
