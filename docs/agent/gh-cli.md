# `gh` CLI Reference

Primary tool for GitHub interaction. Never use the browser. Key feature: `--json <fields>` + `--jq <query>` on every major command. Run `gh <command> --json` for available fields.

Exit codes: 0=success, 1=failure, 4=not authenticated, 8=checks pending.

## Pull Requests

```bash
gh pr view [<number>|<url>|<branch>] --json <fields>
gh pr status --json number,title,state,reviewDecision,url
gh pr list --json number,title,author,headRefName,state

gh pr create --fill                                        # auto-fill from commits
gh pr review <num> --approve --body "..."                  # or --request-changes, --comment
gh pr comment <num> --body "..."
gh pr edit <num> --title "..." --body "..."
gh pr edit <num> --add-label "bug,help wanted" --remove-label "core"
gh pr edit <num> --base main

gh pr merge <num> --squash --delete-branch                 # squash+merge (required)
gh pr merge <num> --squash --auto --delete-branch           # auto-merge
gh pr checks <num> --json bucket,name,state,link            # CI status
gh pr diff <num> [--name-only]
gh pr update-branch <num> [--rebase]
```

## CI / Workflow Runs

```bash
gh run watch --compact --interval 15 --exit-status          # watch live CI
gh run list --json number,workflowName,status,conclusion,headBranch
gh run view <run-id> --json jobs,status,conclusion
gh run view <run-id> --log-failed
gh workflow run <name> --ref <branch> -f key=value
```

## Search

```bash
gh search prs --review-requested=@me --state=open --json number,title,repository
gh search issues --label=bug --state=open --assignee=@me --json number,title
```

## Issues / Releases

```bash
gh issue view <num> --json number,title,body,state,labels,assignees,comments
gh release list --json tagName,isLatest,createdAt
gh release create v1.2.3 --title "v1.2.3" --notes "..."
```

## GitHub API (operations without dedicated commands)

### List review threads with IDs

```bash
gh api graphql -f query='
  query($o:String!,$r:String!,$p:Int!){repository(owner:$o,name:$r){pullRequest(number:$p){reviewThreads(first:50){nodes{id isResolved isOutdated comments(first:10){nodes{databaseId path line body}}}}}}}
' --field o="$OWNER" --field r="$REPO" --field p="$PR_NUMBER"
```

### Resolve/unresolve thread

```bash
gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{id isResolved}}}' --field id="$THREAD_NODE_ID"
```

### Inline reply to review comment

```bash
gh api "repos/$OWNER/$REPO/pulls/$PR_NUMBER/comments" \
  -X POST -f "body=Addressed in $(git rev-parse HEAD)" -F "in_reply_to=$COMMENT_ID"
```

ID mapping: `comment.databaseId` (from reviewThreads query) = REST ID for `in_reply_to`. GraphQL `PRRT_...` ID for thread resolution.

## Agent Patterns

```bash
# Watch CI after push
git push origin <branch> && gh run watch --compact --interval 15 --exit-status

# Get PR number for current branch
gh pr view --json number --jq .number

# Check merge readiness
gh pr view "$PR_NUMBER" --json mergeable,mergeStateStatus,reviewDecision
# mergeable: MERGEABLE / CONFLICTING / UNKNOWN
# mergeStateStatus: CLEAN / BLOCKED / BEHIND / DIRTY / UNSTABLE
# reviewDecision: APPROVED / CHANGES_REQUESTED / REVIEW_REQUIRED

# CI status by bucket
gh pr checks "$PR_NUMBER" --json bucket,name,state
# bucket: pass / fail / pending / skipping / cancel

# Investigate CI failure logs
gh run view --log-failed
```
