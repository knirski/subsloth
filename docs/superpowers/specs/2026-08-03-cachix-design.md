# Cachix Binary Cache Integration

## Goal

Configure SubSloth to consume the `knirski-subsloth` Cachix binary cache in
local Nix workflows and publish the x86_64 Linux development-shell closure from
GitHub Actions on pushes to `main`.

## Design

The flake will declare the Cachix substituter and trusted public signing key in
`nixConfig`, matching the existing `lm-bot` pattern. This makes `nix develop`
and other flake commands eligible to reuse cached store paths without exposing
the write token.

GitHub Actions will gain a reusable `.github/actions/setup-nix/action.yml`
composite action. It will pin the Determinate Nix installer, magic-nix-cache,
and Cachix action versions. With no token it configures pull-only access; with
the `CACHIX_AUTH_TOKEN` input it enables authenticated pushes. The token will be
passed only for a `main` push, never for pull requests or fork builds.

The CI workflow will add an independent `cache-devshell` job. It will run on
`main` pushes and manual dispatches, install Nix through the composite action,
realize the complete `.#devShells.x86_64-linux.default` closure into a profile,
and push that profile only on trusted `main` pushes. Manual dispatch remains
pull-only and still realizes the profile for verification. The job remains
separate from the existing Gradle jobs so their behavior is unchanged.

## Security and compatibility

- The cache name and public signing key are committed; the auth token is not.
- Pull-only cache configuration is used when no token is available.
- The secret expression is restricted to `push` events targeting `refs/heads/main`.
- GitHub Action references are pinned to immutable revisions, following the
  source repository’s Cachix integration pattern.
- The existing x86_64-only flake output is used; no new platform outputs are
  introduced.

## Verification

- Validate YAML and composite-action syntax with `actionlint`.
- Evaluate the flake with `nix flake check` or the narrowest available flake
  evaluation.
- Verify the workflow contains the expected secret guard and devShell push
  target without running a real authenticated upload locally.
- Run `git diff --check` and inspect the final diff for secret leakage.
