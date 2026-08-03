# Cachix Binary Cache Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure SubSloth to use the `knirski-subsloth` Cachix cache locally and publish the x86_64 Linux Nix development shell from GitHub Actions on `main`.

**Architecture:** Add the cache substituter and trusted key to `flake.nix`. Add a pinned composite action that installs Nix and configures Cachix in pull-only or authenticated mode based on an optional token. Add an independent workflow job that pushes the devShell closure only for trusted `main` pushes and manual dispatches.

**Tech Stack:** Nix flakes, GitHub Actions composite actions, Determinate Nix installer, `cachix/cachix-action`.

## Global Constraints

- Cache name: `knirski-subsloth`.
- Cache signing key: `knirski-subsloth.cachix.org-1:3Dn6262rKxqcVYqNA9OJansYszD3OsgI7SEJvn+JQQ4=`.
- Never commit or print `CACHIX_AUTH_TOKEN`.
- Pass the token only for `push` to `refs/heads/main`; pull requests remain pull-only.
- Preserve all existing Gradle CI jobs and triggers.
- Use immutable action revisions matching `lm-bot`.

---

### Task 1: Configure the flake substituter

**Files:** Modify `flake.nix` after `inputs` and before `outputs`.

- [ ] Add `nixConfig.extra-substituters` entries for `https://cache.nixos.org` and `https://knirski-subsloth.cachix.org`.
- [ ] Add the NixOS trusted key and `knirski-subsloth.cachix.org-1:3Dn6262rKxqcVYqNA9OJansYszD3OsgI7SEJvn+JQQ4=` to `extra-trusted-public-keys`.
- [ ] Run `nix flake show --json`; expect valid JSON containing `devShells.x86_64-linux.default`.
- [ ] Commit with `ci: configure SubSloth Cachix substituter`.

### Task 2: Add the reusable setup action

**Files:** Create `.github/actions/setup-nix/action.yml`.

- [ ] Define optional input `cachix-auth-token`, defaulting to empty.
- [ ] Add pinned `DeterminateSystems/nix-installer-action` v22 and `DeterminateSystems/magic-nix-cache-action` v14, with `use-flakehub: false`.
- [ ] Add pinned `cachix/cachix-action` v17 twice: empty-token branch configures pull-only cache `knirski-subsloth`; non-empty-token branch passes `authToken: '${{ inputs.cachix-auth-token }}'`.
- [ ] Copy the immutable revisions from `/home/krzysiek/github/knirski/lm-bot/.github/actions/setup-nix/action.yml`, correcting only the cache name.
- [ ] Run `nix run nixpkgs#actionlint -- .github/actions/setup-nix/action.yml`.
- [ ] Commit with `ci: add reusable Nix Cachix setup`.

### Task 3: Add the cache-devShell workflow job

**Files:** Modify `.github/workflows/ci.yml`.

- [ ] Add `workflow_dispatch` while retaining existing pull-request and main-push triggers.
- [ ] Add independent job `cache-devshell` on `ubuntu-latest`.
- [ ] Check out with `persist-credentials: false`, invoke `./.github/actions/setup-nix`, and pass the secret only when `github.event_name == 'push' && github.ref == 'refs/heads/main'`.
- [ ] On main pushes only, realize the devShell with `DEV_SHELL=$(nix build --no-link --print-out-paths .#devShells.x86_64-linux.default)` and push the resulting store path with `cachix push knirski-subsloth "$DEV_SHELL"`.
- [ ] Keep manual dispatch pull-only and leave all existing Gradle jobs unchanged.
- [ ] Run `nix run nixpkgs#actionlint -- .github/workflows/ci.yml`.
- [ ] Confirm `rg -n 'CACHIX_AUTH_TOKEN|authToken|knirski-subsloth' .github flake.nix` contains no literal token.
- [ ] Commit with `ci: publish devShell to Cachix on main`.

### Task 4: Final verification

**Files:** Verify `flake.nix`, `.github/actions/setup-nix/action.yml`, and `.github/workflows/ci.yml`.

- [ ] Run `nix flake check --no-build`; expect successful evaluation.
- [ ] Run `nix run nixpkgs#actionlint -- .github/actions/setup-nix/action.yml .github/workflows/ci.yml`; expect no errors.
- [ ] Run `git diff --check`.
- [ ] Inspect `git status --short --branch` and `git diff main...HEAD --stat`; ensure only the approved design, plan, flake, action, and workflow files changed.
