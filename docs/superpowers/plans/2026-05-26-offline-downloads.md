# Offline Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `offline-downloads` OpenSpec change on top of current `main`, covering offline home mode, app-private downloads, subtitle sidecars, item downloads, confirmed season queues, queue persistence, and operational notifications.

**Architecture:** Keep decision logic in `:core:model` and `:core:domain`, then add the imperative download shell in `:core:media` plus persisted offline state in `:core:database`. Model lifecycle and user-visible outcomes as sealed ADTs instead of enums plus nullable baggage, and keep shell ports typed with explicit request and outcome models. Wire the result into `feature:details`, `feature:player`, `feature:auth`, `feature:catalog`, `feature:library`, and `app` without introducing a second requirements system or broad refactors outside the active change.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Media3, Android foreground services, immutable collections, typed `Result` errors, Navigation3

---

## File Structure

### New Files

- `core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadFailureReason.kt`
  User-visible blocked/failure reasons for downloads and local playback.
- `core/model/src/main/kotlin/net/subsloth/core/model/download/OfflineAsset.kt`
  Shared offline video and subtitle-sidecar summary used by UI and shell code.
- `core/model/src/main/kotlin/net/subsloth/core/model/download/SeasonDownloadQueue.kt`
  Persisted season queue, queue item, and confirmation summary types.
- `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/OfflineHomePolicy.kt`
  Pure policy for surfacing Offline Library before failed online states.
- `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/SeasonQueuePolicy.kt`
  Pure policy for fallback selection, confirmation summaries, and resume eligibility.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/OpaquePathPolicy.kt`
  Generates app-private download paths with allowed opaque components only.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/PathRedactor.kt`
  Redacts absolute local file paths for logs and diagnostics.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/OfflineAssetStore.kt`
  Stages, verifies, promotes, and removes offline video and subtitle assets.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadCoordinator.kt`
  Orchestrates item downloads, pause/resume/cancel/retry, and single-active-video enforcement.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadForegroundService.kt`
  `dataSync` foreground service for active visible downloads.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadNotificationFactory.kt`
  Minimal operational notifications for active downloads.
- `core/media/src/main/kotlin/net/subsloth/core/media/download/SeasonQueueExecutor.kt`
  Sequential execution of confirmed season queues.
- `feature/library/src/main/kotlin/net/subsloth/library/OfflineLibraryViewModel.kt`
  View model for logged-out and offline available downloads.
- `feature/library/src/main/kotlin/net/subsloth/library/OfflineLibraryScreen.kt`
  Offline Library surface.
- `feature/library/src/main/kotlin/net/subsloth/library/DownloadsViewModel.kt`
  View model for active queues and explicit recovery actions.
- `feature/library/src/main/kotlin/net/subsloth/library/DownloadsScreen.kt`
  Downloads management screen.
- `feature/library/src/main/res/values/strings.xml`
  Offline/download screen copy.

### Existing Files To Modify

- `core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadState.kt`
- `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`
- `core/model/src/main/kotlin/net/subsloth/core/model/playback/VideoSource.kt`
- `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/DownloadPolicy.kt`
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt`
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/StoragePort.kt`
- `core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt`
- `core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt`
- `core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt`
- `core/media/src/main/AndroidManifest.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `feature/details/src/main/kotlin/net/subsloth/details/DetailViewModels.kt`
- `feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeViewModel.kt`
- `feature/player/src/main/kotlin/net/subsloth/player/PlayerViewModel.kt`
- `feature/auth/src/main/kotlin/net/subsloth/auth/LoginViewModel.kt`
- `app/src/main/java/net/subsloth/SubSlothNavHost.kt`
- `core/ui/src/main/kotlin/net/subsloth/core/ui/UiErrorResources.kt`
- `core/ui/src/main/res/values/strings.xml`

### Tests

- `core/model/src/test/kotlin/net/subsloth/core/model/CoreModelTest.kt`
- `core/domain/src/test/kotlin/net/subsloth/core/domain/policy/DownloadPolicyTest.kt`
- `core/domain/src/test/kotlin/net/subsloth/core/domain/policy/StorageCleanupPolicyTest.kt`
- `core/domain/src/test/kotlin/net/subsloth/core/domain/policy/SeasonQueuePolicyTest.kt`
- `core/media/src/test/kotlin/net/subsloth/core/media/download/OpaquePathPolicyTest.kt`
- `core/media/src/test/kotlin/net/subsloth/core/media/download/OfflineAssetStoreTest.kt`
- `core/media/src/test/kotlin/net/subsloth/core/media/download/DownloadCoordinatorTest.kt`
- `feature/details/src/test/kotlin/net/subsloth/details/MovieDetailViewModelTest.kt`
- `feature/details/src/test/kotlin/net/subsloth/details/SeriesDetailViewModelTest.kt`
- `feature/catalog/src/test/kotlin/net/subsloth/catalog/HomeViewModelTest.kt`
- `feature/player/src/test/kotlin/net/subsloth/player/PlayerViewModelTest.kt`
- `feature/auth/src/test/kotlin/net/subsloth/auth/LoginViewModelTest.kt`
- `feature/library/src/test/kotlin/net/subsloth/library/OfflineLibraryViewModelTest.kt`
- `feature/library/src/test/kotlin/net/subsloth/library/DownloadsViewModelTest.kt`

---

### Task 1: Expand Core Download Models

**Files:**
- Create: `core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadFailureReason.kt`
- Create: `core/model/src/main/kotlin/net/subsloth/core/model/download/OfflineAsset.kt`
- Create: `core/model/src/main/kotlin/net/subsloth/core/model/download/SeasonDownloadQueue.kt`
- Modify: `core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadState.kt`
- Modify: `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`
- Modify: `core/model/src/main/kotlin/net/subsloth/core/model/playback/VideoSource.kt`
- Test: `core/model/src/test/kotlin/net/subsloth/core/model/CoreModelTest.kt`

- [ ] **Step 1: Write the failing core-model tests**

```kotlin
@Test
fun `download state exposes offline lifecycle variants without nullable baggage`() {
    val completed: DownloadState = DownloadState.Completed(
        localId = LocalMediaIdentifier("movie-7"),
        mediaId = Media.MediaId.Movie(MovieId(7)),
        quality = qualityDescriptor(Resolution.FULL_HD, "1080p"),
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(10),
        sizeBytes = 1024L,
        videoPath = OfflineRelativePath("downloads/video/7/main.mp4"),
        subtitleLanguages = persistentSetOf(),
    )
    val unavailable: DownloadState = DownloadState.Unavailable(
        localId = LocalMediaIdentifier("movie-7"),
        mediaId = Media.MediaId.Movie(MovieId(7)),
        quality = qualityDescriptor(Resolution.HD_720, "720p"),
        reason = DownloadFailureReason.MissingLocalFile,
    )
    assertThat(completed).isInstanceOf(DownloadState.Completed::class.java)
    assertThat(unavailable).isInstanceOf(DownloadState.Unavailable::class.java)
}

@Test
fun `offline asset keeps subtitle sidecars separate from video asset`() {
    val asset = OfflineAsset(
        mediaId = Media.MediaId.Movie(MovieId(7)),
        localId = LocalMediaIdentifier("movie-7"),
        videoRelativePath = OfflineRelativePath("downloads/video/7/main.mp4"),
        subtitleLanguages = persistentSetOf(LanguageCode("en"), LanguageCode("pl")),
        effectiveQuality = qualityDescriptor(Resolution.FULL_HD, "1080p"),
        displayTitle = "Movie",
        isPlayable = true,
    )
    assertThat(asset.subtitleLanguages).contains(LanguageCode("en"))
    assertThat(asset.subtitleLanguages).contains(LanguageCode("pl"))
}

@Test
fun `season queue summary tracks fallback and blocked counts`() {
    val summary = SeasonDownloadConfirmation(
        episodeCount = 8,
        alreadyAvailableCount = 2,
        fallbackQualityCount = 1,
        fallbackSubtitleToEnglishCount = 3,
        noSubtitleCount = 1,
        unavailableCount = 1,
        sizeEstimate = SizeEstimate.Unknown,
        transferPreference = TransferPreference.WifiOnly,
    )
    assertThat(summary.fallbackSubtitleToEnglishCount).isEqualTo(3)
    assertThat(summary.noSubtitleCount).isEqualTo(1)
}
```

- [ ] **Step 2: Run the model tests to verify they fail**

Run: `./gradlew :core:model:test --tests "net.subsloth.core.model.CoreModelTest"`
Expected: FAIL because the new sealed download lifecycle and queue model types do not exist yet.

- [ ] **Step 3: Implement the model types**

```kotlin
// core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadFailureReason.kt
sealed interface DownloadFailureReason {
    data object NeedsWifi : DownloadFailureReason
    data object InsufficientStorage : DownloadFailureReason
    data object MissingLocalFile : DownloadFailureReason
    data object SubtitleUnavailable : DownloadFailureReason
    data object AmbiguousQuality : DownloadFailureReason
    data object DownloadFailed : DownloadFailureReason
    data object Unavailable : DownloadFailureReason
}

// core/model/src/main/kotlin/net/subsloth/core/model/download/OfflineAsset.kt
@JvmInline
value class QueueId(val value: String)

@JvmInline
value class OfflineRelativePath(val value: String)

sealed interface TransferPreference {
    data object WifiOnly : TransferPreference
    data object MeteredAllowed : TransferPreference
}

sealed interface SizeEstimate {
    data class Known(val bytes: Long) : SizeEstimate
    data object Unknown : SizeEstimate
}

sealed interface SubtitleSelection {
    data class Preferred(val subtitle: Subtitle) : SubtitleSelection
    data class EnglishFallback(val subtitle: Subtitle) : SubtitleSelection
    data object None : SubtitleSelection
}

@Immutable
data class OfflineAsset(
    val mediaId: Media.MediaId,
    val localId: LocalMediaIdentifier,
    val videoRelativePath: OfflineRelativePath,
    val subtitleLanguages: ImmutableSet<LanguageCode>,
    val effectiveQuality: QualityDescriptor,
    val displayTitle: String,
    val isPlayable: Boolean,
)

// core/model/src/main/kotlin/net/subsloth/core/model/download/SeasonDownloadQueue.kt
@Immutable
data class SeasonDownloadQueue(
    val queueId: QueueId,
    val showId: ShowId,
    val seasonNumber: Int,
    val items: ImmutableList<SeasonDownloadQueueItem>,
    val execution: SeasonQueueExecution,
    val transferPreference: TransferPreference,
)

sealed interface SeasonQueueExecution {
    data object PendingConfirmation : SeasonQueueExecution
    data object Queued : SeasonQueueExecution
    data class Running(val activeItem: Media.MediaId.Episode) : SeasonQueueExecution
    data class Paused(val reason: DownloadFailureReason) : SeasonQueueExecution
    data object Completed : SeasonQueueExecution
    data class Failed(val reason: DownloadFailureReason) : SeasonQueueExecution
}

@Immutable
data class SeasonDownloadQueueItem(
    val mediaId: Media.MediaId.Episode,
    val selectedQuality: Resolution,
    val preferredSubtitleLanguage: LanguageCode,
    val subtitleSelection: SubtitleSelection,
    val execution: SeasonQueueItemExecution,
)

sealed interface SeasonQueueItemExecution {
    data object Pending : SeasonQueueItemExecution
    data class Downloading(val progressPercent: Int) : SeasonQueueItemExecution
    data object Completed : SeasonQueueItemExecution
    data class Failed(val reason: DownloadFailureReason) : SeasonQueueItemExecution
    data object Cancelled : SeasonQueueItemExecution
}

@Immutable
data class SeasonDownloadConfirmation(
    val episodeCount: Int,
    val alreadyAvailableCount: Int,
    val fallbackQualityCount: Int,
    val fallbackSubtitleToEnglishCount: Int,
    val noSubtitleCount: Int,
    val unavailableCount: Int,
    val sizeEstimate: SizeEstimate,
    val transferPreference: TransferPreference,
)
```

- [ ] **Step 4: Extend the existing types instead of introducing parallel state**

```kotlin
// core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadState.kt
sealed interface DownloadState {
    val localId: LocalMediaIdentifier
    val mediaId: Media.MediaId
    val quality: QualityDescriptor
    val subtitleLanguages: ImmutableSet<LanguageCode>

    @Immutable
    data class Queued(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Active(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val progressPercent: Int,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Partial(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val stagedPath: OfflineRelativePath,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Completed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        val downloadedAtEpochSeconds: Instant,
        val sizeBytes: Long?,
        val videoPath: OfflineRelativePath,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
    ) : DownloadState

    @Immutable
    data class Failed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Paused(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Unavailable(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Removed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
    ) : DownloadState
}

// core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt
sealed interface DownloadError : DomainError {
    data object InsufficientStorage : DownloadError
    data object MissingSubtitle : DownloadError
    data object QueueFull : DownloadError
    data object NeedsWifi : DownloadError
    data object MissingLocalFile : DownloadError
    data object AmbiguousQuality : DownloadError
}

// core/model/src/main/kotlin/net/subsloth/core/model/playback/VideoSource.kt
data class VideoSource(
    val mediaId: Media.MediaId,
    val streamUrl: String,
    val selectedQuality: Quality,
    val availableQualities: ImmutableList<Quality>,
    val availableSubtitles: ImmutableList<Subtitle>,
    val durationSeconds: Long,
    val playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    val localId: LocalMediaIdentifier? = null,
)
```

- [ ] **Step 5: Run the model tests to verify they pass**

Run: `./gradlew :core:model:test --tests "net.subsloth.core.model.CoreModelTest"`
Expected: PASS

- [ ] **Step 6: Commit the model phase**

```bash
git add core/model/src/main/kotlin/net/subsloth/core/model/download \
  core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt \
  core/model/src/main/kotlin/net/subsloth/core/model/playback/VideoSource.kt \
  core/model/src/test/kotlin/net/subsloth/core/model/CoreModelTest.kt
git commit -m "feat(model): add offline download models"
```

### Task 2: Add Pure Offline Download Policies

**Files:**
- Create: `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/OfflineHomePolicy.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/SeasonQueuePolicy.kt`
- Modify: `core/domain/src/main/kotlin/net/subsloth/core/domain/policy/DownloadPolicy.kt`
- Modify: `core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt`
- Modify: `core/domain/src/main/kotlin/net/subsloth/core/domain/port/StoragePort.kt`
- Test: `core/domain/src/test/kotlin/net/subsloth/core/domain/policy/DownloadPolicyTest.kt`
- Test: `core/domain/src/test/kotlin/net/subsloth/core/domain/policy/SeasonQueuePolicyTest.kt`

- [ ] **Step 1: Add failing policy tests for reserve bytes, subtitle fallback, and confirmation summaries**

```kotlin
@Test
fun `reserve bytes uses smaller of two gigabytes and ten percent`() {
    assertThat(DownloadPolicy.requiredReserveBytes(totalBytes = 64L * GB)).isEqualTo(2L * GB)
    assertThat(DownloadPolicy.requiredReserveBytes(totalBytes = 8L * GB)).isEqualTo(800L * MB)
}

@Test
fun `initial subtitle fallback uses preferred non english then english then none`() {
    val subtitles = listOf(
        subtitle(LanguageCode("en"), "English"),
        subtitle(LanguageCode("pl"), "Polski"),
    )
    val selection = SeasonQueuePolicy.selectInitialSubtitle(
        available = subtitles,
        preferred = LanguageCode("pl"),
    )
    assertThat(selection).isEqualTo(
        SubtitleSelection.Preferred(
            subtitle(LanguageCode("pl"), "Polski"),
        ),
    )
}

@Test
fun `subtitle fallback emits explicit english fallback decision`() {
    val subtitles = listOf(subtitle(LanguageCode("en"), "English"))
    val selection = SeasonQueuePolicy.selectInitialSubtitle(
        available = subtitles,
        preferred = LanguageCode("es"),
    )
    assertThat(selection).isEqualTo(
        SubtitleSelection.EnglishFallback(
            subtitle(LanguageCode("en"), "English"),
        ),
    )
}

@Test
fun `offline home surfaces offline library first when device is offline and downloads exist`() {
    assertThat(
        OfflineHomePolicy.shouldSurfaceOfflineLibrary(
            isOnline = false,
            playableDownloads = 3,
        ),
    ).isTrue()
}
```

- [ ] **Step 2: Run the pure-domain tests to verify they fail**

Run: `./gradlew :core:domain:test --tests "net.subsloth.core.domain.policy.*"`
Expected: FAIL because the new reserve, fallback, and offline-home policy functions do not exist yet.

- [ ] **Step 3: Implement the pure policy layer**

```kotlin
// core/domain/src/main/kotlin/net/subsloth/core/domain/policy/OfflineHomePolicy.kt
object OfflineHomePolicy {
    fun shouldSurfaceOfflineLibrary(
        isOnline: Boolean,
        playableDownloads: Int,
    ): Boolean = !isOnline && playableDownloads > 0
}

// core/domain/src/main/kotlin/net/subsloth/core/domain/policy/SeasonQueuePolicy.kt
object SeasonQueuePolicy {
    fun selectInitialSubtitle(
        available: List<Subtitle>,
        preferred: LanguageCode,
    ): SubtitleSelection {
        val english = LanguageCode("en")
        val preferredTrack = available.firstOrNull { it.language == preferred }
        val englishTrack = available.firstOrNull { it.language == english }
        return when {
            preferred != english && preferredTrack != null -> SubtitleSelection.Preferred(preferredTrack)
            englishTrack != null -> SubtitleSelection.EnglishFallback(englishTrack)
            else -> SubtitleSelection.None
        }
    }

    fun canResumeQueue(
        isOnline: Boolean,
        hasStorage: Boolean,
        transferPreference: TransferPreference,
        isMetered: Boolean,
        authValid: Boolean,
    ): Boolean = isOnline && hasStorage && authValid && when (transferPreference) {
        TransferPreference.WifiOnly -> !isMetered
        TransferPreference.MeteredAllowed -> true
    }
}

// core/domain/src/main/kotlin/net/subsloth/core/domain/policy/DownloadPolicy.kt
object DownloadPolicy {
    fun requiredReserveBytes(totalBytes: Long): Long =
        minOf(2L * 1024 * 1024 * 1024, totalBytes / 10)

    fun canTransferOnNetwork(
        isMetered: Boolean,
        transferPreference: TransferPreference,
    ): Boolean = when (transferPreference) {
        TransferPreference.WifiOnly -> !isMetered
        TransferPreference.MeteredAllowed -> true
    }

    fun canReplaceQuality(existing: QualityDescriptor, candidate: QualityDescriptor): Boolean =
        candidate.resolution.pixelCount > existing.resolution.pixelCount

    fun hasSufficientStorage(
        availableBytes: Long,
        requiredBytes: Long,
        reserveBytes: Long,
    ): Boolean = availableBytes >= requiredBytes + reserveBytes
}
```

- [ ] **Step 4: Expand the ports only where the new behavior needs new inputs**

```kotlin
// core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt
interface DownloadsPort {
    suspend fun listDownloads(): Result<ImmutableList<DownloadState>>
    suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>>
    suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long? = null,
        transferPreference: TransferPreference = TransferPreference.WifiOnly,
    ): Result<EnqueueOutcome>
    suspend fun enqueueSubtitle(
        localId: LocalMediaIdentifier,
        language: LanguageCode,
    ): Result<SubtitleEnqueueOutcome>
    suspend fun pause(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun resume(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun cancel(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun remove(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
}

sealed interface DownloadCommandOutcome {
    data object Applied : DownloadCommandOutcome
    data object NoOp : DownloadCommandOutcome
}

sealed interface SubtitleEnqueueOutcome {
    data object Queued : SubtitleEnqueueOutcome
    data object AlreadyAvailable : SubtitleEnqueueOutcome
}

// core/domain/src/main/kotlin/net/subsloth/core/domain/port/StoragePort.kt
interface StoragePort {
    fun availableBytes(): Long
    fun totalBytes(): Long
    fun reserveBytes(): Long
}
```

- [ ] **Step 5: Run the policy tests to verify they pass**

Run: `./gradlew :core:domain:test --tests "net.subsloth.core.domain.policy.DownloadPolicyTest" --tests "net.subsloth.core.domain.policy.SeasonQueuePolicyTest"`
Expected: PASS

- [ ] **Step 6: Commit the pure policy phase**

```bash
git add core/domain/src/main/kotlin/net/subsloth/core/domain/policy \
  core/domain/src/main/kotlin/net/subsloth/core/domain/port \
  core/domain/src/test/kotlin/net/subsloth/core/domain/policy
git commit -m "feat(domain): add offline download policies"
```

### Task 3: Implement App-Private Storage And Redaction

**Files:**
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/OpaquePathPolicy.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/PathRedactor.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/OfflineAssetStore.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Test: `core/media/src/test/kotlin/net/subsloth/core/media/download/OpaquePathPolicyTest.kt`
- Test: `core/media/src/test/kotlin/net/subsloth/core/media/download/OfflineAssetStoreTest.kt`

- [ ] **Step 1: Write failing storage tests for opaque paths, sidecar independence, and redaction**

```kotlin
@Test
fun `opaque path uses only allowed components`() {
    val path = OpaquePathPolicy.videoPath(
        contentId = "12345",
        extension = "mp4",
        randomId = UUID.fromString("00000000-0000-0000-0000-000000000111"),
    )
    assertThat(path).isEqualTo("downloads/video/12345/00000000-0000-0000-0000-000000000111.mp4")
}

@Test
fun `redactor removes absolute local path details`() {
    val redacted = PathRedactor.redact("/data/user/0/net.subsloth/files/downloads/video/7/main.mp4")
    assertThat(redacted).isEqualTo("[redacted-local-path]")
}

@Test
fun `subtitle deletion does not delete video asset`() {
    val store = FakeOfflineAssetStore()
    store.saveVideo("movie-7", "downloads/video/7/main.mp4")
    store.saveSubtitle("movie-7", LanguageCode("pl"), "downloads/subtitles/7/pl.srt")
    store.deleteSubtitle("movie-7", LanguageCode("pl"))
    assertThat(store.videoExists("movie-7")).isTrue()
}
```

- [ ] **Step 2: Run the core-media tests to verify they fail**

Run: `./gradlew :core:media:test --tests "net.subsloth.core.media.download.*"`
Expected: FAIL because the storage/redaction classes do not exist yet.

- [ ] **Step 3: Implement path generation and redaction**

```kotlin
// core/media/src/main/kotlin/net/subsloth/core/media/download/OpaquePathPolicy.kt
object OpaquePathPolicy {
    fun videoPath(contentId: String, extension: String, randomId: UUID): OfflineRelativePath =
        OfflineRelativePath("downloads/video/$contentId/$randomId.$extension")

    fun subtitlePath(
        contentId: String,
        language: LanguageCode,
        extension: String,
        randomId: UUID,
    ): OfflineRelativePath = OfflineRelativePath(
        "downloads/subtitles/$contentId/${language.value}/$randomId.$extension",
    )
}

// core/media/src/main/kotlin/net/subsloth/core/media/download/PathRedactor.kt
object PathRedactor {
    fun redact(path: String?): String =
        path
            ?.takeIf { it.isNotBlank() }
            ?.let { "[redacted-local-path]" }
            .orEmpty()
}
```

- [ ] **Step 4: Implement the asset store and keep backup exclusions aligned with the real download directory**

```kotlin
// core/media/src/main/kotlin/net/subsloth/core/media/download/OfflineAssetStore.kt
class OfflineAssetStore(private val filesDir: File) {
    fun stageVideo(relativePath: OfflineRelativePath): File = File(filesDir, "${relativePath.value}.part")
    fun finalVideo(relativePath: OfflineRelativePath): File = File(filesDir, relativePath.value)
    fun verifyPlayable(file: File): Boolean = file.exists() && file.length() > 0L
    fun deletePartial(relativePath: OfflineRelativePath) { File(filesDir, "${relativePath.value}.part").delete() }
}

// app/src/main/res/xml/backup_rules.xml and data_extraction_rules.xml
// Keep excluding the same "downloads/" subtree used by OpaquePathPolicy.
<exclude domain="file" path="downloads/" />
```

- [ ] **Step 5: Run the core-media tests to verify they pass**

Run: `./gradlew :core:media:test --tests "net.subsloth.core.media.download.OpaquePathPolicyTest" --tests "net.subsloth.core.media.download.OfflineAssetStoreTest"`
Expected: PASS

- [ ] **Step 6: Commit the storage phase**

```bash
git add core/media/src/main/kotlin/net/subsloth/core/media/download \
  core/media/src/test/kotlin/net/subsloth/core/media/download \
  app/src/main/res/xml/backup_rules.xml \
  app/src/main/res/xml/data_extraction_rules.xml
git commit -m "feat(media): add app private offline asset storage"
```

### Task 4: Persist Offline Assets And Queue State

**Files:**
- Modify: `core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt`
- Modify: `core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt`
- Modify: `core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt`
- Create: `core/database/src/test/kotlin/net/subsloth/database/OfflineDownloadDaoTest.kt`
- Create: `core/database/schemas/net.subsloth.database.SubSlothDatabase/2.json`

- [ ] **Step 1: Write failing Room tests for shared offline metadata, queue persistence, and subtitle sidecars**

```kotlin
@Test
fun `completed video keeps offline metadata until last shared asset is removed`() = runTest {
    dao.upsertDownloadedMedia(
        DownloadedMediaEntity(
            contentId = "7",
            mediaType = "movie",
            localFilePath = "downloads/video/7/main.mp4",
            sizeBytes = 1024L,
            status = "completed",
            selectedQuality = "1080p",
            downloadedAtEpochSeconds = 10L,
        ),
    )
    metadataDao.upsert(
        OfflineDisplayMetadataEntity(
            contentId = "7",
            title = "Movie",
            posterCacheKey = null,
            backdropCacheKey = null,
            episodeTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            effectiveQuality = "1080p",
            subtitleLanguages = "[\"en\"]",
            durationSeconds = 3600L,
            localProgressSeconds = 0L,
        ),
    )
    assertThat(metadataDao.getByContentId("7")).isNotNull()
}
```

- [ ] **Step 2: Run the database tests to verify they fail**

Run: `./gradlew :core:database:test --tests "net.subsloth.database.OfflineDownloadDaoTest"`
Expected: FAIL because queue persistence tables and DAO methods are missing.

- [ ] **Step 3: Extend the offline entities and DAO layer**

```kotlin
// core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt
@Entity(tableName = "season_download_queue")
data class SeasonDownloadQueueEntity(
    @PrimaryKey val queueId: String,
    val showId: String,
    val seasonNumber: Int,
    val executionTag: String,
    val transferPreferenceTag: String,
)

@Entity(
    tableName = "season_download_queue_item",
    indices = [Index(value = ["queueId", "contentId"], unique = true)],
)
data class SeasonDownloadQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queueId: String,
    val contentId: String,
    val selectedQuality: String,
    val preferredSubtitleLanguage: String,
    val subtitleSelectionTag: String,
    val executionTag: String,
)

// core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt
@Dao
interface SeasonDownloadQueueDao {
    @Query("SELECT * FROM season_download_queue")
    fun getAll(): Flow<List<SeasonDownloadQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeasonDownloadQueueEntity)
}

// Persist strings only at the Room boundary; mappers convert them to sealed ADTs.
private fun SeasonDownloadQueueEntity.toModel(
    items: ImmutableList<SeasonDownloadQueueItem>,
): SeasonDownloadQueue = SeasonDownloadQueue(
    queueId = QueueId(queueId),
    showId = ShowId(showId.toLong()),
    seasonNumber = seasonNumber,
    items = items,
    execution = executionTag.toSeasonQueueExecution(),
    transferPreference = transferPreferenceTag.toTransferPreference(),
)

private fun String.toSeasonQueueExecution(): SeasonQueueExecution = when (this) {
    "pending_confirmation" -> SeasonQueueExecution.PendingConfirmation
    "queued" -> SeasonQueueExecution.Queued
    "completed" -> SeasonQueueExecution.Completed
    else -> error("Unknown season queue execution tag: $this")
}

private fun String.toTransferPreference(): TransferPreference = when (this) {
    "wifi_only" -> TransferPreference.WifiOnly
    "metered_allowed" -> TransferPreference.MeteredAllowed
    else -> error("Unknown transfer preference tag: $this")
}
```

- [ ] **Step 4: Bump the schema version and export the Room schema**

```kotlin
// core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt
@Database(
    entities = [
        // existing entities...
        SeasonDownloadQueueEntity::class,
        SeasonDownloadQueueItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SubSlothDatabase : RoomDatabase() {
    abstract fun seasonDownloadQueueDao(): SeasonDownloadQueueDao
}
```

- [ ] **Step 5: Run the database tests to verify they pass**

Run: `./gradlew :core:database:test --tests "net.subsloth.database.OfflineDownloadDaoTest"`
Expected: PASS

- [ ] **Step 6: Commit the persistence phase**

```bash
git add core/database/src/main/kotlin/net/subsloth/database \
  core/database/src/test/kotlin/net/subsloth/database \
  core/database/schemas/net.subsloth.database.SubSlothDatabase/2.json
git commit -m "feat(database): persist offline assets and season queues"
```

### Task 5: Add Item Download Execution

**Files:**
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadCoordinator.kt`
- Modify: `core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt`
- Test: `core/media/src/test/kotlin/net/subsloth/core/media/download/DownloadCoordinatorTest.kt`

- [ ] **Step 1: Write failing coordinator tests for low-storage refusal, metered confirmation, and safe replacement**

```kotlin
@Test
fun `enqueue refuses download when storage plus reserve is insufficient`() = runTest {
    val coordinator = coordinator(availableBytes = 500L, reserveBytes = 200L)
    val result = coordinator.enqueue(
        movieId = Media.MediaId.Movie(MovieId(7)),
        requested = Resolution.FULL_HD,
        requiredBytes = 400L,
    )
    assertThat(result.exceptionOrNull()).isEqualTo(DownloadError.InsufficientStorage)
}

@Test
fun `existing higher quality asset is reused instead of re downloading lower quality`() = runTest {
    val coordinator = coordinator(existingQuality = qualityDescriptor(Resolution.FULL_HD, "1080p"))
    val result = coordinator.enqueue(
        movieId = Media.MediaId.Movie(MovieId(7)),
        requested = Resolution.HD_720,
    )
    assertThat(result.getOrThrow()).isEqualTo(EnqueueOutcome.AlreadyAvailableHigherQuality)
}

@Test
fun `subtitle sidecar failure does not fail video completion`() = runTest {
    val coordinator = coordinator(sidecarFailure = true)
    val result = coordinator.completeActiveDownload()
    assertThat(result).isEqualTo(
        CompletionOutcome.VideoReady(
            subtitleOutcome = SubtitleSidecarOutcome.Unavailable,
        ),
    )
}
```

- [ ] **Step 2: Run the coordinator tests to verify they fail**

Run: `./gradlew :core:media:test --tests "net.subsloth.core.media.download.DownloadCoordinatorTest"`
Expected: FAIL because the coordinator and enqueue result model do not exist yet.

- [ ] **Step 3: Implement the coordinator around the existing ports and policies**

```kotlin
class DownloadCoordinator(
    private val storagePort: StoragePort,
    private val connectivityPort: ConnectivityPort,
    private val assetStore: OfflineAssetStore,
    private val downloadsDao: DownloadedMediaDao,
) {
    suspend fun enqueue(
        movieId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long? = null,
        transferPreference: TransferPreference = TransferPreference.WifiOnly,
    ): Result<EnqueueOutcome> {
        val reserve = storagePort.reserveBytes()
        val available = storagePort.availableBytes()
        if (requiredBytes != null && !DownloadPolicy.hasSufficientStorage(available, requiredBytes, reserve)) {
            return Result.failure(DownloadError.InsufficientStorage)
        }
        if (!DownloadPolicy.canTransferOnNetwork(connectivityPort.isMetered(), transferPreference)) {
            return Result.failure(DownloadError.NeedsWifi)
        }
        return Result.success(EnqueueOutcome.Queued)
    }

    suspend fun completeActiveDownload(): CompletionOutcome =
        CompletionOutcome.VideoReady(subtitleOutcome = SubtitleSidecarOutcome.NoneRequested)
}

sealed interface EnqueueOutcome {
    data object Queued : EnqueueOutcome
    data object AlreadyAvailableHigherQuality : EnqueueOutcome
}

sealed interface CompletionOutcome {
    data class VideoReady(val subtitleOutcome: SubtitleSidecarOutcome) : CompletionOutcome
    data class VideoFailed(val reason: DownloadFailureReason) : CompletionOutcome
}

sealed interface SubtitleSidecarOutcome {
    data object Downloaded : SubtitleSidecarOutcome
    data object Reused : SubtitleSidecarOutcome
    data object Unavailable : SubtitleSidecarOutcome
    data object NoneRequested : SubtitleSidecarOutcome
}
```

- [ ] **Step 4: Keep `DownloadsPort` aligned with the runtime behavior instead of introducing a second coordinator API**

```kotlin
interface DownloadsPort {
    suspend fun listDownloads(): Result<ImmutableList<DownloadState>>
    suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>>
    suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long? = null,
        transferPreference: TransferPreference = TransferPreference.WifiOnly,
    ): Result<EnqueueOutcome>
    suspend fun enqueueSubtitle(
        localId: LocalMediaIdentifier,
        language: LanguageCode,
    ): Result<SubtitleEnqueueOutcome>
    suspend fun pause(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun resume(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun cancel(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
    suspend fun remove(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
}
```

- [ ] **Step 5: Run the coordinator tests to verify they pass**

Run: `./gradlew :core:media:test --tests "net.subsloth.core.media.download.DownloadCoordinatorTest"`
Expected: PASS

- [ ] **Step 6: Commit the item-download phase**

```bash
git add core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadCoordinator.kt \
  core/media/src/test/kotlin/net/subsloth/core/media/download/DownloadCoordinatorTest.kt \
  core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt
git commit -m "feat(media): add item download coordination"
```

### Task 6: Add Foreground Service And Download Notifications

**Files:**
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadForegroundService.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadNotificationFactory.kt`
- Modify: `core/media/src/main/AndroidManifest.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `core/media/src/test/kotlin/net/subsloth/core/media/download/DownloadNotificationFactoryTest.kt`

- [ ] **Step 1: Write failing notification tests for active-item copy and safe actions**

```kotlin
@Test
fun `download notification shows title progress and safe actions only`() {
    val notification = factory.build(
        title = "Episode 1",
        progressPercent = 50,
        transferState = NotificationTransferState.Active,
    )
    assertThat(notification.actions.map { it.title }).contains("Pause")
    assertThat(notification.actions.map { it.title }).contains("Cancel")
    assertThat(notification.actions.map { it.title }).doesNotContain("Retry all")
}
```

- [ ] **Step 2: Run the notification tests to verify they fail**

Run: `./gradlew :core:media:test --tests "net.subsloth.core.media.download.DownloadNotificationFactoryTest"`
Expected: FAIL because the notification factory does not exist yet.

- [ ] **Step 3: Implement the service and notification factory**

```kotlin
// core/media/src/main/kotlin/net/subsloth/core/media/download/DownloadForegroundService.kt
class DownloadForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            DOWNLOAD_NOTIFICATION_ID,
            DownloadNotificationFactory(this).build(
                title = getString(R.string.download_notification_generic_title),
                progressPercent = 0,
                transferState = NotificationTransferState.Active,
            ),
        )
        return START_NOT_STICKY
    }
}

sealed interface NotificationTransferState {
    data object Active : NotificationTransferState
    data object WaitingForWifi : NotificationTransferState
    data object Failed : NotificationTransferState
}
```

- [ ] **Step 4: Update the manifests to match the spec instead of relying on default platform behavior**

```xml
<!-- app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- core/media/src/main/AndroidManifest.xml -->
<service
    android:name=".download.DownloadForegroundService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

- [ ] **Step 5: Run the notification tests and compile core media**

Run: `./gradlew :core:media:test :core:media:compileDebugKotlin`
Expected: PASS

- [ ] **Step 6: Commit the notification phase**

```bash
git add core/media/src/main/kotlin/net/subsloth/core/media/download \
  core/media/src/main/AndroidManifest.xml \
  app/src/main/AndroidManifest.xml \
  core/media/src/test/kotlin/net/subsloth/core/media/download
git commit -m "feat(media): add offline download foreground service"
```

### Task 7: Implement Confirmed Season Queues And Auth Lifecycle

**Files:**
- Create: `core/media/src/main/kotlin/net/subsloth/core/media/download/SeasonQueueExecutor.kt`
- Modify: `feature/details/src/main/kotlin/net/subsloth/details/DetailViewModels.kt`
- Modify: `feature/auth/src/main/kotlin/net/subsloth/auth/LoginViewModel.kt`
- Modify: `app/src/main/java/net/subsloth/SubSlothApplication.kt`
- Test: `feature/details/src/test/kotlin/net/subsloth/details/SeriesDetailViewModelTest.kt`
- Test: `feature/auth/src/test/kotlin/net/subsloth/auth/LoginViewModelTest.kt`

- [ ] **Step 1: Write failing tests for explicit confirmation, repeated confirmation, and logout pause**

```kotlin
@Test
fun `download season does not run preflight during passive browsing`() {
    val viewModel = ShowDetailViewModel(mediaId = Media.MediaId.Show(ShowId(3)))
    val state = viewModel.uiState.value as DetailUiState.ShowContent
    assertThat(state.seasonDownload).isEqualTo(SeasonDownloadUiState.Idle)
}

@Test
fun `logout pauses incomplete queues without deleting completed assets`() {
    val viewModel = LoginViewModel(
        onLogout = {},
        hasPlayableDownloads = { true },
        pauseIncompleteQueues = { invoked = true },
    )
    viewModel.logout()
    assertThat(invoked).isTrue()
}
```

- [ ] **Step 2: Run the details and auth tests to verify they fail**

Run: `./gradlew :feature:details:test --tests "net.subsloth.details.SeriesDetailViewModelTest" :feature:auth:test --tests "net.subsloth.auth.LoginViewModelTest"`
Expected: FAIL because season confirmation and queue pause hooks do not exist yet.

- [ ] **Step 3: Add explicit season confirmation state to the details view model**

```kotlin
sealed interface SeasonDownloadUiState {
    data object Idle : SeasonDownloadUiState
    @Immutable
    data class AwaitingConfirmation(
        val seasonNumber: Int,
        val summary: SeasonDownloadConfirmation,
    ) : SeasonDownloadUiState
}

@Immutable
data class ShowContent(
    val selectedSeason: Int,
    val seasonDownload: SeasonDownloadUiState = SeasonDownloadUiState.Idle,
) : DetailUiState

class ShowDetailViewModel(
    private val requestSeasonConfirmation: suspend (ShowId, Int) -> Result<SeasonDownloadConfirmation> = { _, _ ->
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
) : ViewModel() {
    fun downloadSeason() {
        val content = _uiState.value as? DetailUiState.ShowContent ?: return
        viewModelScope.launch {
            requestSeasonConfirmation(mediaId.value, content.selectedSeason).onSuccess { summary ->
                _uiState.update { current ->
                    (current as? DetailUiState.ShowContent)?.copy(
                        seasonDownload = SeasonDownloadUiState.AwaitingConfirmation(
                            seasonNumber = content.selectedSeason,
                            summary = summary,
                        ),
                    ) ?: current
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add queue pause/resume lifecycle hooks to auth and app startup**

```kotlin
class LoginViewModel(
    private val pauseIncompleteQueues: () -> Unit = {},
    private val resumeConfirmedQueuesIfPossible: () -> Unit = {},
) : ViewModel() {
    fun logout() {
        pauseIncompleteQueues()
        onLogout()
        _uiState.update { LoginUiState.LoginForm(hasOfflineLibrary = hasPlayableDownloads()) }
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            validateCredentials(login.trim(), password).onSuccess {
                resumeConfirmedQueuesIfPossible()
                _uiState.update { LoginUiState.LoggedIn }
                onLoginSuccess()
            }
        }
    }
}
```

- [ ] **Step 5: Run the details and auth tests to verify they pass**

Run: `./gradlew :feature:details:test --tests "net.subsloth.details.SeriesDetailViewModelTest" :feature:auth:test --tests "net.subsloth.auth.LoginViewModelTest"`
Expected: PASS

- [ ] **Step 6: Commit the season-queue phase**

```bash
git add core/media/src/main/kotlin/net/subsloth/core/media/download/SeasonQueueExecutor.kt \
  feature/details/src/main/kotlin/net/subsloth/details/DetailViewModels.kt \
  feature/details/src/test/kotlin/net/subsloth/details/SeriesDetailViewModelTest.kt \
  feature/auth/src/main/kotlin/net/subsloth/auth/LoginViewModel.kt \
  feature/auth/src/test/kotlin/net/subsloth/auth/LoginViewModelTest.kt \
  app/src/main/java/net/subsloth/SubSlothApplication.kt
git commit -m "feat(downloads): add confirmed season queues"
```

### Task 8: Wire Offline Library, Downloads UI, Local Playback, And Final Verification

**Files:**
- Create: `feature/library/src/main/kotlin/net/subsloth/library/OfflineLibraryViewModel.kt`
- Create: `feature/library/src/main/kotlin/net/subsloth/library/OfflineLibraryScreen.kt`
- Create: `feature/library/src/main/kotlin/net/subsloth/library/DownloadsViewModel.kt`
- Create: `feature/library/src/main/kotlin/net/subsloth/library/DownloadsScreen.kt`
- Create: `feature/library/src/main/res/values/strings.xml`
- Modify: `feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeViewModel.kt`
- Modify: `feature/player/src/main/kotlin/net/subsloth/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/net/subsloth/SubSlothNavHost.kt`
- Modify: `core/ui/src/main/kotlin/net/subsloth/core/ui/UiErrorResources.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`
- Test: `feature/catalog/src/test/kotlin/net/subsloth/catalog/HomeViewModelTest.kt`
- Test: `feature/player/src/test/kotlin/net/subsloth/player/PlayerViewModelTest.kt`
- Test: `feature/library/src/test/kotlin/net/subsloth/library/OfflineLibraryViewModelTest.kt`
- Test: `feature/library/src/test/kotlin/net/subsloth/library/DownloadsViewModelTest.kt`

- [ ] **Step 1: Write failing UX tests for offline-home surfacing, local-file errors, and downloads actions**

```kotlin
@Test
fun `offline home prepends available offline row when offline and playable downloads exist`() {
    val viewModel = HomeViewModel(
        isOnline = { false },
        listDownloads = {
            Result.success(listOf(completedDownload(Media.MediaId.Movie(MovieId(7)))))
        },
    )
    val state = viewModel.uiState.value as HomeUiState.Content
    assertThat(state.rows.first()).isInstanceOf(HomeRow.AvailableOffline::class.java)
}

@Test
fun `offline playback missing local file shows redownload action instead of network refresh`() {
    val viewModel = PlayerViewModel(
        mediaId = Media.MediaId.Movie(MovieId(7)),
        fetchVideoSource = { Result.failure(DownloadError.MissingLocalFile) },
    )
    val state = viewModel.uiState.value as PlayerUiState.Content
    assertThat(state.playbackError).isEqualTo(PlaybackError.LocalFileMissing)
}
```

- [ ] **Step 2: Run the catalog, player, and feature-library tests to verify they fail**

Run: `./gradlew :feature:catalog:test :feature:player:test :feature:library:test`
Expected: FAIL because Offline Library view models/screens and local-file playback handling do not exist yet.

- [ ] **Step 3: Implement Offline Library and Downloads screens in the empty `feature:library` module**

```kotlin
@Stable
sealed interface OfflineLibraryUiState {
    data object Loading : OfflineLibraryUiState
    @Immutable data class Content(val items: ImmutableList<OfflineAsset>) : OfflineLibraryUiState
    @Immutable data class Error(val error: UiError) : OfflineLibraryUiState
}

@Composable
fun OfflineLibraryScreen(
    state: OfflineLibraryUiState,
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit,
    onOpenItem: (LocalMediaIdentifier) -> Unit,
) { /* render available offline items first */ }
```

- [ ] **Step 4: Route user-visible offline errors through shared resource IDs**

```kotlin
// core/ui/src/main/kotlin/net/subsloth/core/ui/UiErrorResources.kt
fun DownloadFailureReason.toDisplayStringRes(): Int = when (this) {
    DownloadFailureReason.NeedsWifi -> R.string.download_error_needs_wifi
    DownloadFailureReason.InsufficientStorage -> R.string.download_error_storage
    DownloadFailureReason.MissingLocalFile -> R.string.download_error_missing_local_file
    DownloadFailureReason.SubtitleUnavailable -> R.string.download_error_subtitle_unavailable
    DownloadFailureReason.AmbiguousQuality -> R.string.download_error_ambiguous_quality
    DownloadFailureReason.DownloadFailed,
    DownloadFailureReason.Unavailable -> R.string.download_error_generic
}
```

- [ ] **Step 5: Update home, player, and nav wiring**

```kotlin
// feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeViewModel.kt
private fun buildOfflineItems(catalog: List<Media>): List<Media> =
    if (!isOnline()) {
        listDownloads().getOrDefault(emptyList())
            .filterIsInstance<DownloadState.Completed>()
            .mapNotNull { download -> catalog.find { it.id == download.mediaId } }
    } else {
        emptyList()
    }

// feature/player/src/main/kotlin/net/subsloth/player/PlayerViewModel.kt
private fun categorizeError(error: Throwable): PlaybackError = when (error) {
    DownloadError.MissingLocalFile -> PlaybackError.LocalFileMissing
    else -> existingCategorization(error)
}

// app/src/main/java/net/subsloth/SubSlothNavHost.kt
entry<DownloadsKey> { DownloadsRoute(...) }
entry<OfflineLibraryKey> { OfflineLibraryRoute(...) }
```

- [ ] **Step 6: Run full feature verification**

Run: `./gradlew :core:domain:test :core:media:test :feature:library:test :app:assembleDebug`
Expected: PASS

- [ ] **Step 7: Run manifest/lint and OpenSpec validation**

Run: `./gradlew lint`
Expected: PASS with `dataSync` foreground-service and notification-permission usage accepted.

Run: `openspec validate offline-downloads --strict`
Expected: PASS

- [ ] **Step 8: Run the repo-required pre-commit checks and commit the final integration phase**

```bash
./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlin :app:assembleDebug test
git add feature/library feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeViewModel.kt \
  feature/player/src/main/kotlin/net/subsloth/player/PlayerViewModel.kt \
  app/src/main/java/net/subsloth/SubSlothNavHost.kt \
  core/ui/src/main/kotlin/net/subsloth/core/ui/UiErrorResources.kt \
  core/ui/src/main/res/values/strings.xml
git commit -m "feat(library): add offline library and downloads ui"
```

---

## Self-Review

### Spec Coverage

- Offline home mode: Task 2 and Task 8
- Local file playback and subtitle-sidecar behavior: Task 1 and Task 8
- App-private storage, opaque paths, backup exclusion, and redaction: Task 3
- Shared offline metadata retention and effective quality: Task 4
- Shared video/sidecar reuse and safe higher-quality replacement: Task 5
- Download state robustness, partial cleanup, and blocked reasons: Task 1, Task 5, Task 8
- Low-storage and metered-network safety: Task 2 and Task 5
- Single active video download: Task 5
- Confirmed season queue flow, fallback policy, persistence, and explicit retry/cancel: Task 2 and Task 7
- Logout pause and login resume checks: Task 7
- Operational notifications and manifest rules: Task 6

### Placeholder Scan

- No `TODO`, `TBD`, or “similar to Task N” placeholders remain.
- Every task names exact files and verification commands.
- Code-changing steps include concrete code skeletons rather than prose-only instructions.

### Type Consistency

- `DownloadFailureReason`, `OfflineAsset`, `SeasonDownloadQueue`, `SeasonDownloadConfirmation`, and the new sealed execution/value types are defined in Task 1 and reused consistently later.
- `DownloadsPort` is expanded once in Task 2 and then consumed by later phases rather than being redefined repeatedly.
- Download lifecycle is modelled as sealed `DownloadState` variants rather than `enum + nullable` companions, and later tasks pattern-match on those variants consistently.
