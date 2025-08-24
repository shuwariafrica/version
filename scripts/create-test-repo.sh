#!/usr/bin/env bash
set -euo pipefail

# Create a deterministic test repository with multiple branches, tags,
# annotated and lightweight tags, pre-releases, multi-tag commits,
# ignored/non-semver tags, and unreachable tags.
#
# Usage: scripts/create-test-repo.sh /absolute/path/to/repo
#
# The script will remove the directory if it exists, then recreate it.

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/path/to/repo" >&2
  exit 2
fi

REPO_DIR="$1"
if [[ -e "$REPO_DIR" ]]; then
  rm -rf "$REPO_DIR"
fi
mkdir -p "$REPO_DIR"
cd "$REPO_DIR"

git init -q
git config user.name "Version CLI Test"
git config user.email "test@example.com"
# Silence detached HEAD advice in tests
git config advice.detachedHead false
# Disable any signing and editor prompts for non-interactive runs
git config commit.gpgsign false
git config tag.gpgsign false
git config gpg.sign false 2>/dev/null || true
git config gpg.format openpgp 2>/dev/null || true
git config core.editor true
git config core.hooksPath /dev/null

# c1: initial commit, lightweight semver tag and an invalid tag
echo "# Test Repo" > README.md
git add README.md
git commit --no-gpg-sign --message "Initial commit" >/dev/null
C1=$(git rev-parse HEAD)
git tag v0.1.0       # lightweight
git tag not-a-version

# c2: change: minor (to simulate activity before v1.0.0)
echo "line 1" >> README.md
git add README.md
git commit --no-gpg-sign --message "change: minor" >/dev/null
C2=$(git rev-parse HEAD)

# Tag v1.0.0 (annotated, explicitly not signed) on c2
git tag --annotate --no-sign --message "Release 1.0.0" v1.0.0

# Create release/1.0 branch at v1.0.0
git branch release/1.0 v1.0.0
git checkout -q release/1.0
# cR1: patch bump on maintenance
echo "hotfix" >> README.md
git add README.md
git commit --no-gpg-sign --message "change: patch" >/dev/null
git tag --annotate --no-sign --message "Release 1.0.1" v1.0.1

# Return to main
git checkout -q master 2>/dev/null || git checkout -q main 2>/dev/null || git checkout -q -B main v1.0.0
# Ensure we are on main (create it if needed)
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  git branch -M main
fi

# If currently at v1.0.0, add a couple of commits
echo "post-1.0 work" >> README.md
git add README.md
git commit --no-gpg-sign --message "target: 1.0.2" >/dev/null
echo "patch work" >> README.md
git add README.md
git commit --no-gpg-sign --message "change: patch" >/dev/null

# Multi-tag same commit (pre-release and final)
echo "2.0 line" >> README.md
git add README.md
git commit --no-gpg-sign --message "prep 2.0" >/dev/null
MT=$(git rev-parse HEAD)
git tag --annotate --no-sign --message "2.0.0-rc.1 on same commit" v2.0.0-rc.1
git tag --annotate --no-sign --message "2.0.0 final on same commit" v2.0.0

# Pre-release branch with reachable pre-release
git branch pre-from-1.0 v1.0.0
git checkout -q pre-from-1.0
echo "minor bump line" >> README.md
git add README.md
git commit --no-gpg-sign --message "change: minor" >/dev/null
git tag --annotate --no-sign --message "1.1.0-rc.1 pre-release" v1.1.0-rc.1
git checkout -q main

# Unreachable high tag on another branch for repo-wide tests
git checkout -q -b unreachable-from-main "$C1"
echo "unreach" >> README.md
git add README.md
git commit --no-gpg-sign --message "unreachable branch work" >/dev/null
git tag --annotate --no-sign --message "Repo-wide high tag" v4.3.0
git checkout -q main

# Additional ignored tags
git tag v1.0
git tag release-1.0.0

# Create a feature branch from v1.0.0, add commits, and merge back into main with a merge commit
git checkout -q -b feature/merge v1.0.0
echo "feat work" > FEATURE.txt
git add FEATURE.txt
git commit --no-gpg-sign --message "change: minor" >/dev/null
echo "bugfix" > BUGFIX.txt
git add BUGFIX.txt
git commit --no-gpg-sign --message "change: patch" >/dev/null
git checkout -q main
# Ensure merge is non fast-forward
git merge --no-ff --no-gpg-sign -m "merge feature/merge" feature/merge >/dev/null
# Add a post-merge linear commit so first-parent count is > 0
echo "post-merge" >> README.md
git add README.md
git commit --no-gpg-sign --message "housekeeping" >/dev/null

# Final: leave repository on main tip, clean state.
echo "OK: created test repo at $REPO_DIR"
