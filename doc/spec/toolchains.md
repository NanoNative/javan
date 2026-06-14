# Toolchains

## Current Behavior

`javan` currently uses local tools:

- `javac` for plain Java compilation
- Maven or Maven wrapper for Maven projects
- Gradle or Gradle wrapper for Gradle projects
- `cc`, `clang`, or `gcc` for C linking
- Javan's own bytecode -> IR -> C/native path for native output

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
native C compiler, and global settings status. `toolchain list` reads
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

The production distribution is binary-first. `javan` should work beside the user's
current JDK, Maven, Gradle, and IDE, then add plugins and installer support around the
same executable. See [binary-first-distribution.md](binary-first-distribution.md).

Near-term commands:

- `javan toolchain doctor` (implemented as a read-only alias)
- `javan toolchain list` (implemented as read-only local inventory)
- Maven and Gradle plugins invoking the installed/downloaded `javan` binary
- Homebrew formula for the binary archive
- IDE report consumption from stable JSON/Markdown reports
- optional `javan toolchain install jdk` only when local Java tools cannot run
- deterministic project selection stored in `.javan/toolchain.lock.json`
- user-global installs and settings under `~/.javan`

Bundling should be optional and explicit. Silent global toolchain mutation is how build
systems learn to bite.
