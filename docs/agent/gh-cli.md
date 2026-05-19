# `gh` CLI Reference

Primary tool for all GitHub interactions. Never use the browser.

**Key feature for agents**: `--json <fields>` + `--jq <query>` on every major command. Run `gh <command> --json` to list available fields.

Also see `gh help formatting` for Go template functions: `tablerow`, `color`, `timeago`, `hyperlink`, `pluck`, `truncate`, `join`.

## Authentication & Exit Codes

```bash
gh auth status                              # exit 4 if not authenticated
```

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Failure |
| 4 | Not authenticated |
| 8 | Checks pending (`gh pr checks`) |

## Pull Requests

```bash
# View
gh pr view [<number>|<url>|<branch>] --json number,title,state,mergeable,mergeStateStatus,reviewDecision,reviews,statusCheckRollup,isDraft,additions,deletions,changedFiles,labels,assignees,comments,headRefName,baseRefName,url
gh pr status --json number,title,state,reviewDecision,mergeStateStatus,statusCheckRollup,url

# List (also: --author @me, --label bug, --search "status:success")
gh pr list --json number,title,author,headRefName,state

# Create
gh pr create --fill                          # auto-fill title/body from commits
gh pr create --title "..." --body "..." --label enhancement --assignee @me
gh pr create --draft --title "..." --body "..."

# Review
gh pr review <num> --approve --body "LGTM"
gh pr review <num> --request-changes --body "..."
gh pr review <num> --comment --body "..."

# Comment
gh pr comment <num> --body "..."            # add comment
gh pr comment <num> --edit-last --body "..." # edit your last comment

# Edit
gh pr edit <num> --title "..." --body "..."
gh pr edit <num> --add-label "bug,help wanted" --remove-label "core"
gh pr edit <num> --add-reviewer monalisa --add-assignee @me
gh pr edit <num> --base main

# Merge
gh pr merge <num> --squash --delete-branch   # squash + merge (required)
gh pr merge <num> --squash --auto --delete-branch  # auto-merge when checks pass
gh pr merge <num> --squash --subject "feat: ..." --body "Closes #..."

# CI Checks
gh pr checks <num> --json bucket,name,state,link
gh pr checks <num> --watch --fail-fast       # blocks until done

# Diff
gh pr diff <num>                             # full patch
gh pr diff <num> --name-only                 # changed files list
gh pr diff <num> --exclude '*.lock'          # exclude globs

# Update branch from base
gh pr update-branch <num>                    # merge base into PR
gh pr update-branch <num> --rebase           # rebase PR on base
```

## CI / Workflow Runs

```bash
# Watch live (killer feature — blocks until completion)
gh run watch --compact --interval 15 --exit-status
gh run watch <run-id> --compact --interval 15 --exit-status

# List
gh run list --json number,workflowName,status,conclusion,headBranch,createdAt
gh run list --branch main --status failure --json number,conclusion

# View
gh run view <run-id> --json jobs,status,conclusion
gh run view <run-id> --log-failed            # only failed steps
gh run view --job <job-id> --log             # specific job logs

# Trigger
gh workflow run <name> --ref <branch> -f key=value
```

## Search

```bash
# PRs
gh search prs --review-requested=@me --state=open --json number,title,repository
gh search prs --author=@me --merged --json number,title,mergedAt
gh search prs --review=approved --repo=owner/repo --json number,title

# Issues
gh search issues --label=bug --state=open --assignee=@me --json number,title
```

## Issues

```bash
gh issue view <num> --json number,title,body,state,labels,assignees,comments
gh issue list --json number,title,labels,assignees --state open
gh issue status --json number,title,state,url
```

## GitHub API (for operations without dedicated commands)

### List review threads with IDs

```bash
gh api graphql -f query='
  query($o:String!,$r:String!,$p:Int!){repository(owner:$o,name:$r){pullRequest(number:$p){reviewThreads(first:50){nodes{id isResolved isOutdated comments(first:10){nodes{databaseId path line body}}}}}}}
' --field o="$OWNER" --field r="$REPO" --field p="$PR_NUMBER"
```

### Resolve / unresolve thread

```bash
gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{id isResolved}}}' --field id="$THREAD_NODE_ID"
gh api graphql -f query='mutation($id:ID!){unresolveReviewThread(input:{threadId:$id}){thread{id isResolved}}}' --field id="$THREAD_NODE_ID"
```

### Inline reply to review comment

```bash
gh api "repos/$OWNER/$REPO/pulls/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD)" -F "in_reply_to=$COMMENT_ID"
```

**ID mapping**: `comment.databaseId` (from `reviewThreads` query) = REST comment ID for `in_reply_to`. GraphQL `PRRT_...` ID for thread resolution.

## Releases & Repos

```bash
gh release list --json tagName,isLatest,createdAt
gh release view --json tagName,body
gh release create v1.2.3 --title "v1.2.3" --notes "..."
gh repo view owner/repo --json nameWithOwner,defaultBranchRef,description
gh repo list owner --json nameWithOwner,isPrivate
```

## Quick Status

```bash
gh status                                  # issues, PRs, review requests, mentions
gh status --org my-org
```

## Agent Patterns

```bash
# Watch CI after push
git push origin <branch> && gh run watch --compact --interval 15 --exit-status

# Get PR number for current branch
gh pr view --json number --jq .number

# Check merge readiness
gh pr view "$PR_NUMBER" --json mergeable,mergeStateStatus,reviewDecision
# mergeable: MERGEABLE / CONFLICTING / UNKNOWN
# mergeStateStatus: CLEAN / HAS_HOOKS / BLOCKED / BEHIND / DIRTY / UNSTABLE
# reviewDecision: APPROVED / CHANGES_REQUESTED / REVIEW_REQUIRED

# CI status categorized
gh pr checks "$PR_NUMBER" --json bucket,name,state
# bucket: pass / fail / pending / skipping / cancel

# Investigate CI failure logs
gh run view --log-failed
```
