#!/bin/sh
set -eu

REPO=${1:-NanoNative/javan}
BRANCH=${2:-$(git branch --show-current 2>/dev/null || printf '')}
REMOTE=${3:-origin}

if [ -z "$BRANCH" ]; then
  printf '%s\n' "Usage: scripts/github-cleanup-test-artifacts.sh [owner/repo] [workflow-branch] [remote]" >&2
  exit 2
fi

case "$BRANCH" in
  workflow/*|ci/*|test/*) ;;
  *)
    printf '%s\n' "Refusing to delete workflow runs for non-test branch: $BRANCH" >&2
    printf '%s\n' "Use a branch named workflow/*, ci/*, or test/*." >&2
    exit 2
    ;;
esac

if ! command -v gh >/dev/null 2>&1; then
  printf '%s\n' "Missing gh CLI." >&2
  exit 2
fi

printf '%s\n' "Cleaning temporary javan workflow artifacts in $REPO for branch $BRANCH"

release_tags=$(gh release list -R "$REPO" --limit 100 --json tagName --jq '.[] | select(.tagName | startswith("javan-test-")) | .tagName')
for tag in $release_tags; do
  printf '%s\n' "Deleting release and tag $tag"
  gh release delete "$tag" -R "$REPO" --cleanup-tag --yes
done

remote_tags=$(git ls-remote --tags "$REMOTE" 'refs/tags/javan-test-*' | awk '{print $2}' | sed 's#refs/tags/##')
for tag in $remote_tags; do
  printf '%s\n' "Deleting orphan test tag $tag"
  git push "$REMOTE" ":refs/tags/$tag"
done

run_ids=$(gh run list -R "$REPO" --branch "$BRANCH" --limit 100 --json databaseId,status --jq '.[] | select(.status == "completed") | .databaseId')
for run_id in $run_ids; do
  printf '%s\n' "Deleting completed workflow run $run_id"
  gh run delete "$run_id" -R "$REPO"
done

printf '%s\n' "Cleanup complete."
