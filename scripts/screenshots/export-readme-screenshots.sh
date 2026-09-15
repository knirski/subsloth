#!/usr/bin/env bash
#
# export-readme-screenshots.sh
#
# Copies selected golden images from the Compose Preview Screenshot Testing
# framework to docs/screenshots/ for use in README.md.
#
# Run AFTER regenerating goldens:
#   ./gradlew :androidApp:updateDebugScreenshotTest
#
# Usage:
#   ./scripts/screenshots/export-readme-screenshots.sh
#
# Mapping:
#   The compose-screenshot plugin writes reference images as
#     {FunctionName}_{PreviewName} {Theme}_{hash}_0.png
#   under androidApp/src/screenshotTestDebug/reference/…, so each mapping
#   entry below is matched as a filename prefix.
#
# Only the subset used in README.md is exported.  Update the MAPPING
# array below when adding/removing screenshots from the README.
#

set -o errexit -o nounset -o pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
reference_dir="$repo_root/androidApp/src/screenshotTestDebug/reference"
docs_dir="$repo_root/docs/screenshots"

if [[ ! -d "$reference_dir" ]]; then
  echo "[ERR] Reference image directory not found at $reference_dir"
  echo "      Run the Screenshots workflow in update mode (or"
  echo "      ./gradlew :androidApp:updateDebugScreenshotTest) first."
  exit 1
fi

mkdir -p "$docs_dir"

# ── Mapping: golden filename → docs/screenshots/ filename ──────────────
# These map the @PreviewTest function + @Preview name to the README asset.
declare -A MAPPING=(
  # Home — all three form factors, both themes
  ["HomeScreenLightScreenshot_Phone.png"]="home-light-phone.png"
  ["HomeScreenLightScreenshot_Tablet.png"]="home-light-tablet.png"
  ["HomeScreenLightScreenshot_TV.png"]="home-light-tv.png"
  ["HomeScreenDarkScreenshot_Phone.png"]="home-dark-phone.png"
  ["HomeScreenDarkScreenshot_Tablet.png"]="home-dark-tablet.png"
  ["HomeScreenDarkScreenshot_TV.png"]="home-dark-tv.png"

  # Movie detail — phone only, both themes
  ["MovieDetailLightScreenshot_Phone.png"]="movie-light-phone.png"
  ["MovieDetailDarkScreenshot_Phone.png"]="movie-dark-phone.png"

  # Series detail — tablet only, both themes
  ["SeriesDetailLightScreenshot_Tablet.png"]="series-light-tablet.png"
  ["SeriesDetailDarkScreenshot_Tablet.png"]="series-dark-tablet.png"

  # Player — phone only, both themes
  ["PlayerScreenLightScreenshot_Phone.png"]="player-light-phone.png"
  ["PlayerScreenDarkScreenshot_Phone.png"]="player-dark-phone.png"

  # Downloads — phone only, both themes
  ["DownloadsScreenLightScreenshot_Phone.png"]="downloads-light-phone.png"
  ["DownloadsScreenDarkScreenshot_Phone.png"]="downloads-dark-phone.png"
)

copied=0
missing=0

for golden_name in "${!MAPPING[@]}"; do
  docs_name="${MAPPING[$golden_name]}"

  # Reference images carry a trailing theme/hash suffix and live in a
  # per-class subdirectory; match the mapped name as a prefix.
  src=$(find "$reference_dir" -type f -name "${golden_name%.png}*" -print -quit 2>/dev/null || true)

  if [[ -z "$src" ]]; then
    echo "[WARN] Golden not found: $golden_name — skipping $docs_name"
    missing=$((missing + 1))
    continue
  fi

  if [[ ! -f "$src" ]]; then
    echo "[WARN] Golden path is not a file: $src — skipping $docs_name"
    missing=$((missing + 1))
    continue
  fi

  cp "$src" "$docs_dir/$docs_name"
  echo "[OK]   $golden_name → $docs_name"
  copied=$((copied + 1))
done

echo ""
echo "Done — $copied screenshots exported, $missing missing."

if (( missing > 0 )); then
  echo "Generate missing goldens by running screenshot tests with the update flag."
  if (( copied == 0 )); then
    echo "[WARN] No screenshots exported — this is expected on first run before goldens are generated."
  else
    exit 1
  fi
fi
