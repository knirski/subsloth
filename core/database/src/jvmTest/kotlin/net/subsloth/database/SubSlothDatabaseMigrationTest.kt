package net.subsloth.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises [MIGRATION_5_6], [MIGRATION_6_7] and [MIGRATION_7_8] directly on a
 * bundled SQLite connection: the migrations must preserve rows, backfill the
 * offline content type, make the new `(contentType, contentId)` keys accept a
 * movie and an episode that share a numeric id, and drop the retired
 * offline-display-metadata table.
 */
class SubSlothDatabaseMigrationTest {

    @Test
    fun `migration 5 to 6 keeps progress per content type`() = runTest {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")
        try {
            createV5Schema(connection)
            connection.execSQL(
                "INSERT INTO account_playback_progress " +
                    "(profileKey, contentId, contentType, positionSeconds, durationSeconds, updatedAtEpochSeconds) " +
                    "VALUES ('user1', '7', 'movie', 300, 600, 10)",
            )
            connection.execSQL(
                "INSERT INTO offline_playback_progress " +
                    "(contentId, positionSeconds, durationSeconds, updatedAtEpochSeconds) " +
                    "VALUES ('7', 120, 600, 20)",
            )
            connection.execSQL(
                "INSERT INTO downloaded_media (contentId, mediaType) VALUES ('7', 'movie')",
            )

            MIGRATION_5_6.migrate(connection)

            assertEquals(
                300L,
                connection.scalarLong(
                    "SELECT positionSeconds FROM account_playback_progress " +
                        "WHERE profileKey = 'user1' AND contentType = 'movie' AND contentId = '7'",
                ),
            )
            assertEquals(
                "movie",
                connection.scalarText(
                    "SELECT contentType FROM offline_playback_progress WHERE contentId = '7'",
                ),
            )

            // The new keys let the same numeric id exist per content type.
            connection.execSQL(
                "INSERT INTO account_playback_progress " +
                    "(profileKey, contentId, contentType, positionSeconds, durationSeconds, updatedAtEpochSeconds) " +
                    "VALUES ('user1', '7', 'episode', 50, 600, 30)",
            )
            connection.execSQL(
                "INSERT INTO offline_playback_progress " +
                    "(contentId, contentType, positionSeconds, durationSeconds, updatedAtEpochSeconds) " +
                    "VALUES ('7', 'episode', 50, 600, 30)",
            )

            assertEquals(
                2L,
                connection.scalarLong(
                    "SELECT COUNT(*) FROM account_playback_progress WHERE profileKey = 'user1' AND contentId = '7'",
                ),
            )
            assertEquals(
                2L,
                connection.scalarLong(
                    "SELECT COUNT(*) FROM offline_playback_progress WHERE contentId = '7'",
                ),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun `migration 6 to 7 keys library tables per content type`() = runTest {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")
        try {
            createV6Schema(connection)
            connection.execSQL(
                "INSERT INTO favorites (profileKey, contentId, contentType) VALUES ('user1', '7', 'movie')",
            )
            connection.execSQL(
                "INSERT INTO watched_state " +
                    "(profileKey, contentId, contentType, isWatched, watchedAtEpochSeconds) " +
                    "VALUES ('user1', '7', 'movie', 1, 1000)",
            )
            connection.execSQL(
                "INSERT INTO offline_display_metadata (contentId, title) VALUES ('7', 'Movie 7')",
            )
            connection.execSQL(
                "INSERT INTO downloaded_media (contentId, mediaType) VALUES ('7', 'movie')",
            )

            MIGRATION_6_7.migrate(connection)

            // Existing rows survive with their type and payload.
            assertEquals(
                "movie",
                connection.scalarText(
                    "SELECT contentType FROM favorites WHERE profileKey = 'user1' AND contentId = '7'",
                ),
            )
            assertEquals(
                1000L,
                connection.scalarLong(
                    "SELECT watchedAtEpochSeconds FROM watched_state " +
                        "WHERE profileKey = 'user1' AND contentId = '7'",
                ),
            )
            assertEquals(
                "Movie 7",
                connection.scalarText(
                    "SELECT title FROM offline_display_metadata WHERE contentId = '7'",
                ),
            )
            // The backfilled metadata type comes from the download record.
            assertEquals(
                "movie",
                connection.scalarText(
                    "SELECT contentType FROM offline_display_metadata WHERE contentId = '7'",
                ),
            )

            // The new keys let the same numeric id exist per content type.
            connection.execSQL(
                "INSERT INTO favorites (profileKey, contentId, contentType) VALUES ('user1', '7', 'show')",
            )
            connection.execSQL(
                "INSERT INTO offline_display_metadata (contentId, contentType, title) " +
                    "VALUES ('7', 'episode', 'Episode 7')",
            )
            assertEquals(
                2L,
                connection.scalarLong(
                    "SELECT COUNT(*) FROM favorites WHERE profileKey = 'user1' AND contentId = '7'",
                ),
            )
            assertEquals(
                2L,
                connection.scalarLong(
                    "SELECT COUNT(*) FROM offline_display_metadata WHERE contentId = '7'",
                ),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun `migration 7 to 8 drops the unused offline display metadata table`() = runTest {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(":memory:")
        try {
            createV6Schema(connection)
            connection.execSQL(
                "INSERT INTO offline_display_metadata (contentId, title) VALUES ('7', 'Movie 7')",
            )

            MIGRATION_7_8.migrate(connection)

            assertEquals(
                0L,
                connection.scalarLong(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'offline_display_metadata'",
                ),
            )
        } finally {
            connection.close()
        }
    }

    private fun createV6Schema(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE favorites (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_favorites_profileKey_contentId " +
                "ON favorites (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE watch_later (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_watch_later_profileKey_contentId " +
                "ON watch_later (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE watched_state (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL, " +
                "isWatched INTEGER NOT NULL, watchedAtEpochSeconds INTEGER)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_watched_state_profileKey_contentId " +
                "ON watched_state (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE subscriptions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL, " +
                "subscribedAtEpochSeconds INTEGER NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_subscriptions_profileKey_contentId " +
                "ON subscriptions (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE local_library_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL, " +
                "addedAtEpochSeconds INTEGER NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_local_library_records_profileKey_contentId " +
                "ON local_library_records (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE offline_display_metadata (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contentId TEXT NOT NULL, " +
                "title TEXT NOT NULL, posterCacheKey TEXT, backdropCacheKey TEXT, episodeTitle TEXT, " +
                "seasonNumber INTEGER, episodeNumber INTEGER, effectiveQuality TEXT, " +
                "subtitleLanguages TEXT, durationSeconds INTEGER, localProgressSeconds INTEGER)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_offline_display_metadata_contentId " +
                "ON offline_display_metadata (contentId)",
        )
        connection.execSQL(
            "CREATE TABLE downloaded_media (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "contentId TEXT NOT NULL, mediaType TEXT NOT NULL)",
        )
    }

    private fun createV5Schema(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE account_playback_progress (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "profileKey TEXT NOT NULL, contentId TEXT NOT NULL, contentType TEXT NOT NULL, " +
                "positionSeconds INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, " +
                "updatedAtEpochSeconds INTEGER NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_account_playback_progress_profileKey_contentId " +
                "ON account_playback_progress (profileKey, contentId)",
        )
        connection.execSQL(
            "CREATE TABLE offline_playback_progress (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contentId TEXT NOT NULL, " +
                "positionSeconds INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, " +
                "updatedAtEpochSeconds INTEGER NOT NULL)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX index_offline_playback_progress_contentId " +
                "ON offline_playback_progress (contentId)",
        )
        connection.execSQL(
            "CREATE TABLE downloaded_media (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "contentId TEXT NOT NULL, mediaType TEXT NOT NULL)",
        )
    }

    private fun SQLiteConnection.scalarLong(sql: String): Long? {
        prepare(sql).use { statement ->
            if (!statement.step()) return null
            return statement.getLong(0)
        }
    }

    private fun SQLiteConnection.scalarText(sql: String): String? {
        prepare(sql).use { statement ->
            if (!statement.step()) return null
            return statement.getText(0)
        }
    }
}
