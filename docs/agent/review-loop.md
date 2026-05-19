# Review Loop

Use after pushing a branch or when asked to monitor a PR. `gh` CLI only; see [gh-cli.md](gh-cli.md) for syntax.

## Procedure

### 1. Push & Create PR

```bash
git push origin <branch>
PR_URL="$(gh pr create --fill)"                                  # ready for review (not draft)
PR_NUMBER="$(gh pr view "$PR_URL" --json number --jq .number)"
OWNER="$(gh repo view --json owner --jq .owner.login)"
REPO="$(gh repo view --json name --jq .name)"
export OWNER REPO PR_NUMBER PR_URL
```

Start monitoring immediately.

### 2. Monitor Loop

Repeat until `merged` or `blocked`.

Two types of comments exist on a PR and both must be checked:

- **Inline review threads** — comments on specific lines of code in the diff
- **Issue-level comments** — top-level PR comments (posted by automated tools like `github-actions[bot]`, `coderabbitai[bot]`, `gemini-code-assist[bot]`)

Issue-level comments are easy to miss because they do not appear in `reviewThreads`. Always fetch both types.

#### a. Watch CI

```bash
gh run watch --compact --interval 15 --exit-status
```

Use `gh run watch --compact --interval 15 --exit-status` to monitor PR CI progress. Use the PR checks surface as the source of truth for merge gates. In this repo that includes `conventional-title` plus the `changes` / `ci` jobs.

If a required check fails, inspect the failing run or job:

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,url
gh run view <run-id> --log-failed
```

Docs-only PRs may legitimately show `ci=skipped` when `changes` finds no source edits.

#### b. Wait for PR Agent

Wait for the latest PR Agent run and comments before merging.

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,createdAt,url \
  --jq '.[] | select(.workflowName=="PR Agent")'
gh run watch <run-id> --compact --exit-status
```

After a significant update, post `/review`; PR Agent does not rerun on every `synchronize` push in this repo.

#### c. Gather State

Refresh `HEAD_SHA`, then gather the current PR state — including both inline review threads **and** issue-level comments.

```bash
HEAD_SHA="$(gh pr view "$PR_NUMBER" --json headRefOid --jq .headRefOid)"
gh pr view "$PR_NUMBER" --json number,title,state,mergeable,mergeStateStatus,reviewDecision,reviews,latestReviews,comments,statusCheckRollup,isDraft,additions,deletions,changedFiles,url
gh pr checks "$PR_NUMBER" --json bucket,name,state,workflow,link
gh run list --commit "$HEAD_SHA" --json databaseId,workflowName,status,conclusion,createdAt,url \
  --jq '.[] | select(.workflowName=="PR Agent")'

# Inline review threads (comments on specific lines in the diff)
gh api graphql -f query='
  query($o:String!,$r:String!,$p:Int!){repository(owner:$o,name:$r){pullRequest(number:$p){reviewThreads(first:50){nodes{id isResolved isOutdated comments(first:10){nodes{databaseId path line body}}}}}}}
' --field o="$OWNER" --field r="$REPO" --field p="$PR_NUMBER"

# Issue-level comments (posted by automated tools, not part of review threads)
gh api "/repos/$OWNER/$REPO/issues/$PR_NUMBER/comments" --jq '.[] | "\(.user.login): \(.body[0:100])..."'
```

This gives you required checks, PR-level comments/reviews, the latest PR Agent run, unresolved inline threads, **and** issue-level comments from automated reviewers.

#### d. Decide

| If | Then |
|---|---|
| Required check failed | Fix & push → back to (a) |
| Required check pending | Wait |
| PR Agent run pending or not yet gathered for the current head SHA | Wait |
| PR-level review or comment is actionable | Address it before merge |
| Issue-level comment from automated tool is actionable | Fix and reply (same as inline threads) |
| Thread: suggestion correct | Apply, commit, push, resolve |
| Thread: factual error | Reply with evidence, `@gemini-code-assist` |
| Thread: ambiguous/design | Ask for clarification |
| "Changes requested" review | Resolve all threads first |
| All required checks passed or were intentionally skipped, no actionable PR-level or issue-level comments remain, all threads are resolved, and the PR is mergeable | → State: `ready-to-merge` → Merge |

After any push, reply, or thread resolution, gather again before deciding to merge.

#### e. Reply & Resolve

After addressing a suggestion or answering a question, reply AND resolve the thread (unless you need to ask follow-up questions).

```bash
# Inline reply (match databaseId from reviewThreads query)
gh api "repos/$OWNER/$REPO/pulls/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD)" -F "in_reply_to=$COMMENT_ID"

# Issue-level reply (no threading — post a new top-level comment)
gh api "/repos/$OWNER/$REPO/issues/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD). See reply above for details."

# Resolve thread (id = PRRT_kwDOxxx from reviewThreads query)
gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{id}}}' \
  --field id="$THREAD_NODE_ID"
```

Use `databaseId` from the GraphQL thread query as the numeric REST `in_reply_to` ID.

Resolve the thread immediately after replying unless additional discussion is needed (ambiguous suggestion, design question, or clarification requested). Unresolved threads block merge — resolve proactively.

Issue-level comments cannot be resolved like review threads. After posting a reply noting the fix, mention the comment ID in the body so the automated tool can see the response.

#### f. Re-request Review

After significant changes, trigger fresh PR Agent and Gemini review comments.

```bash
gh pr comment "$PR_NUMBER" --body "/review"
gh pr comment "$PR_NUMBER" --body "/improve"
gh pr comment "$PR_NUMBER" --body "/gemini review"
```

Then go back to (a).

#### g. Merge

Set status to `ready-to-merge`, then merge.

```bash
gh pr merge "$PR_NUMBER" --squash --delete-branch
```

Or auto-merge (merges when checks eventually pass):

```bash
gh pr merge "$PR_NUMBER" --squash --auto --delete-branch
```

## Autonomy

Merge immediately when all conditions are met. Stop only for:
- Design decisions not in specs
- 403/401 permissions
- Cross-PR scope in multi-PR mode

## Multi-PR Mode (PR Owner Subagent)

### Status Reporting

```
pr: <number-or-pending>
branch: <branch-name>
base: <main|base-branch|base-pr>
worktree: <path>
state: planning|implementing|waiting-review|addressing-comments|ready-to-merge|merged|blocked
blocker: <none-or-short-blocker>
next-action: <single next step>
```

### Rules

- MUST push, reply, and merge only the assigned PR.
- MUST NOT act on another PR.
- MUST mark `blocked` if another PR must merge first.
- MUST escalate if a review comment exceeds your scope.
- MUST NOT mark a stacked PR `ready-to-merge` while its `base` PR is still open unless the coordinator restacks it.

### State Transitions

- `planning`: before the first code edit in the assigned worktree
- `implementing`: code or tests are in progress
- `waiting-review`: PR is open and waiting on CI or reviews
- `addressing-comments`: acting on review feedback
- `ready-to-merge`: all required checks and reviews are satisfied
- `merged`: PR merged
- `blocked`: waiting on dependency, permission, dirty worktree resolution, or design decision

### Exit

Exit when `merged` or `blocked`. In multi-PR mode, rotate checks across PRs.
