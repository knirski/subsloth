# KMP Core Shared Scope C (Shared Data Layer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `:core:database` and `:core:preferences` from Android-only libraries to KMP libraries, migrating Room 2.x → Room 3.0 KMP (alpha) and DataStore Android → DataStore KMP (stable).

**Architecture:** The database and preferences modules are the persistence layer of the imperative shell. Room 3.0 KMP supports the same `@Entity`/`@Dao`/`@Database` annotations across all platforms using platform-specific SQLite drivers and the `@ConstructedBy` pattern. DataStore KMP (stable since 1.1.0) works cross-platform via `datastore-preferences-core` with Okio-based file storage on non-Android targets. The domain ports (`CredentialsPort`, `LibraryPort`, etc.) in `:core:domain` stay unchanged.

**Tech Stack:** Room 3.0 alpha (multiplatform SQL via KSP), DataStore Preferences KMP (multiplatform key-value store), Okio (multiplatform file I/O for DataStore). The existing Room annotations (`@Entity`, `@Dao`, `@Query`) are kept but change from `androidx.room.*` to `androidx.room3.*`. Kept: kotlinx-datetime, kotlinx-collections-immutable, kotlinx-serialization-json.

**Scope boundary:** Only `:core:database` and `:core:preferences` change. All downstream Android modules (`:core:media`, `:feature:*`, `:app`) stay unchanged. `:core:model` and `:core:domain` are already KMP from Scope A. `:core:network` is already KMP from Scope B.

---

## Migration Strategy

| Current | KMP Replacement |
|---|---|
| `androidx.room:room-runtime:2.8.4` | `androidx.room3:room3-runtime:3.0.0-alpha01` |
| `androidx.room:room-ktx:2.8.4` | **Removed** — merged into `room3-runtime` |
| `androidx.room:room-compiler` (KSP) | `androidx.room3:room3-compiler` (KSP) |
| `@Database` with abstract class | `@Database` + `@ConstructedBy(Constructor::class)` + `expect object : RoomDatabaseConstructor` |
| `Room.databaseBuilder(context, ...)` | `Room.databaseBuilder(name = path, factory = Constructor::initialize).setDriver(...)` |
| Android `RoomDatabase.Builder` | `setDriver(BundledSQLiteDriver())` / `setDriver(NativeSQLiteDriver())` / `setDriver(AndroidSqliteDriver)` |
| `androidx.datastore:datastore-preferences` (Android-only) | `datastore-preferences-core` (common) + `datastore-core-okio` (non-Android) + `datastore-preferences` (Android) |
| `Context.userPreferencesDataStore` delegate | `expect fun createDataStorePreferences(name: String): DataStore<Preferences>` |
| `dataStore.data.map { }` Flow reads | **Same API** — DataStore Flow is multiplatform |
| `dataStore.edit { }` transactions | **Same API** — DataStore edit is multiplatform |

### Key design decisions

- **Room 3.0 alpha** keeps the same annotation-driven development model. Entities, DAOs, and queries stay as Kotlin code, not `.sq` files. The migration is mechanical: import path changes (`androidx.room` → `androidx.room3`), adding `@ConstructedBy`, and wiring platform drivers. No SQL schema rewrite.
- **DataStore KMP** keeps the exact same API surface (`Flow`, `edit {}`, `map {}`). The only change is how the `DataStore<Preferences>` instance is created — platform-specific factory instead of the Android `preferencesDataStore` delegate.
- **CredentialStore** stays Android-only via `expect`/`actual` — Android uses Keystore, iOS uses Keychain API, Desktop uses encrypted file. This avoids the "Base64 obfuscation" security hole.

### Risk assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Room 3.0 alpha API changes | Medium | Pin to a specific alpha version; test thoroughly before upgrading alphas |
| Room 3.0 KSP code gen differs from Room 2.x | Medium | The `@Dao` return types for `Flow<List<T>>` may need `@RawQuery` or different annotations |
| DataStore `createWithPath` + Okio not yet in stable | Low | `datastore-core-okio` is beta but functional; can fall back to `java.io.File` on JVM targets |
| CredentialStore KMP requires platform-native crypto | Medium | Use `expect`/`actual` with platform-specific implementations; Android/Desktop share `javax.crypto`, iOS uses `Security.framework` |

---

## File Map

### Modified files

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add Room 3.0 alpha version, adapt DataStore deps, add Okio, add sqlite-kmp drivers |
| `core/database/build.gradle.kts` | Change `subsloth.android.library` → `subsloth.kmp.library`, swap Room 2.x for Room 3.0 + KSP |
| `core/preferences/build.gradle.kts` | Change `subsloth.android.library` → `subsloth.kmp.library`, add DataStore KMP + Okio deps |
| `build.gradle.kts` (root) | Add `alias(libs.plugins.room3) apply false` (if using Room Gradle plugin) |

### Source files — `:core:database`

| File | Action |
|---|---|
| `src/commonMain/kotlin/.../SubSlothDatabase.kt` | **Rewrite** — Room 2.x `@Database` → Room 3.0 `@Database` + `@ConstructedBy` |
| `src/commonMain/kotlin/.../entity/LibraryEntities.kt` | **Rewrite** — change `androidx.room.*` imports to `androidx.room3.*` |
| `src/commonMain/kotlin/.../dao/LibraryDao.kt` | **Rewrite** — change `androidx.room.*` imports to `androidx.room3.*` |
| **New**: `src/commonMain/kotlin/.../SubSlothDatabaseBuilder.kt` | `expect fun createSubSlothDatabase(name: String): SubSlothDatabase` |
| **New**: `src/jvmMain/kotlin/.../SubSlothDatabaseBuilder.jvm.kt` | `actual fun` using `BundledSQLiteDriver()` for all JVM targets (Android + Desktop) |
| **New**: `src/iosMain/kotlin/.../SubSlothDatabaseBuilder.ios.kt` | `actual fun` using `NativeSQLiteDriver()` |

### Source files — `:core:preferences`

| File | Action |
|---|---|
| `src/main/kotlin/.../UserPreferences.kt` | **Rewrite** — `androidx.datastore.preferences` remains same API; change DataStore creation from Android delegate to injected `DataStore<Preferences>` |
| `src/main/kotlin/.../AccountProfileStore.kt` | **Rewrite** — same as UserPreferences (injected `DataStore<Preferences>`) |
| `src/main/kotlin/.../CredentialStore.kt` | **Split into expect/actual** — commonMain gets `expect class CredentialStore`, platform source sets get actual implementations |
| **New**: `src/commonMain/kotlin/.../DataStoreFactory.kt` | `expect fun createDataStorePreferences(name: String, path: String): DataStore<Preferences>` |
| **New**: `src/jvmMain/kotlin/.../DataStoreFactory.android.kt` | `actual fun` using `PreferenceDataStoreFactory.createWithPath()` with Okio |
| **New**: `src/jvmMain/kotlin/.../DataStoreFactory.desktop.kt` | `actual fun` using `PreferenceDataStoreFactory.createWithPath()` with Okio |
| **New**: `src/iosMain/kotlin/.../DataStoreFactory.ios.kt` | `actual fun` using `PreferenceDataStoreFactory.createWithPath()` with Okio |

---

## Task 1: Add Room 3.0 alpha + DataStore KMP dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build-logic/convention/build.gradle.kts` (add Room 3.0 Gradle plugin if using plugin route)

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add/modify these entries:

```toml
[versions]
# Keep existing room reference for downstream modules still on Room 2.x
# (none — all Room usage is in :core:database)
room = "2.8.4"          # ← keep this version entry, but it won't be used by :core:database anymore
room3 = "3.0.0-alpha05"  # ← Room 3.0 KMP alpha
datastore = "1.3.0-alpha09"      # ← DataStore KMP alpha (Okio 3.17.0 synergy)
okio = "3.17.0"          # ← multiplatform file I/O for DataStore
sqliteKmp = "2.7.0-alpha05"      # ← SQLite KMP drivers (provides BundledSQLiteDriver, NativeSQLiteDriver via sqlite-framework)
```

Add to `[libraries]`:

```toml
# Room 3.0 KMP (alpha) — replaces Room 2.x for :core:database
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }

# SQLite KMP drivers (used by Room 3.0 for non-Android platforms)
# SQLite KMP drivers (actual Maven coordinates — the plan's `sqlite-kmp-*` names don't exist)
sqlite-framework = { module = "androidx.sqlite:sqlite-framework", version.ref = "sqliteKmp" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqliteKmp" }

# DataStore KMP
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
# Keep existing datastore-preferences for Android-specific usage
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Okio (multiplatform file I/O for DataStore on non-Android)
datastore-core-okio = { module = "androidx.datastore:datastore-core-okio", version.ref = "datastore" }
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
```

Add to `[plugins]`:

```toml
room3 = { id = "androidx.room3", version.ref = "room3" }
```

- [ ] **Step 2: Register Room 3.0 plugin in root build.gradle.kts**

In `build.gradle.kts`, add:

```kotlin
alias(libs.plugins.room3) apply false
```

- [ ] **Step 3: Add Room 3.0 compiler to build-logic classpath (if needed)**

In `build-logic/convention/build.gradle.kts`, add `compileOnly(libs.room3.compiler)` — but only if the convention plugin needs to reference Room types. Initially this isn't needed (room3 plugin and KSP handle this at the module level).

---

## Task 2: Convert `:core:database` to KMP with Room 3.0 alpha

**Files:**
- Modify: `core/database/build.gradle.kts` (plugin + dependencies)
- Modify: `core/database/src/main/kotlin/.../SubSlothDatabase.kt` (add @ConstructedBy, import changes)
- Modify: `core/database/src/main/kotlin/.../entity/LibraryEntities.kt` (import changes only)
- Modify: `core/database/src/main/kotlin/.../dao/LibraryDao.kt` (import changes only)
- Create: `core/database/src/commonMain/kotlin/.../SubSlothDatabaseBuilder.kt` (expect/actual for database creation)
- Create: `core/database/src/jvmMain/kotlin/.../SubSlothDatabaseBuilder.android.kt`
- Create: `core/database/src/jvmMain/kotlin/.../SubSlothDatabaseBuilder.desktop.kt`
- Create: `core/database/src/iosMain/kotlin/.../SubSlothDatabaseBuilder.ios.kt`
- Delete: `core/database/src/main` directory tree after migration

### How Room 3.0 KMP differs from Room 2.x

| Aspect | Room 2.x (Android-only) | Room 3.0 KMP |
|---|---|---|
| Artifact | `androidx.room:room-runtime` | `androidx.room3:room3-runtime` |
| Compiler | `androidx.room:room-compiler` (KSP) | `androidx.room3:room3-compiler` (KSP) |
| Package | `androidx.room.*` | `androidx.room3.*` |
| KTX | `room-ktx` separate artifact | Merged into `room3-runtime` |
| Database creation | `Room.databaseBuilder(context, ...)` | `Room.databaseBuilder(name = path, factory = ...).setDriver(...)` |
| Constructor | Reflection-based | `@ConstructedBy(Constructor::class)` + `RoomDatabaseConstructor` |
| Driver | Android `SupportSQLiteOpenHelper` | `setDriver(BundledSQLiteDriver())` / `NativeSQLiteDriver()` |
| `Flow<List<T>>` from DAO | Same | Same |
| `suspend` functions in DAO | Same | Same |

The annotation APIs (`@Entity`, `@Dao`, `@Database`, `@Query`, `@Insert`, `@Delete`, `@PrimaryKey`, `@Index`, etc.) have the **same names and same parameters** but in the `androidx.room3` package instead of `androidx.room`.

- [ ] **Step 1: Rewrite build.gradle.kts**

```kotlin
plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.room3.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.sqlite.kmp.android.driver)
            // BundledSQLiteDriver for desktop JVM targets
            implementation(libs.sqlite.kmp.bundled.driver)
        }

        iosMain.dependencies {
            implementation(libs.sqlite.kmp.native.driver)
        }

        // desktopMain — linuxX64, macosArm64, mingwX64
        // BundledSQLiteDriver works on all JVM targets including desktop
        // The desktop source set uses the same driver as jvmMain
    }
}

// KSP must be configured for code generation
dependencies {
    ksp(libs.room3.compiler)
}
```

- [ ] **Step 2: Rewrite SubSlothDatabase.kt**

The `@Database` annotation stays but needs:
1. Package change: `androidx.room` → `androidx.room3`
2. `@ConstructedBy(SubSlothDatabaseCtor::class)` annotation
3. Remove `abstract` from class — Room 3.0 generates non-abstract implementation

```kotlin
package subsloth.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import subsloth.database.dao.AccountPlaybackProgressDao
import subsloth.database.dao.CachedOnlineMetadataDao
import subsloth.database.dao.DownloadedMediaDao
import subsloth.database.dao.DownloadedSubtitleDao
import subsloth.database.dao.FavoriteDao
import subsloth.database.dao.LocalLibraryRecordDao
import subsloth.database.dao.OfflineDisplayMetadataDao
import subsloth.database.dao.OfflinePlaybackProgressDao
import subsloth.database.dao.SubscriptionDao
import subsloth.database.dao.WatchLaterDao
import subsloth.database.dao.WatchedStateDao
import subsloth.database.entity.AccountPlaybackProgressEntity
import subsloth.database.entity.CachedOnlineMetadataEntity
import subsloth.database.entity.DownloadedMediaEntity
import subsloth.database.entity.DownloadedSubtitleEntity
import subsloth.database.entity.FavoriteEntity
import subsloth.database.entity.LocalLibraryRecordEntity
import subsloth.database.entity.OfflineDisplayMetadataEntity
import subsloth.database.entity.OfflinePlaybackProgressEntity
import subsloth.database.entity.SubscriptionEntity
import subsloth.database.entity.WatchLaterEntity
import subsloth.database.entity.WatchedStateEntity

@Database(
    entities = [
        CachedOnlineMetadataEntity::class,
        AccountPlaybackProgressEntity::class,
        FavoriteEntity::class,
        WatchLaterEntity::class,
        WatchedStateEntity::class,
        SubscriptionEntity::class,
        LocalLibraryRecordEntity::class,
        DownloadedMediaEntity::class,
        DownloadedSubtitleEntity::class,
        OfflineDisplayMetadataEntity::class,
        OfflinePlaybackProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(SubSlothDatabaseCtor::class)
abstract class SubSlothDatabase : RoomDatabase() {
    abstract fun cachedOnlineMetadataDao(): CachedOnlineMetadataDao
    abstract fun accountPlaybackProgressDao(): AccountPlaybackProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun watchedStateDao(): WatchedStateDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun localLibraryRecordDao(): LocalLibraryRecordDao
    abstract fun downloadedMediaDao(): DownloadedMediaDao
    abstract fun downloadedSubtitleDao(): DownloadedSubtitleDao
    abstract fun offlineDisplayMetadataDao(): OfflineDisplayMetadataDao
    abstract fun offlinePlaybackProgressDao(): OfflinePlaybackProgressDao
}
```

- [ ] **Step 3: Update LibraryEntities.kt imports**

The entity file needs only import changes:

```kotlin
package subsloth.database.entity

// Change: import androidx.room.Entity → import androidx.room3.Entity
// Change: import androidx.room.Index → import androidx.room3.Index
// Change: import androidx.room.PrimaryKey → import androidx.room3.PrimaryKey
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// All data classes stay exactly the same — no schema changes needed
// ... (keep all 11 entity data classes as-is)
```

- [ ] **Step 4: Update LibraryDao.kt imports**

```kotlin
package subsloth.database.dao

// Change: import androidx.room.Dao → import androidx.room3.Dao
// Change: import androidx.room.Delete → import androidx.room3.Delete
// Change: import androidx.room.Insert → import androidx.room3.Insert
// Change: import androidx.room.OnConflictStrategy → import androidx.room3.OnConflictStrategy
// Change: import androidx.room.Query → import androidx.room3.Query
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

// All DAO interfaces stay exactly the same — no query changes needed
// ... (keep all 11 DAO interfaces as-is)
```

- [ ] **Step 5: Create expect/actual for database construction**

In commonMain, create the `expect object` (implements `RoomDatabaseConstructor`) and `expect fun`:

```kotlin
// File: core/database/src/commonMain/kotlin/net/subsloth/database/SubSlothDatabaseBuilder.kt
package subsloth.database

import androidx.room3.Room
import androidx.room3.RoomDatabaseConstructor

/**
 * Room 3.0 KMP constructor — implemented per platform
 * (Android: AndroidSqliteDriver, Desktop: BundledSQLiteDriver, iOS: NativeSQLiteDriver).
 */
expect object SubSlothDatabaseCtor : RoomDatabaseConstructor<SubSlothDatabase>

/**
 * Platform-specific database factory.
 *
 * Android needs [android.content.Context] for [AndroidSqliteDriver] — the
 * Android actual exposes an overload that accepts Context; callers on Android
 * should use a DI/application holder to supply it before calling this.
 */
expect fun createSubSlothDatabase(name: String): SubSlothDatabase
```

The Android actual uses the same jvmMain builder (no separate androidTarget — BundledSQLiteDriver works on both Android and desktop JVM):

```kotlin
// File: core/database/src/jvmMain/kotlin/net/subsloth/database/SubSlothDatabaseBuilder.jvm.kt
package subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createSubSlothDatabase(name: String): SubSlothDatabase =
    Room.databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
```

(Note: Room's KSP generates the `actual object SubSlothDatabaseCtor` automatically — no manual actual needed for the constructor.)

```kotlin
// File: core/database/src/jvmMain/kotlin/net/subsloth/database/SubSlothDatabaseBuilder.desktop.kt
package subsloth.database

import androidx.room3.Room
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.BundledSQLiteDriver

actual object SubSlothDatabaseCtor : RoomDatabaseConstructor<SubSlothDatabase> {
    override fun initialize(): SubSlothDatabase = SubSlothDatabase()
}

actual fun createSubSlothDatabase(name: String): SubSlothDatabase {
    return Room.databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}
```

```kotlin
// File: core/database/src/iosMain/kotlin/net/subsloth/database/SubSlothDatabaseBuilder.ios.kt
package subsloth.database

import androidx.room3.Room
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.NativeSQLiteDriver

actual object SubSlothDatabaseCtor : RoomDatabaseConstructor<SubSlothDatabase> {
    override fun initialize(): SubSlothDatabase = SubSlothDatabase()
}

actual fun createSubSlothDatabase(name: String): SubSlothDatabase {
    return Room.databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    )
        .setDriver(NativeSQLiteDriver())
        .build()
}
```

- [ ] **Step 6: Move source files to KMP layout**

```bash
# Create the commonMain source directory
mkdir -p core/database/src/commonMain/kotlin/net/subsloth/database
mkdir -p core/database/src/commonMain/kotlin/net/subsloth/database/entity
mkdir -p core/database/src/commonMain/kotlin/net/subsloth/database/dao

# Move rewritten Room files to commonMain
cp core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt \
   core/database/src/commonMain/kotlin/net/subsloth/database/
cp core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt \
   core/database/src/commonMain/kotlin/net/subsloth/database/entity/
cp core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt \
   core/database/src/commonMain/kotlin/net/subsloth/database/dao/

# Create platform source directories
mkdir -p core/database/src/jvmMain/kotlin/net/subsloth/database
mkdir -p core/database/src/iosMain/kotlin/net/subsloth/database

# Delete old Android-only source tree
rm -rf core/database/src/main
```

- [ ] **Step 7: Copy existing Room schema for migration continuity**

Room 3.0 generates schemas to the directory configured in `room3 { schemaDirectory() }`. Copy the existing Room 2.x schema so migrations are preserved:

```bash
# Room 3.0 schema directory mirrors the old room schema location
mkdir -p core/database/schemas
cp -r core/database/src/main/schemas/* core/database/schemas/ 2>/dev/null || true
```

---

## Task 3: Convert `:core:preferences` to KMP with DataStore KMP

**Files:**
- Modify: `core/preferences/build.gradle.kts`
- Modify: `core/preferences/src/main/kotlin/.../UserPreferences.kt`
- Modify: `core/preferences/src/main/kotlin/.../AccountProfileStore.kt`
- Split: `core/preferences/src/main/kotlin/.../CredentialStore.kt` → expect/actual
- Create: `core/preferences/src/commonMain/kotlin/.../DataStoreFactory.kt` (expect)
- Create: `core/preferences/src/jvmMain/kotlin/.../DataStoreFactory.android.kt` (Android)
- Create: `core/preferences/src/jvmMain/kotlin/.../DataStoreFactory.desktop.kt` (Desktop)
- Create: `core/preferences/src/iosMain/kotlin/.../DataStoreFactory.ios.kt` (iOS)

### How DataStore KMP differs from DataStore Android

| Aspect | DataStore Android | DataStore KMP |
|---|---|---|
| Artifact | `datastore-preferences` | `datastore-preferences-core` (common) + `datastore-preferences` (Android) + `datastore-core-okio` (non-Android file storage) |
| Creation | `Context.preferencesDataStore` delegate | `PreferenceDataStoreFactory.createWithPath(produceFile = { okio.Path(...) })` |
| API surface (`data`, `edit {}`, `map {}`, etc.) | Same | Same, from `datastore-preferences-core` |
| Flow-based reads | `dataStore.data.map { }` | Same — no change needed in callers |
| `edit {}` return value | Returns modified `Preferences` | Same — no change needed |

The key insight: **UserPreferences and AccountProfileStore stay almost identical** — only the DataStore construction changes from Android delegate to injected `DataStore<Preferences>`. The rest of the code (Flow reads, `edit {}` transactions, preference key definitions) works unchanged because `datastore-preferences-core` exports the same types.

- [ ] **Step 1: Rewrite build.gradle.kts**

```kotlin
plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            // DataStore KMP — core types are here
            implementation(libs.datastore.preferences.core)
            // Okio for multiplatform file I/O
            implementation(libs.okio)
        }

        jvmMain.dependencies {
            // Android-specific DataStore (includes Context delegate)
            implementation(libs.datastore.preferences)
            // Okio-backed storage for non-Android JVM targets
            implementation(libs.datastore.core.okio)
        }

        iosMain.dependencies {
            // Okio-backed storage for iOS
            implementation(libs.datastore.core.okio)
        }

        // Testing
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
```

- [ ] **Step 2: Create expect/actual DataStore factory**

```kotlin
// File: core/preferences/src/commonMain/kotlin/net/subsloth/preferences/DataStoreFactory.kt
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/**
 * Creates a platform-appropriate DataStore<Preferences> for the given name.
 *
 * Each platform provides its own storage backend:
 * - Android: uses the datastore-preferences Context delegate
 * - Desktop (JVM): uses Okio-backed storage via FileSystem.SYSTEM
 * - iOS: uses Okio-backed storage via FileSystem.SYSTEM
 */
expect fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
): DataStore<Preferences>
```

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/DataStoreFactory.android.kt
package subsloth.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope

/**
 * Android-specific DataStore creation using the standard preferencesDataStore delegate.
 * This provides automatic Context-scoped lifecycle and multi-process safety.
 */
private val Context.androidDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

fun createAndroidDataStorePreferences(context: Context): DataStore<Preferences> =
    context.androidDataStore
```

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/DataStoreFactory.desktop.kt
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Path

/**
 * Desktop/JVM DataStore using Okio FileSystem (works on Linux, macOS, Windows).
 */
actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val dataDir: Path = resolveAppDataDir().toPath()
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { dataDir.resolve("$name.preferences_pb") },
    )
}

/** Resolves an app-specific data directory for the current OS. */
private fun resolveAppDataDir(): String {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")
    return when {
        osName.contains("linux") || osName.contains("mac") ->
            "$userHome/.local/share/subsloth"
        osName.contains("windows") ->
            "${System.getenv("APPDATA")}\\subsloth"
        else ->
            "$userHome/.subsloth"
    }
}
```

```kotlin
// File: core/preferences/src/iosMain/kotlin/net/subsloth/preferences/DataStoreFactory.ios.kt
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Path
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS DataStore using Okio FileSystem backed by the app's Documents directory.
 */
actual fun createDataStorePreferences(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    scope: CoroutineScope,
): DataStore<Preferences> {
    val documentsDir: String = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String
    val appDir = "$documentsDir/subsloth"
    // Ensure directory exists
    val path = appDir.toPath()
    FileSystem.SYSTEM.createDirectories(path)
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = corruptionHandler,
        scope = scope,
        produceFile = { path.resolve("$name.preferences_pb") },
    )
}
```

- [ ] **Step 3: Rewrite UserPreferences**

The class stays almost identical — only the constructor and companion factory change:

```kotlin
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import subsloth.core.model.identifier.AccountProfileKey

/**
 * Account-scoped user preferences backed by DataStore KMP.
 *
 * Same API as before — DataStore<Preferences> works identically
 * across all platforms. The DataStore instance is injected so the
 * platform-specific creation logic stays outside this class.
 */
@Suppress("TooManyFunctions")
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    // ── Preference keys (unchanged) ──────────────────────────────────────
    private fun subtitleEnabledKey(profileKey: AccountProfileKey) =
        booleanPreferencesKey("${profileKey.value}_subtitle_enabled")
    private fun subtitleLanguageKey(profileKey: AccountProfileKey) =
        stringPreferencesKey("${profileKey.value}_subtitle_language")
    private fun qualityKey(profileKey: AccountProfileKey) =
        stringPreferencesKey("${profileKey.value}_quality")
    private fun playbackSpeedKey(profileKey: AccountProfileKey) =
        floatPreferencesKey("${profileKey.value}_playback_speed")
    private fun downloadsWifiOnlyKey(profileKey: AccountProfileKey) =
        booleanPreferencesKey("${profileKey.value}_downloads_wifi_only")
    private fun catalogCacheTimestampKey(profileKey: AccountProfileKey) =
        longPreferencesKey("${profileKey.value}_catalog_cache_timestamp")
    private fun detailCacheTimestampKey(profileKey: AccountProfileKey) =
        longPreferencesKey("${profileKey.value}_detail_cache_timestamp")

    // ── All methods stay unchanged from Room 2.x (same API) ──────────────
    //
    // The following methods keep their exact Room 2.x signatures and bodies.
    // Only the import path changes: androidx.datastore → androidx.datastore
    // (same package — DataStore KMP uses identical types).
    //
    //- subtitleLanguage(profileKey): Flow<String?>
    //- setSubtitleLanguage(profileKey, language: String?)
    //- quality(profileKey): Flow<String?>
    //- setQuality(profileKey, quality: String?)
    //- playbackSpeed(profileKey): Flow<Float>
    //- setPlaybackSpeed(profileKey, speed: Float)
    //- downloadsWifiOnly(profileKey): Flow<Boolean>
    //- setDownloadsWifiOnly(profileKey, wifiOnly: Boolean)
    //- catalogCacheTimestamp(profileKey): Flow<Long?>
    //- setCatalogCacheTimestamp(profileKey, timestamp: Long)
    //- detailCacheTimestamp(profileKey): Flow<Long?>
    //- setDetailCacheTimestamp(profileKey, timestamp: Long)
    //
    // See the original file at core/preferences/src/main/kotlin/.../UserPreferences.kt
    // for the exact implementation of each — they copy verbatim with no change.
    // The test file at UserPreferencesTest.kt verifies all 7 pairs.

    suspend fun clearProfilePreferences(profileKey: AccountProfileKey) {
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { key ->
                key.name.startsWith("${profileKey.value}_")
            }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }

}
```

> **Note:** The companion factory method `from(context: Context)` is intentionally **omitted** from commonMain. It uses `android.content.Context` which is Android-only. Instead, it lives in a separate Android-specific file so commonMain stays clean:

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/UserPreferences.android.kt
package subsloth.preferences

import android.content.Context

/** Android factory — keeps android.content.Context out of commonMain. */
fun UserPreferences.Companion.from(context: Context): UserPreferences =
    UserPreferences(createAndroidDataStorePreferences(context))
```

- [ ] **Step 4: Rewrite AccountProfileStore**

Same pattern — injected `DataStore<Preferences>`, companion factory in Android-specific file:

```kotlin
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import subsloth.core.model.identifier.AccountProfileKey
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stores an app-local salt and derives account profile keys using HMAC-SHA256.
 *
 * DataStore KMP preserves the `edit {}` return-value semantics that
 * getOrCreateSalt() depends on, so this code stays unchanged.
 */
class AccountProfileStore(private val dataStore: DataStore<Preferences>) {
    private val saltKey = stringPreferencesKey("profile_salt")

    /**
     * Returns the current profile salt, generating and persisting a new one
     * if none exists yet.
     *
     * Uses DataStore's `edit {}` which returns the modified [Preferences],
     * allowing atomic read-modify-write of the salt.
     */
    suspend fun getOrCreateSalt(): String {
        val result = dataStore.edit { prefs ->
            if (prefs[saltKey] == null) {
                prefs[saltKey] = generateSalt()
            }
        }
        return requireNotNull(result[saltKey]) {
            "Profile salt must exist after getOrCreateSalt"
        }
    }

    suspend fun deriveProfileKey(login: String): AccountProfileKey {
        val salt = getOrCreateSalt()
        val normalized = normalizeLogin(login)
        val hmac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(keySpec)
        val hash = hmac.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02x".format(it) }
        return AccountProfileKey(hex)
    }

    suspend fun hasSalt(): Boolean = dataStore.data
        .map { prefs -> prefs[saltKey] != null }
        .first()

    fun saltFlow(): Flow<String?> = dataStore.data.map { prefs -> prefs[saltKey] }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeLogin(login: String): String {
        val trimmed = login.trim()
        val nfc = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        return nfc.lowercase(Locale.ROOT)
    }
}
```

Same pattern for the Android factory:

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/AccountProfileStore.android.kt
package subsloth.preferences

import android.content.Context

/** Android factory — keeps android.content.Context out of commonMain. */
fun AccountProfileStore.Companion.from(context: Context): AccountProfileStore =
    AccountProfileStore(createAndroidDataStorePreferences(context))
```
```

- [ ] **Step 5: Split CredentialStore into expect/actual**

The existing `CredentialStore` uses Android Keystore directly. For KMP, it becomes a platform-specific expect/actual:

```kotlin
// File: core/preferences/src/commonMain/kotlin/net/subsloth/preferences/CredentialStore.kt
package subsloth.preferences

/**
 * Platform-specific encrypted credential store.
 *
 * Implementations:
 * - Android: AES/GCM with Android Keystore (as before)
 * - iOS: Keychain Services via platform.Security
 * - Desktop: AES/GCM with JCA (javax.crypto), file-based storage
 */
expect class CredentialStore {
    fun save(login: String, password: String)
    fun read(): Pair<String, String>?
    fun clear()
    fun exists(): Boolean
}
```

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialStore.android.kt
package subsloth.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android actual: AES/GCM with Android Keystore.
 * Identical to the original CredentialStore.kt — no changes needed.
 */
actual class CredentialStore(private val context: Context) {
    private val keystoreAlias = "subsloth_credentials_key"
    private val keystoreType = "AndroidKeyStore"
    private val prefsName = "subsloth_encrypted_credentials"

    actual fun save(login: String, password: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val plaintext = "$login\u0000$password"
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            putString("credentials", encoded)
        }
    }

    actual fun read(): Pair<String, String>? {
        val encoded = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString("credentials", null) ?: return null
        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            if (decoded.size < 13) return null
            val iv = decoded.copyOfRange(0, 12)
            val ciphertext = decoded.copyOfRange(12, decoded.size)
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            val parts = String(plaintext, Charsets.UTF_8).split("\u0000", limit = 2)
            if (parts.size != 2) null else Pair(parts[0], parts[1])
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    actual fun clear() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit {
            remove("credentials")
        }
    }

    actual fun exists(): Boolean =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .contains("credentials")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keystoreType)
        keyStore.load(null)
        keyStore.getEntry(keystoreAlias, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, keystoreType
        )
        val spec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
```

```kotlin
// File: core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/CredentialStore.desktop.kt
package subsloth.preferences

import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Desktop actual: AES/GCM with JCA, file-backed keystore.
 * Uses java.security.KeyStore (PKCS12) instead of Android Keystore.
 */
actual class CredentialStore {
    private val storeFile = File(System.getProperty("user.home"), ".subsloth/credentials.ks")
    private val storePass = "subsloth"  // not a secret — obfuscation only
    private val keyAlias = "credentials_key"

    init {
        storeFile.parentFile?.mkdirs()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(storeFile.inputStream().takeIf { storeFile.exists() } ?: null, storePass.toCharArray())
        if (ks.containsAlias(keyAlias)) {
            return (ks.getEntry(keyAlias, KeyStore.PasswordProtection(storePass.toCharArray()))
                as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        ks.setEntry(keyAlias, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(storePass.toCharArray()))
        ks.store(storeFile.outputStream(), storePass.toCharArray())
        return key
    }

    actual fun save(login: String, password: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal("$login\u0000$password".toByteArray())
        storeFile.writeBytes(cipher.iv + ct)
    }

    actual fun read(): Pair<String, String>? {
        if (!storeFile.exists()) return null
        return try {
            val data = storeFile.readBytes()
            val iv = data.copyOfRange(0, 12)
            val ct = data.copyOfRange(12, data.size)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val parts = String(cipher.doFinal(ct), Charsets.UTF_8).split("\u0000", limit = 2)
            if (parts.size != 2) null else Pair(parts[0], parts[1])
        } catch (_: Exception) {
            null
        }
    }

    actual fun clear() { storeFile.delete() }

    actual fun exists(): Boolean = storeFile.exists()
}
```

```kotlin
// File: core/preferences/src/iosMain/kotlin/net/subsloth/preferences/CredentialStore.ios.kt
package subsloth.preferences

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS actual: Keychain Services via Security framework.
 * Uses kSecClassGenericPassword with a fixed service name.
 */
actual class CredentialStore {
    private val serviceName = "subsloth.credentials"

    @OptIn(ExperimentalForeignApi::class)
    actual fun save(login: String, password: String) {
        // Remove existing first
        clear()
        val data = "$login\u0000$password".encodeToByteArray()
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to "credentials",
            kSecValueData to CFBridgingRelease(
                NSData.create(bytes = data.toCFData(), length = data.size.toULong())
            ),
        )
        SecItemAdd(query as CFDictionaryRef, null)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Suppress("UNCHECKED_CAST")
    actual fun read(): Pair<String, String>? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to "credentials",
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != 0) return null
            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            val str = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return null
            val parts = str.toString().split("\u0000", limit = 2)
            return if (parts.size != 2) null else Pair(parts[0], parts[1])
        }
    }

    actual fun clear() {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to "credentials",
        )
        SecItemDelete(query as CFDictionaryRef)
    }

    actual fun exists(): Boolean = read() != null
}

/** Converts a ByteArray to a raw C pointer for NSData. */
private fun ByteArray.toCFData() = this.toCValues().ptr
```

- [ ] **Step 6: Move source to KMP layout**

```bash
# Create the commonMain source directory
mkdir -p core/preferences/src/commonMain/kotlin/net/subsloth/preferences
mkdir -p core/preferences/src/jvmMain/kotlin/net/subsloth/preferences
mkdir -p core/preferences/src/iosMain/kotlin/net/subsloth/preferences

# Move rewritten files to commonMain
cp core/preferences/src/main/kotlin/net/subsloth/preferences/UserPreferences.kt \
   core/preferences/src/commonMain/kotlin/net/subsloth/preferences/
cp core/preferences/src/main/kotlin/net/subsloth/preferences/AccountProfileStore.kt \
   core/preferences/src/commonMain/kotlin/net/subsloth/preferences/

# New expect/actual files
# CredentialStore.kt → commonMain (expect)
# CredentialStore.android.kt → jvmMain (actual, Android)
# CredentialStore.desktop.kt → jvmMain (actual, Desktop)
# CredentialStore.ios.kt → iosMain (actual, iOS)
# DataStoreFactory.kt → commonMain (expect)
# DataStoreFactory.android.kt → jvmMain (actual, Android)
# DataStoreFactory.desktop.kt → jvmMain (actual, Desktop)
# DataStoreFactory.ios.kt → iosMain (actual, iOS)

# Copy test files (they need updating for KMP too)
cp -r core/preferences/src/test/* core/preferences/src/jvmTest/
cp -r core/preferences/src/androidTest/* core/preferences/src/jvmTest/ 2>/dev/null || true

# Delete old Android-only source tree
rm -rf core/preferences/src/main
rm -rf core/preferences/src/androidTest
rm -rf core/preferences/src/test
```

---

## Task 4: Update tests

**Files:**
- Modify: `core/preferences/src/jvmTest/kotlin/.../UserPreferencesTest.kt` (use in-memory DataStore)
- Modify: `core/preferences/src/jvmTest/kotlin/.../AccountProfileStoreTest.kt` (same)
- Create: `core/preferences/src/commonTest/kotlin/.../CredentialStoreTest.kt` (common tests)
- Create: `core/preferences/src/jvmTest/kotlin/.../CredentialStoreAndroidTest.kt`
- Modify: `core/preferences/src/jvmTest/kotlin/.../DataStoreTestHelper.kt` (switch to KMP-compatible helper)

- [ ] **Step 1: Rewrite DataStoreTestHelper**

The existing test helper uses `java.io.File` (JVM-only). Switch to a KMP-compatible approach using `PreferenceDataStoreFactory.createWithPath()` with Okio's in-memory `FileSystem`:

```kotlin
// File: core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/DataStoreTestHelper.kt
package subsloth.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Creates an ephemeral [DataStore] for JVM unit testing backed by a unique temp file.
 * Each call produces an isolated DataStore so tests do not share state.
 *
 * Uses [java.io.File.createTempFile] which is JVM-only — this file lives in
 * `jvmTest/` and is not compiled for iOS/Desktop targets.
 */
fun createTestDataStore(): DataStore<Preferences> {
    val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val tempFile = File.createTempFile("test_datastore_", ".preferences_pb")
    tempFile.deleteOnExit()
    return PreferenceDataStoreFactory.create(
        scope = testScope,
        produceFile = { tempFile },
    )
}
```

- [ ] **Step 2: Update existing test classes**

`UserPreferencesTest.kt` and `AccountProfileStoreTest.kt` only need to change the `@BeforeEach` setup — everything else stays the same:

```kotlin
// In UserPreferencesTest.kt:
@BeforeEach
fun setUp() {
    val dataStore = createTestDataStore()  // was: createTempFileDataStore()
    prefs = UserPreferences(dataStore)
}

// In AccountProfileStoreTest.kt:
@BeforeEach
fun setUp() {
    val dataStore = createTestDataStore()  // was: createTempFileDataStore()
    store = AccountProfileStore(dataStore)
}
```

- [ ] **Step 3: Create common credential tests**

```kotlin
// File: core/preferences/src/commonTest/kotlin/net/subsloth/preferences/CredentialStoreContractTest.kt
package subsloth.preferences

import kotlin.test.Test
import kotlin.test.*

/**
 * Contract tests for CredentialStore that should pass on all platforms.
 * Platform-specific setup varies, but the contract (save/read/clear/exists)
 * is the same everywhere.
 *
 * Concrete subclasses per platform:
 * - JVM/Android: `CredentialStoreAndroidTest` in `src/jvmTest/`
 * - iOS: `CredentialStoreIosTest` in `src/iosTest/`
 */
abstract class CredentialStoreContractTest {
    abstract fun createStore(): CredentialStore

    @Test
    fun saveAndReadCredentials() {
        val store = createStore()
        store.clear()
        store.save("user@example.com", "securePassword123")
        val result = store.read()
        assertNotNull(result)
        assertEquals("user@example.com", result.first)
        assertEquals("securePassword123", result.second)
    }

    @Test
    fun readReturnsNullWhenNoCredentialsStored() {
        val store = createStore()
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun existsReturnsFalseWhenNoCredentials() {
        val store = createStore()
        store.clear()
        assertFalse(store.exists())
    }

    @Test
    fun existsReturnsTrueWhenCredentialsAreStored() {
        val store = createStore()
        store.clear()
        store.save("user@example.com", "password")
        assertTrue(store.exists())
    }

    @Test
    fun clearRemovesCredentials() {
        val store = createStore()
        store.clear()
        store.save("user@example.com", "password")
        assertTrue(store.exists())
        store.clear()
        assertFalse(store.exists())
        assertNull(store.read())
    }

    @Test
    fun saveOverwritesExistingCredentials() {
        val store = createStore()
        store.clear()
        store.save("user1@example.com", "pass1")
        store.save("user2@example.com", "pass2")
        val result = store.read()
        assertNotNull(result)
        assertEquals("user2@example.com", result!!.first)
        assertEquals("pass2", result.second)
    }

    @Test
    fun credentialsHandleSpecialCharacters() {
        val store = createStore()
        store.clear()
        val login = "test+special@example.com"
        val password = "p@ssw0rd!\"\"#$%&'()*+,-./:;<=>?@[]^_`{|}~"
        store.save(login, password)
        val result = store.read()
        assertNotNull(result)
        assertEquals(login, result!!.first)
        assertEquals(password, result.second)
    }

    @Test
    fun clearIsIdempotent() {
        val store = createStore()
        store.clear()  // no credentials yet
        store.clear()  // already clear
        store.clear()  // still fine
        assertFalse(store.exists())
    }

    @Test
    fun credentialsSurviveStoreRecreation() {
        val store = createStore()
        store.clear()
        store.save("persistent@test.com", "keepMe")
        // Simulate app restart
        val newStore = createStore()
        val result = newStore.read()
        assertNotNull(result)
        assertEquals("persistent@test.com", result!!.first)
        assertEquals("keepMe", result.second)
    }
}
```

Concrete test subclass for JVM platforms:

```kotlin
// File: core/preferences/src/jvmTest/kotlin/net/subsloth/preferences/CredentialStoreJvmTest.kt
package subsloth.preferences

/**
 * JVM/Android test — uses the platform CredentialStore constructor.
 * On Android this needs Context (via ApplicationProvider).
 */
class CredentialStoreJvmTest : CredentialStoreContractTest() {
    override fun createStore(): CredentialStore {
        // For Android: CredentialStore(ApplicationProvider.getApplicationContext())
        // For Desktop: CredentialStore()
        return CredentialStore()
    }
}
```
```

---

## Task 5: Verify downstream compatibility

**Files:**
- All modules that depend on `:core:database` or `:core:preferences`

Current dependents:
- `:app` — depends on both
- `:feature:auth` — depends on both (LoginScreen.kt, LoginViewModel.kt)
- `:feature:library` — depends on both (NO source files yet)

- [ ] **Step 1: Build and fix compilation errors**

```bash
./gradlew :app:assembleDebug
```

Expected issues to fix:
1. `DatabaseDriverFactory` / `SubSlothDatabase` instantiation — downstream modules that create the database need updating. If nothing outside `:core:database` creates it directly (currently no `grep` hits), this may be a no-op.
2. `UserPreferences` / `AccountProfileStore` factory methods — `.from(context)` still works on Android via the companion. No change needed for existing callers.
3. `CredentialStore` constructor — now expect/actual. Android callers pass `Context` as before.

- [ ] **Step 2: Verify module dependencies**

Check `:feature:library` and `:feature:settings` still compile (they have no source files but their build scripts reference the modules).

---

## Task 6: Full verification

- [ ] **Step 1: Run pre-commit checks**

```bash
./gradlew spotlessApply spotlessCheck detekt :core:database:compileKotlinJvm :core:preferences:compileKotlinJvm :core:database:compileKotlinIosArm64 :core:preferences:compileKotlinIosArm64 :app:assembleDebug test
```

- [ ] **Step 2: Commit and push**

```bash
git add -A
git commit -m "feat(core): migrate :core:database and :core:preferences to KMP with Room 3.0 alpha + DataStore KMP"
git push origin HEAD
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|---|---|
| Room 3.0 alpha KMP dependencies | Task 1 |
| DataStore KMP dependencies | Task 1 |
| `@Database` → `@ConstructedBy` pattern | Task 2 |
| Import migration `androidx.room` → `androidx.room3` | Task 2 (Step 2-4) |
| Platform SQLite drivers (Android/Bundled/Native) | Task 2 (Step 5) |
| `UserPreferences` → DataStore KMP | Task 3 (Step 3) |
| `AccountProfileStore` → DataStore KMP (preserves `edit {}` return value) | Task 3 (Step 4) |
| `CredentialStore` → expect/actual | Task 3 (Step 5) |
| DataStore factory expect/actual | Task 3 (Step 2) |
| Test migration (in-memory DataStore via Okio FakeFileSystem) | Task 4 |
| Downstream compatibility | Task 5 |
| Verification | Task 6 |

### Correctness: Issues fixed from original plan

| Original plan issue | Fix in this plan |
|---|---|
| Wrong file map (invented separate `entity/*.kt` and `dao/*.kt` files) | Accurate 3-file structure (`LibraryEntities.kt`, `LibraryDao.kt`, `SubSlothDatabase.kt`) |
| `.sq` file missing (11 tables undefined) | Not needed — Room KMP keeps entities/DAOs as Kotlin code |
| `AccountProfileStore.getOrCreateSalt()` race with Settings | Not applicable — DataStore KMP preserves `edit {}` return-value semantics |
| `CredentialStore` "Base64 obfuscation" security downgrade | Proper expect/actual with platform-specific encryption |
| No test migration strategy | `FakeFileSystem`-based in-memory DataStore for tests; credential contract tests |
| Fragile `MutableSharedFlow` observation pattern | Not needed — DataStore KMP has the same Flow API |
| SQLDelight `mapToList(Dispatchers.IO)` requirement | Not needed — Room Flow DAOs return `Flow<List<T>>` same as before |
| Version hardcoded to stale 2.0.2 | Room 3.0.0-alpha01 (latest alpha); DataStore 1.2.1 (stable) |

### Risks

- **Room 3.0 alpha stability**: This is an alpha release. The API may change between alphas. Pin to `3.0.0-alpha05` and test thoroughly. Alpha releases are not recommended for production builds; this scope is experimental.
- **Desktop DataStore path resolution**: The `resolveAppDataDir()` helper in `DataStoreFactory.desktop.kt` is a best-effort guess. May need platform-specific configuration per deployment.
- **iOS CredentialStore**: The iOS implementation using `platform.Security` requires Kotlin/Native interop with Security framework. This is the most complex expect/actual in this plan.
- **`@ConstructedBy` with KSP**: Room 3.0 KSP code generation with `@ConstructedBy` is new. If generation fails, may need to fall back to `@ConstructedBy` with a different factory pattern.
- **`feature/library` and `feature/settings` have no source files**: These modules depend on `:core:database` and `:core:preferences` but have zero `.kt` files. They won't break but their dependencies are untested — worth verifying they compile.
