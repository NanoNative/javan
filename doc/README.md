# Documentation

This directory is split by purpose so the core repository stays readable:

- `status/`: generated or maintained project status documents such as roadmap progress,
  support accounting, JDK compatibility, and release-readiness tracking
- `spec/`: stable product and engineering contracts for the core `javan` compiler, CLI,
  reports, runtime model, packaging, and verification
- `adr/`: short architecture decisions that explain why the repository is organized the
  way it is

Core entrypoints:

- [README.md](../README.md): public front door, quick start, supported outputs, and high
  signal status snapshot
- [status/roadmap-progress.md](status/roadmap-progress.md): implementation progress by
  roadmap item, coverage snapshot, and honest target view
- [status/support-matrix.md](status/support-matrix.md): named support ledger used for
  release accounting
- [status/jdk-compatibility.md](status/jdk-compatibility.md): latest deterministic JDK
  inventory summary
- [spec/roadmap.md](spec/roadmap.md): core-repo roadmap
- [spec/native-abi.md](spec/native-abi.md): native library ABI, ownership, and error
  contracts
- [spec/release.md](spec/release.md): release gates and verification policy

Sibling-product work such as Javan Studio, build plugins, and optional SDK wrappers should
keep their detailed product roadmaps in their own workspace folders under
`/Users/yuna/projects/javan-project/`, not inside the core compiler repo.
