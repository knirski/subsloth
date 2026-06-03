# Review Loop

Use after pushing a branch or when asked to monitor a PR. `gh` CLI only; see `docs/agent/gh-cli.md` for syntax.

## Push & Create PR

```bash
git push origin <branch>
PR_URL="$(gh pr create --fill)"                                  # ready for review (not draft)
PR_NUMBER="$(gh pr view "$PR_URL" --json number --jq .number)"
OWNER="$(gh repo view --json owner --jq .owner.login)"
REPO="$(gh repo view --json name --jq .name)"
export OWNER REPO PR_NUMBER PR_URL
```

Start monitoring immediately.

## Monitor Loop

Repeat until `merged` or `blocked`. Two types of PR comments exist — both must be checked:
- **Inline review threads** — comments on specific lines in the diff
- **Issue-level comments** — top-level PR comments from automated tools (`github-actions[bot]`, `coderabbitai[bot]`, `gemini-code-assist[bot]`). These do NOT appear in `reviewThreads` — always fetch both.

### a. Watch CI

```bash
gh run watch --compact --interval 15 --exit-status
```

If a required check fails:

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,url
gh run view <run-id> --log-failed
```

Docs-only PRs may show `ci=skipped`.

### b. Wait for PR Agent

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,createdAt,url \
  --jq '.[] | select(.workflowName=="PR Agent")'
gh run watch <run-id> --compact --exit-status
```

After significant update, post `/review` — PR Agent doesn't rerun on every `synchronize` push.

### c. Gather State

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh pr view "$PR_NUMBER" --json number,title,state,mergeable,mergeStateStatus,reviewDecision,reviews,latestReviews,comments,statusCheckRollup,isDraft,additions,deletions,changedFiles,url
gh pr checks "$PR_NUMBER" --json bucket,name,state,workflow,link
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,createdAt,url

# Inline review threads
gh api graphql -f query='
  query($o:String!,$r:String!,$p:Int!){repository(owner:$o,name:$r){pullRequest(number:$p){reviewThreads(first:50){nodes{id isResolved isOutdated comments(first:10){nodes{databaseId path line body}}}}}}}
' --field o="$OWNER" --field r="$REPO" --field p="$PR_NUMBER"

# Issue-level comments (automated tools, NOT in reviewThreads)
gh api "/repos/$OWNER/$REPO/issues/$PR_NUMBER/comments" --jq '.[] | "\(.user.login): \(.body[0:100])..."'
```

### d. Decide

| If | Then |
|---|---|
| Required check failed | Fix & push → back to (a) |
| Required check pending | Wait |
| PR Agent pending for current SHA | Wait |
| Actionable comment found | Address before merge |
| Thread: suggestion correct | Apply, commit, push, resolve |
| Thread: factual error | Reply with evidence |
| Thread: ambiguous/design | Ask for clarification |
| "Changes requested" review | Resolve all threads first |
| All checks green, no actionable comments remain, all threads resolved, mergeable | → ready-to-merge → Merge |

Refresh state after every push, reply, or thread resolution before deciding to merge.

### e. Reply & Resolve

After addressing a suggestion, reply AND resolve the thread (unless follow-up needed):

```bash
# Inline reply (databaseId from reviewThreads query)
gh api "repos/$OWNER/$REPO/pulls/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD)" -F "in_reply_to=$COMMENT_ID"

# Issue-level reply (new top-level comment)
gh api "/repos/$OWNER/$REPO/issues/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD)"

# Resolve thread (PRRT_... id from reviewThreads query)
gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{id}}}' \
  --field id="$THREAD_NODE_ID"
```

If resolve fails, reply-only is acceptable — unresolved non-blocking threads don't prevent merge.

### f. Re-request Review

```bash
gh pr comment "$PR_NUMBER" --body "/review && /improve && /gemini review"
```

Then back to (a).

### g. Merge

```bash
gh pr merge "$PR_NUMBER" --squash --delete-branch
# or auto-merge when checks eventually pass:
gh pr merge "$PR_NUMBER" --squash --auto --delete-branch
```

## Autonomy

Merge immediately when all conditions met. Stop only for: design decisions not in specs, 403/401 permissions, cross-PR scope in multi-PR mode.

## Multi-PR Mode (PR Owner Subagent)

Status format:
```
pr: <number-or-pending>
branch: <branch-name>
base: <main|base-branch|base-pr>
worktree: <path>
state: planning|implementing|waiting-review|addressing-comments|ready-to-merge|merged|blocked
blocker: <none-or-short-blocker>
next-action: <single next step>
```

Rules: MUST push/reply/merge only the assigned PR. MUST NOT act on another. MUST mark `blocked` if another PR must merge first. MUST escalate if review exceeds scope. MUST NOT mark stacked PR ready-to-merge while base is open.

State transitions: `planning` → `implementing` → `waiting-review` → `addressing-comments` → `ready-to-merge` → `merged` (or `blocked` at any point).

Exit when `merged` or `blocked`. In multi-PR mode, rotate checks across PRs.
