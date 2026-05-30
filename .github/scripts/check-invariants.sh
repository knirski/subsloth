#!/usr/bin/env bash
#
# check-invariants.sh
#
# Invariant and hygiene checks for the subsloth CI pipeline.
#
# 1. Sensitive artifact / credential scanning
# 2. Kodi-parity basic request-behaviour patterns
#
# Exits with a non-zero status if any check fails.

set -o errexit -o nounset -o pipefail

rc=0
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

# ----
# Helper: log a failure
# ----
fail() {
  local category="$1" msg="$2"
  rc=1
  printf "[FAIL][%s] %s\n" "$category" "$msg"
}

# ----
# 1. Sensitive artifact / credential scanning
# ----
check_secrets() {
  # 1a. Media credentials in non-shell-non-env files
  while IFS=: read -r file line content; do
    fail "SECRETS" "$file:$line — Media credential literal: $content"
  done < <(
    grep -rnwI -E '(SUBSLOTH_LOGIN|SUBSLOTH_PASSWORD)\s*=' \
      --include='*.kt' --include='*.java' --include='*.kts' \
      --include='*.xml' --include='*.json' --include='*.yaml' --include='*.yml' \
      --include='*.properties' --include='*.gradle' \
      "$repo_root" 2>/dev/null || true
  )

  # 1b. Basic auth header literals in source
  while IFS=: read -r file line content; do
    fail "SECRETS" "$file:$line — Basic auth header literal: $content"
  done < <(
    grep -rnwI -E 'Authorization:\s*Basic\s+' \
      --include='*.kt' --include='*.java' --include='*.kts' \
      --include='*.xml' --include='*.json' --include='*.yaml' --include='*.yml' \
      "$repo_root" 2>/dev/null || true
  )

  # 1c. Signed-media-URL patterns in source
  # The OpenAPI spec yaml may use "signed" in documentation descriptions;
  # that is not a real signed-URL leak, so we exclude it.
  local pattern='(token=|signature=|X-Amz-Signature|X-Amz-Credential)\S'
  local hits
  hits=$(grep -rnwI -E "$pattern" \
      --include='*.kt' --include='*.java' --include='*.kts' \
      --include='*.xml' --include='*.json' --include='*.yaml' --include='*.yml' \
      "$repo_root" 2>/dev/null \
    || true
  )
  hits=$(echo "$hits" \
    | grep -v -F '/api/subsloth.openapi.yaml:' \
    | grep -v -F 'check-invariants.sh:' \
    | grep -v -F 'secret-scanning.md:' \
    | grep -v -F 'docs/agent/' \
    || true
  )
  if [ -n "$hits" ]; then
    while IFS=: read -r file line content; do
      fail "SECRETS" "$file:$line — signed-media-URL token: $content"
    done <<< "$hits"
  fi

  # 1d. .playwright-cli/ directory
  if [ -d "$repo_root/.playwright-cli" ]; then
    fail "SECRETS" ".playwright-cli/ directory exists — contains authenticated capture artifacts"
  fi

  # 1e. HAR files
  while IFS= read -r -d '' har; do
    fail "SECRETS" "$har — HAR file contains raw HTTP traces"
  done < <(find "$repo_root" -name '*.har' -not -path '*/node_modules/*' -not -path '*/.git/*' -print0 2>/dev/null || true)

  # 1f. Browser traces (Chrome DevTools traces)
  while IFS= read -r -d '' trace; do
    fail "SECRETS" "$trace — browser trace file"
  done < <(find "$repo_root" \( -name 'trace-*.json' -o -name '*.trace' \) -not -path '*/node_modules/*' -not -path '*/.git/*' -print0 2>/dev/null || true)

  # 1g. Screenshots committed outside allowed test assets
  while IFS= read -r -d '' scrot; do
    fail "SECRETS" "$scrot — screenshot / image likely captured from authenticated session"
  done < <(
    find "$repo_root" \
      \( -name 'screenshot*.png' -o -name 'screenshot*.jpg' -o -name 'screenshot*.jpeg' \
         -o -name 'screen*.png' -o -name 'screen*.jpg' -o -name 'screen*.jpeg' \) \
      -not -path '*/node_modules/*' -not -path '*/.git/*' \
      -not -path '*/testing/*' -not -path '*/src/test/*' -not -path '*/src/androidTest/*' \
      -print0 2>/dev/null || true
  )

  # 1h. Signed APK / keystore material
  while IFS= read -r -d '' ks; do
    fail "SECRETS" "$ks — signing key / keystore file"
  done < <(
    find "$repo_root" \
      \( -name '*.jks' -o -name '*.keystore' -o -name '*.pk8' -o -name '*.pem' \) \
      -not -path '*/node_modules/*' -not -path '*/.git/*' \
      -print0 2>/dev/null || true
  )
}

# ----
# 2. Kodi-parity request-behaviour patterns
#    Ensure network code does not reference non-Kodi API hosts or
#    incompatible endpoint patterns.
# ----
check_kodi_parity() {
  local network_src="$repo_root/core/network/src"
  if [ -d "$network_src" ]; then
    while IFS=: read -r file line content; do
      # Allow test fixture URLs (they use .invalid / localhost)
      case "$file" in
        */test/*|*/androidTest/*) continue ;;
      esac
      fail "KODI_PARITY" "$file:$line — non-Kodi host reference in network code: $content"
    done < <(
      grep -rnwI -E '"[^"]*https?://[^"]*\.(com|org|net|io|app|dev)[^"]*"' \
        "$network_src" 2>/dev/null || true
    )
  fi
}

# ----
# 3. Banned-dependency lint check.
#    Ensure production source does not import libraries that have been
#    superseded by project policy (see docs/agent/fc-is-architecture.md).
# ----
check_banned_deps() {
  # Pattern: block `import arrow.`, `import dagger.`, `import javax.inject.`,
  # `import com.squareup.moshi.`, `import com.google.gson.`, `import io.kotest.`,
  # `import io.reactivex.` in non-test source.
  local dir
  for dir in app core feature; do
    target="$repo_root/$dir"
    if [ ! -d "$target" ]; then
      continue
    fi
    while IFS=: read -r file line content; do
      fail "BANNED_DEP" "$file:$line — banned dependency import: $content"
    done < <(
      grep -rnwI -E \
        '^import\s+(arrow\.|dagger\.|javax\.inject\.|com\.squareup\.moshi\.|com\.google\.gson\.|io\.kotest\.|io\.reactivex\.|androidx\.navigation\.compose\.)' \
        --include='*.kt' --include='*.java' \
        "$target" 2>/dev/null \
      | grep -v '/src/test/' \
      | grep -v '/src/androidTest/' \
      || true
    )
  done
}

# ----
# Run all checks
# ----
check_secrets
check_banned_deps
check_kodi_parity

if [ "$rc" -eq 0 ]; then
  printf "OK — all invariant checks passed.\n"
else
  printf "\nFAIL — one or more invariant checks failed.\n"
fi

exit "$rc"
