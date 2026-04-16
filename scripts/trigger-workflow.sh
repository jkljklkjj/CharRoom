#!/usr/bin/env bash
set -e

# Trigger the build-installers workflow via GitHub Actions API or gh CLI.
# Usage:
#   GITHUB_TOKEN=ghp_xxx GITHUB_REPOSITORY=owner/repo ./scripts/trigger-workflow.sh [ref]
# If ref is omitted, 'main' will be used.

REF=${1:-main}
WORKFLOW_FILE=".github/workflows/build-installers.yml"
REPO=${GITHUB_REPOSITORY:-}
if [ -z "$REPO" ]; then
  # attempt to detect from git
  REPO_URL=$(git config --get remote.origin.url || true)
  if [ -n "$REPO_URL" ]; then
    # supports https and git@ formats
    REPO=$(echo "$REPO_URL" | sed -E 's/.*[:/]([^/]+\/[^/.]+)(\.git)?$/\1/')
  fi
fi

if [ -z "$REPO" ]; then
  echo "Set GITHUB_REPOSITORY (owner/repo) or ensure git remote origin is configured"
  exit 1
fi

if command -v gh >/dev/null 2>&1; then
  echo "Using gh CLI to trigger workflow"
  gh workflow run build-installers.yml --repo "$REPO" --ref "$REF"
  exit 0
fi

if [ -z "$GITHUB_TOKEN" ]; then
  echo "GITHUB_TOKEN is required when gh CLI is not available"
  exit 1
fi

API_URL="https://api.github.com/repos/$REPO/actions/workflows/build-installers.yml/dispatches"

curl -X POST "$API_URL" \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  -d "{ \"ref\": \"$REF\" }"

echo "Workflow dispatch requested for $REPO (ref=$REF)"