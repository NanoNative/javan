# Security Policy

## Supported Versions

Security fixes target the current `main` branch and the latest published release when releases exist.

## Reporting a Vulnerability

Do not open a public issue for a suspected vulnerability.

Use GitHub private vulnerability reporting if it is enabled for this repository. If private reporting is unavailable, ask the maintainers for a private contact path without publishing exploit details.

Include:

- affected version or commit;
- operating system, architecture, Java version, and compiler when relevant;
- a minimal reproducer or proof of impact;
- whether the issue affects generated native code, release artifacts, dependencies, or CI.

Maintainers should acknowledge valid private reports within seven days and coordinate disclosure after a fix or mitigation is available.

## Scope

In scope:

- arbitrary code execution or unsafe generated native output;
- supply-chain, release artifact, checksum, or CI credential issues;
- vulnerabilities caused by malformed class files, jars, or project inputs.

Out of scope:

- unsupported Java features that fail closed with a clear diagnostic;
- reports requiring already-compromised local developer machines;
- denial-of-service reports without a practical security impact.
