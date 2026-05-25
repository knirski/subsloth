# Offline Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `offline-downloads` OpenSpec change on top of current `main`, covering offline home mode, app-private downloads, subtitle sidecars, item downloads, confirmed season queues, queue persistence, and operational notifications.

**Architecture:** Keep decision logic in `:core:model` and `:core:domain`, then add the imperative download shell in `:core:media` plus persisted offline state in `:core:database`. Wire the result into `feature:details`, `feature:player`, `feature:auth`, `feature:catalog`, `feature:library`, and `app` without introducing a second requirements system or broad refactors outside the active change.

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
fun `download status exposes offline lifecycle states`() {
    val all = DownloadStatus.entries.toSet()
    assertThat(all).contains(DownloadStatus.PARTIAL)
    assertThat(all).contains(DownloadStatus.UNAVAILABLE)
}

@Test
fun `offline asset keeps subtitle sidecars separate from video asset`() {
    val asset = OfflineAsset(
        mediaId = Media.MediaId.Movie(MovieId(7)),
        localId = LocalMediaIdentifier("movie-7"),
        videoRelativePath = "downloads/video/7/main.mp4",
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
        hasUnknownSizes = true,
        knownSizeBytes = null,
        allowMetered = false,
    )
    assertThat(summary.fallbackSubtitleToEnglishCount).isEqualTo(3)
    assertThat(summary.noSubtitleCount).isEqualTo(1)
}
```

- [ ] **Step 2: Run the model tests to verify they fail**

Run: `./gradlew :core:model:test --tests "net.subsloth.core.model.CoreModelTest"`
Expected: FAIL because the new download model types and enum entries do not exist yet.

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
@Immutable
data class OfflineAsset(
    val mediaId: Media.MediaId,
    val localId: LocalMediaIdentifier,
    val videoRelativePath: String,
    val subtitleLanguages: ImmutableSet<LanguageCode>,
    val effectiveQuality: QualityDescriptor,
    val displayTitle: String,
    val isPlayable: Boolean,
)

// core/model/src/main/kotlin/net/subsloth/core/model/download/SeasonDownloadQueue.kt
@Immutable
data class SeasonDownloadQueue(
    val queueId: String,
    val showId: ShowId,
    val seasonNumber: Int,
    val items: ImmutableList<SeasonDownloadQueueItem>,
    val status: DownloadStatus,
    val allowMetered: Boolean,
)

@Immutable
data class SeasonDownloadQueueItem(
    val mediaId: Media.MediaId.Episode,
    val selectedQuality: Resolution,
    val preferredSubtitleLanguage: LanguageCode,
    val status: DownloadStatus,
    val failureReason: DownloadFailureReason? = null,
)

@Immutable
data class SeasonDownloadConfirmation(
    val episodeCount: Int,
    val alreadyAvailableCount: Int,
    val fallbackQualityCount: Int,
    val fallbackSubtitleToEnglishCount: Int,
    val noSubtitleCount: Int,
    val unavailableCount: Int,
    val hasUnknownSizes: Boolean,
    val knownSizeBytes: Long?,
    val allowMetered: Boolean,
)
```

- [ ] **Step 4: Extend the existing types instead of introducing parallel state**

```kotlin
// core/model/src/main/kotlin/net/subsloth/core/model/download/DownloadState.kt
data class DownloadState(
    val localId: LocalMediaIdentifier,
    val mediaId: Media.MediaId,
    val status: DownloadStatus,
    val quality: QualityDescriptor,
    val downloadedAtEpochSeconds: Instant,
    val sizeBytes: Long?,
    val relativePath: String?,
    val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
    val queueId: String? = null,
    val failureReason: DownloadFailureReason? = null,
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PARTIAL,
    COMPLETED,
    FAILED,
    PAUSED,
    UNAVAILABLE,
    REMOVED,
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
    assertThat(
        SeasonQueuePolicy.selectInitialSubtitle(
            available = subtitles,
            preferred = LanguageCode("pl"),
        )?.language,
    ).isEqualTo(LanguageCode("pl"))
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
    ): Subtitle? = when {
        preferred != LanguageCode("en") -> available.firstOrNull { it.language == preferred }
            ?: available.firstOrNull { it.language == LanguageCode("en") }
        else -> available.firstOrNull { it.language == LanguageCode("en") }
    }

    fun canResumeQueue(
        isOnline: Boolean,
        hasStorage: Boolean,
        allowMetered: Boolean,
        isMetered: Boolean,
        authValid: Boolean,
    ): Boolean = isOnline && hasStorage && authValid && (!isMetered || allowMetered)
}

// core/domain/src/main/kotlin/net/subsloth/core/domain/policy/DownloadPolicy.kt
object DownloadPolicy {
    fun requiredReserveBytes(totalBytes: Long): Long =
        minOf(2L * 1024 * 1024 * 1024, totalBytes / 10)

    fun canTransferOnNetwork(isMetered: Boolean, allowMetered: Boolean): Boolean =
        !isMetered || allowMetered

    fun canReplaceQuality(existing: QualityDescriptor, candidate: QualityDescriptor): Boolean =
        candidate.resolution.pixelCount > existing.resolution.pixelCount
}
```

- [ ] **Step 4: Expand the ports only where the new behavior needs new inputs**

```kotlin
// core/domain/src/main/kotlin/net/subsloth/core/domain/port/DownloadsPort.kt
interface DownloadsPort {
    suspend fun listDownloads(): Result<List<DownloadState>>
    suspend fun listOfflineAssets(): Result<List<OfflineAsset>>
    suspend fun enqueue(mediaId: Media.MediaId): Result<Unit>
    suspend fun enqueueSubtitle(localId: LocalMediaIdentifier, language: LanguageCode): Result<Unit>
    suspend fun pause(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun resume(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun cancel(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun remove(localId: LocalMediaIdentifier): Result<Unit>
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
    fun videoPath(contentId: String, extension: String, randomId: UUID): String =
        "downloads/video/$contentId/$randomId.$extension"

    fun subtitlePath(contentId: String, language: LanguageCode, extension: String, randomId: UUID): String =
        "downloads/subtitles/$contentId/${language.value}/$randomId.$extension"
}

// core/media/src/main/kotlin/net/subsloth/core/media/download/PathRedactor.kt
object PathRedactor {
    fun redact(path: String?): String = if (path.isNullOrBlank()) "[redacted-local-path]" else "[redacted-local-path]"
}
```

- [ ] **Step 4: Implement the asset store and keep backup exclusions aligned with the real download directory**

```kotlin
// core/media/src/main/kotlin/net/subsloth/core/media/download/OfflineAssetStore.kt
class OfflineAssetStore(private val filesDir: File) {
    fun stageVideo(relativePath: String): File = File(filesDir, "$relativePath.part")
    fun finalVideo(relativePath: String): File = File(filesDir, relativePath)
    fun verifyPlayable(file: File): Boolean = file.exists() && file.length() > 0L
    fun deletePartial(relativePath: String) { File(filesDir, "$relativePath.part").delete() }
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
    val status: String,
    val allowMetered: Boolean,
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
    val status: String,
    val failureReason: String?,
)

// core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt
@Dao
interface SeasonDownloadQueueDao {
    @Query("SELECT * FROM season_download_queue")
    fun getAll(): Flow<List<SeasonDownloadQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SeasonDownloadQueueEntity)
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
    val result = coordinator.enqueue(movieId = Media.MediaId.Movie(MovieId(7)), requiredBytes = 400L)
    assertThat(result.exceptionOrNull()).isEqualTo(DownloadError.InsufficientStorage)
}

@Test
fun `existing higher quality asset is reused instead of re downloading lower quality`() = runTest {
    val coordinator = coordinator(existingQuality = qualityDescriptor(Resolution.FULL_HD, "1080p"))
    val result = coordinator.enqueue(movieId = Media.MediaId.Movie(MovieId(7)), requested = Resolution.HD_720)
    assertThat(result.getOrThrow()).isEqualTo(EnqueueResult.AlreadyAvailableHigherQuality)
}

@Test
fun `subtitle sidecar failure does not fail video completion`() = runTest {
    val coordinator = coordinator(sidecarFailure = true)
    val result = coordinator.completeActiveDownload()
    assertThat(result.videoPlayable).isTrue()
    assertThat(result.subtitleFailure).isEqualTo(DownloadFailureReason.SubtitleUnavailable)
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
        requiredBytes: Long?,
        requested: Resolution,
        allowMetered: Boolean = false,
    ): Result<EnqueueResult> {
        val reserve = storagePort.reserveBytes()
        val available = storagePort.availableBytes()
        if (requiredBytes != null && !DownloadPolicy.hasSufficientStorage(available, requiredBytes, reserve)) {
            return Result.failure(DownloadError.InsufficientStorage)
        }
        if (!DownloadPolicy.canTransferOnNetwork(connectivityPort.isMetered(), allowMetered)) {
            return Result.failure(DownloadError.NeedsWifi)
        }
        return Result.success(EnqueueResult.Queued)
    }
}

sealed interface EnqueueResult {
    data object Queued : EnqueueResult
    data object AlreadyAvailableHigherQuality : EnqueueResult
}
```

- [ ] **Step 4: Keep `DownloadsPort` aligned with the runtime behavior instead of introducing a second coordinator API**

```kotlin
interface DownloadsPort {
    suspend fun listDownloads(): Result<List<DownloadState>>
    suspend fun listOfflineAssets(): Result<List<OfflineAsset>>
    suspend fun enqueue(mediaId: Media.MediaId): Result<Unit>
    suspend fun enqueueSubtitle(localId: LocalMediaIdentifier, language: LanguageCode): Result<Unit>
    suspend fun pause(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun resume(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun cancel(localId: LocalMediaIdentifier): Result<Unit>
    suspend fun remove(localId: LocalMediaIdentifier): Result<Unit>
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
        state = DownloadStatus.DOWNLOADING,
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
                state = DownloadStatus.QUEUED,
            ),
        )
        return START_NOT_STICKY
    }
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
    assertThat(viewModel.uiState.value).isInstanceOf(DetailUiState.ShowContent::class.java)
    assertThat(viewModel.pendingSeasonConfirmation()).isNull()
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
@Immutable
data class PendingSeasonConfirmation(
    val seasonNumber: Int,
    val summary: SeasonDownloadConfirmation,
)

class ShowDetailViewModel(
    private val requestSeasonConfirmation: suspend (ShowId, Int) -> Result<SeasonDownloadConfirmation> = { _, _ ->
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
) : ViewModel() {
    private val _pendingSeasonConfirmation = MutableStateFlow<PendingSeasonConfirmation?>(null)

    fun downloadSeason() {
        val content = _uiState.value as? DetailUiState.ShowContent ?: return
        viewModelScope.launch {
            requestSeasonConfirmation(mediaId.value, content.selectedSeason).onSuccess { summary ->
                _pendingSeasonConfirmation.value = PendingSeasonConfirmation(content.selectedSeason, summary)
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
        _uiState.value = LoginUiState.LoginForm(hasOfflineLibrary = hasPlayableDownloads())
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            validateCredentials(login.trim(), password).onSuccess {
                resumeConfirmedQueuesIfPossible()
                _uiState.value = LoginUiState.LoggedIn
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
    viewModel: OfflineLibraryViewModel,
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit,
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
            .filter { it.status == DownloadStatus.COMPLETED }
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
entry<DownloadsKey> { DownloadsScreen(...) }
entry<OfflineLibraryKey> { OfflineLibraryScreen(...) }
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

- `DownloadFailureReason`, `OfflineAsset`, `SeasonDownloadQueue`, and `SeasonDownloadConfirmation` are defined in Task 1 and reused consistently later.
- `DownloadsPort` is expanded once in Task 2 and then consumed by later phases rather than being redefined repeatedly.
- `DownloadStatus.PARTIAL` and `DownloadStatus.UNAVAILABLE` are introduced once and used consistently in shell, queue, and UI tasks.
