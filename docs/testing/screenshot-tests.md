# Screenshot Tests

The project uses Android's Compose Preview Screenshot Testing framework for visual regression testing. All screens are captured at three device form-factors: Phone, Tablet, and TV.

> **Note:** This is an Android-only testing framework (not CMP). Screenshot tests live in `:androidApp`'s `screenshotTest` source set and run via `connectedDebugAndroidTest`.

---

## Setup

The `:androidApp` module is configured for screenshot testing in its `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.compose.screenshot)     // enables screenshot testing
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.kotlinx.collections.immutable)
}
```

---

## Pattern

Each screenshot test is a Composable function annotated with `@PreviewTest` and `@Preview`:

```kotlin
package net.subsloth.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Phone", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun MyScreenScreenshot() {
    MaterialTheme {
        // Your composable with test data
    }
}
```

Key elements:
- **`@PreviewTest`** — marks the function as a screenshot test case
- **`@Preview(name = ..., device = ...)`** — one annotation per device form-factor. The framework renders each one and compares against the golden image.
- **`MaterialTheme`** — wrap content in the app theme for accurate rendering
- **Test data** — pass fake/example data to the composable (no network calls)

---

## Running Screenshot Tests

### On an Emulator or Physical Device

```bash
# Start emulator (first time only)
start-subsloth-emulator

# Run screenshot tests
./gradlew :androidApp:connectedDebugAndroidTest

# Stop emulator
stop-subsloth-emulator
```

### CI

A dedicated [`Screenshots`](/.github/workflows/screenshots.yml) workflow
can be triggered manually (`workflow_dispatch`) in two modes:

| Mode | Action |
|------|--------|
| `verify` | Run `:androidApp:connectedDebugAndroidTest` — compares rendered previews against stored golden images (fails on pixel diff) |
| `update` | Run with `-Pandroid.test.screenshot.update.golden=true` to regenerate goldens, then export selected images to `docs/screenshots/` for README.md, and commit both |

Use `verify` on any branch to check whether UI changes drifted from the
committed goldens. Use `update` after intentionally redesigning a screen —
it handles the full pipeline: emulator boot → regolden → export → commit.

> **Note:** This workflow is manually triggered (`workflow_dispatch`) rather
> than running automatically on every PR because screenshot tests require an
> Android emulator (~5-10 min boot + run). Run `update` once to bootstrap
> the golden images before using `verify`. Once goldens exist in the repo,
> the workflow can be added to `ci.yml` as a path-gated job.

---

## Updating Golden Images

When a UI change intentionally alters a screen's appearance, the golden images must be updated:

```bash
# Run with update flag
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.test.screenshot.update.golden=true
```

This replaces the stored golden images with the newly rendered output. **Commit the updated golden images alongside the UI change.**

### Golden Image Location

Golden images are stored in the repository at:
```
androidApp/src/screenshotTest/goldens/
```

Each golden is a PNG file named after the test function and device variant (e.g. `LoginScreenScreenshot_Phone.png`).

---

## Existing Screenshot Tests

| File | Screens | Device Variants |
|---|---|---|
| `LoginScreenScreenshotTest.kt` | Login form | Phone, Tablet, TV |
| `HomeScreenScreenshotTest.kt` | Catalog home with movie/show rows | Phone, Tablet, TV |
| `SearchScreenScreenshotTest.kt` | Search screen | Phone, Tablet, TV |
| `MovieDetailScreenshotTest.kt` | Movie detail view | Phone, Tablet, TV |
| `SeriesDetailScreenshotTest.kt` | Series/episode detail view | Phone, Tablet, TV |
| `PlayerScreenScreenshotTest.kt` | Video player UI | Phone, Tablet, TV |
| `LibraryScreenScreenshotTest.kt` | Library screen | Phone, Tablet, TV |
| `DownloadsScreenScreenshotTest.kt` | Downloads screen | Phone, Tablet, TV |
| `SettingsScreenScreenshotTest.kt` | Settings screen | Phone, Tablet, TV |
| `DiagnosticsScreenScreenshotTest.kt` | Diagnostics screen | Phone, Tablet, TV |

---

## Adding a New Screenshot Test

1. Create `androidApp/src/screenshotTest/kotlin/net/subsloth/screenshot/{ScreenName}ScreenshotTest.kt`
2. Follow the pattern above with `@PreviewTest`, `@Preview` annotations for all three device specs, and `MaterialTheme` wrapping
3. Provide realistic test data using the project's domain types (`MovieSummary`, `ShowSummary`, etc.)
4. Run on an emulator to generate the initial golden images
5. Verify all three variants render correctly
6. Commit the test file and golden images together

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| Test fails with image diff | UI changed intentionally | Update goldens with `-Pandroid.test.screenshot.update.golden=true` |
| Test fails with image diff | UI changed unintentionally | Fix the UI regression |
| `@PreviewTest` annotation not found | Missing `compose.screenshot` plugin | Verify `alias(libs.plugins.compose.screenshot)` in `:androidApp/build.gradle.kts` |
| Golden image not found for new test | First run | Run once to generate, then commit the goldens |
| Test failed to render | Composable throws during preview | Check for platform APIs (Context, Intent) used without guards |
