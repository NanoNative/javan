# GitHub Workflow Testing

Workflow changes are tested on `workflow/*` branches before they are trusted on `main`.

## Branch Flow

1. Push a `workflow/<name>` branch.
2. Let `CI` run on the branch push.
3. Run the `Release` workflow manually on that branch with:
   - `version`: the exact `pom.xml` version, for example `0.1.0`
   - `publish_test_release`: `false` for package-only testing
4. If release publishing itself must be tested, rerun `Release` with
   `publish_test_release=true`. This creates a prerelease named `javan-test-<run-id>-<attempt>`.
5. Delete temporary releases, tags, and completed workflow runs:

```sh
scripts/github-cleanup-test-artifacts.sh NanoNative/javan workflow/<name>
```

## Safety Rules

- Real releases are created only from `v*.*.*` tags.
- Workflow-dispatch test releases use `javan-test-*` tags and are prereleases.
- Uploaded CI/release artifacts use `retention-days: 1`.
- The cleanup script refuses to delete workflow runs outside `workflow/*`, `ci/*`, or `test/*` branches.
- GitHub Packages are not published by the current workflows.
