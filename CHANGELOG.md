# Changelog

## [Unreleased]

- Add the configured static Java-to-C native import ABI, borrowed `byte[]` lifetime,
  target overlays, and project-local link inputs.
- Add the generated runtime-header include path for configured C and Objective-C sources,
  allowing `#include "javan_runtime.h"` to provide native import ABI declarations.
- Require reachable native method references to use an exact instantiated-SAM/native
  descriptor match; no boxing or unboxing adaptation is provided.
- Clarify that dynamic JNI loading and `System.load`/`System.loadLibrary` remain unsupported.

## [2026.6.14] - 2026-06-14

- Release Linux/macOS Javan binary archives.
- Enable replayable container image publication from release assets.
