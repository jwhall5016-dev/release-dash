#!/usr/bin/env bash

# Mentat weekly Phase 1: open production PRs for all four apps.
# Docs: mentat-devsecops-support/related-work/phase-1-release-pr-creation-runbook-robin.md §1a-bis
#
# Requirements:
# - gh authenticated as a user with push on ALL four repos below (pull-only → create ref often 404s).
# - Deploy date = Emily Mentat release ticket ship day (MM-DD-YYYY), NOT calendar "today".

set -euo pipefail

REPOS=(
  "slingshot-aerospace-inc/dst-virtual-core"
  "slingshot-aerospace-inc/slingshot-apis-simulation"
  "slingshot-aerospace-inc/rad-talos-single-step"
  "slingshot-aerospace-inc/rad-talos-agents"
)

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    if [[ "$command_name" == "gh" ]]; then
      echo "Please install the Github CLI - brew install gh" >&2
      exit 1
    fi
    echo "Error: '$command_name' is required but not installed." >&2
    exit 1
  fi
}

prompt_yes_no() {
  local prompt="$1"
  local answer
  local normalized_answer

  while true; do
    read -r -p "$prompt [y/n]: " answer
    normalized_answer="$(printf '%s' "$answer" | tr '[:upper:]' '[:lower:]')"
    case "$normalized_answer" in
      y|yes) return 0 ;;
      n|no) return 1 ;;
      *) echo "Please answer 'y' or 'n'." ;;
    esac
  done
}

validate_deploy_date() {
  local deploy_date="$1"

  DEPLOY_DATE="$deploy_date" python3 - <<'PY'
import os
import sys
from datetime import datetime

deploy_date = os.environ["DEPLOY_DATE"]

try:
    datetime.strptime(deploy_date, "%m-%d-%Y")
except ValueError:
    sys.exit(1)
PY
}

print_latest_commit_details() {
  local repo="$1"
  local commit_json

  if ! commit_json="$(gh api "repos/$repo/commits/main")"; then
    echo
    echo "$repo"
    echo "  Failed to fetch the latest commit on 'main'."
    return 1
  fi

  REPO="$repo" COMMIT_JSON="$commit_json" python3 - <<'PY'
import json
import os
from datetime import datetime, timezone

repo = os.environ["REPO"]
data = json.loads(os.environ["COMMIT_JSON"])

commit = data.get("commit", {})
author = data.get("author") or {}
commit_author = commit.get("author", {}) or {}
commit_committer = commit.get("committer", {}) or {}

author_name = author.get("login") or commit_author.get("name") or "Unknown"
date_str = commit_author.get("date") or commit_committer.get("date")
message = (commit.get("message") or "").strip()
description = " ".join(message.splitlines()) if message else "(no description)"

print()
print(repo)

if not date_str:
    print("  Date: Unknown")
    print("  Age:  Unknown")
else:
    commit_dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
    now = datetime.now(timezone.utc)
    delta = now - commit_dt
    total_hours = max(int(delta.total_seconds() // 3600), 0)
    days = total_hours // 24
    hours = total_hours % 24
    local_dt = commit_dt.astimezone()

    print(f"  Date: {local_dt.strftime('%Y-%m-%d %H:%M:%S %Z')}")
    print(f"  Age:  {days} day(s), {hours} hour(s) ago")

print(f"  Author: {author_name}")
print(f"  Description: {description}")
PY
}

find_existing_pr_url() {
  local repo="$1"
  local release_branch="$2"

  gh pr list \
    --repo "$repo" \
    --state open \
    --base production \
    --head "$release_branch" \
    --json url \
    --jq '.[0].url // ""' || true
}

require_repo_push() {
  local repo="$1"
  local can_push

  can_push="$(gh api "repos/$repo" --jq '.permissions.push // false')" || {
    echo "  ERROR: could not read permissions for $repo" >&2
    return 1
  }
  if [[ "$can_push" != "true" ]]; then
    echo "  ERROR: active gh user lacks push on $repo (permissions.push=$can_push)." >&2
    echo "  GitHub often returns HTTP 404 on create-ref without push — not a missing-branch mystery." >&2
    echo "  Fix: gh auth switch to an account with write, or get push on this repo, then re-run." >&2
    return 1
  fi
}

require_git_sha() {
  local label="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9a-f]{40}$ ]]; then
    echo "  ERROR: expected a 40-char git SHA for ${label}, got: ${value:-<empty>}" >&2
    return 1
  fi
}

ref_sha() {
  local repo="$1"
  local branch="$2"
  gh api "repos/$repo/git/ref/heads/$branch" --jq '.object.sha'
}

ensure_release_branch() {
  local repo="$1"
  local release_branch="$2"
  local main_sha
  local existing_sha=""
  local verified_sha=""

  main_sha="$(ref_sha "$repo" "main")" || {
    echo "  ERROR: could not read refs/heads/main on $repo" >&2
    return 1
  }
  require_git_sha "main" "$main_sha" || return 1

  if existing_sha="$(ref_sha "$repo" "$release_branch" 2>/dev/null)"; then
    require_git_sha "$release_branch" "$existing_sha" || return 1
    echo "  Release branch already exists: $release_branch ($existing_sha)"
    return 0
  fi

  if ! gh api \
    --method POST \
    "repos/$repo/git/refs" \
    -f "ref=refs/heads/$release_branch" \
    -f "sha=$main_sha" \
    >/dev/null; then
    echo "  ERROR: failed to create refs/heads/$release_branch at $main_sha" >&2
    return 1
  fi

  verified_sha="$(ref_sha "$repo" "$release_branch")" || {
    echo "  ERROR: created $release_branch but GET refs/heads/$release_branch failed" >&2
    return 1
  }
  require_git_sha "$release_branch (after create)" "$verified_sha" || return 1
  echo "  Created release branch $release_branch at main ($verified_sha)"
}

# GitHub refuses PRs with no commits vs base. Same-tree empty commit is a no-op merge.
# Only skip when compare clearly shows we are already ahead; on 404/empty/0, add empty commit.
ensure_commits_ahead_of_production() {
  local repo="$1"
  local release_branch="$2"
  local ahead_by=""
  local compare_rc=0
  local head_sha
  local tree_sha
  local new_sha
  local production_sha
  local verified_sha

  production_sha="$(ref_sha "$repo" "production")" || {
    echo "  ERROR: could not read refs/heads/production on $repo" >&2
    return 1
  }
  require_git_sha "production" "$production_sha" || return 1

  ahead_by="$(
    gh api "repos/$repo/compare/production...$release_branch" --jq '.ahead_by // empty' 2>/dev/null
  )" || compare_rc=$?

  if [[ "$compare_rc" -eq 0 && "$ahead_by" =~ ^[1-9][0-9]*$ ]]; then
    return 0
  fi

  if [[ "$compare_rc" -ne 0 ]]; then
    echo "  Compare production...$release_branch failed (rc=$compare_rc); adding empty commit"
  else
    echo "  ahead_by=${ahead_by:-empty}; adding empty commit so GitHub will open a PR"
  fi

  head_sha="$(ref_sha "$repo" "$release_branch")" || {
    echo "  ERROR: could not read refs/heads/$release_branch before empty commit" >&2
    return 1
  }
  require_git_sha "$release_branch" "$head_sha" || return 1

  tree_sha="$(gh api "repos/$repo/git/commits/$head_sha" --jq '.tree.sha')" || {
    echo "  ERROR: could not read commit tree for $head_sha" >&2
    return 1
  }
  require_git_sha "tree" "$tree_sha" || return 1

  new_sha="$(
    gh api --method POST "repos/$repo/git/commits" \
      --jq '.sha' \
      --input - <<EOF
{
  "message": "chore: open production PR with no new changes",
  "tree": "$tree_sha",
  "parents": ["$head_sha"]
}
EOF
  )" || {
    echo "  ERROR: failed to create empty commit on $release_branch" >&2
    return 1
  }
  require_git_sha "empty commit" "$new_sha" || return 1

  if ! gh api --method PATCH "repos/$repo/git/refs/heads/$release_branch" \
    -f "sha=$new_sha" \
    -F "force=false" \
    >/dev/null; then
    echo "  ERROR: failed to move refs/heads/$release_branch to $new_sha" >&2
    return 1
  fi

  verified_sha="$(ref_sha "$repo" "$release_branch")" || {
    echo "  ERROR: empty commit created but could not re-read $release_branch" >&2
    return 1
  }
  if [[ "$verified_sha" != "$new_sha" ]]; then
    echo "  ERROR: $release_branch is $verified_sha, expected $new_sha" >&2
    return 1
  fi

  echo "  Added empty commit on $release_branch so GitHub will open a PR ($new_sha)"
}

create_production_pr() {
  local repo="$1"
  local deploy_date="$2"
  local release_branch="release-${deploy_date}"
  local existing_pr_url
  local pr_url

  existing_pr_url="$(find_existing_pr_url "$repo" "$release_branch")"
  if [[ -n "$existing_pr_url" ]]; then
    echo "  An open production PR already exists: $existing_pr_url"
    CREATED_PR_URLS+=("$existing_pr_url")
    return 0
  fi

  require_repo_push "$repo" || return 1
  ensure_release_branch "$repo" "$release_branch" || return 1
  ensure_commits_ahead_of_production "$repo" "$release_branch" || return 1

  if pr_url="$(
    gh pr create \
      --repo "$repo" \
      --base production \
      --head "$release_branch" \
      --title "release-${deploy_date}" \
      --body "Automated production promotion PR for $deploy_date (head $release_branch). Open even if main has no new commits — pre-release needs all four apps."
  )"; then
    echo "  Created PR: $pr_url"
    CREATED_PR_URLS+=("$pr_url")
    return 0
  fi

  echo "  Failed to create a PR for $repo." >&2
  return 1
}

main() {
  local deploy_date=""
  local repo

  require_command gh
  require_command python3

  if ! gh auth status >/dev/null 2>&1; then
    echo "Error: gh is not authenticated. Run 'gh auth login' first." >&2
    exit 1
  fi

  echo "Latest commits on 'main':"
  for repo in "${REPOS[@]}"; do
    print_latest_commit_details "$repo" || true
  done

  if ! prompt_yes_no "Are you planning to do a production deploy?"; then
    echo
    echo "No production deploy selected. No PRs were created."
    exit 0
  fi

  while true; do
    echo "Ship day from Emily's Mentat release tickets (e.g. 10/02/26 → 10-02-2026), not today's calendar date."
    read -r -p "Enter the deploy date (MM-DD-YYYY): " deploy_date
    if validate_deploy_date "$deploy_date"; then
      break
    fi
    echo "Invalid date. Please use MM-DD-YYYY."
  done

  echo
  echo "Creating PRs for all four apps (release-$deploy_date → production)."
  echo "Pre-release needs every app even when main has no new commits."

  CREATED_PR_URLS=()
  for repo in "${REPOS[@]}"; do
    echo
    echo "$repo"
    create_production_pr "$repo" "$deploy_date" || true
  done

  echo
  if [[ "${#CREATED_PR_URLS[@]}" -eq 0 ]]; then
    echo "No PR URLs to display."
    return 0
  fi

  echo "PR URLs:"
  printf ' - %s\n' "${CREATED_PR_URLS[@]}"
}

main "$@"
