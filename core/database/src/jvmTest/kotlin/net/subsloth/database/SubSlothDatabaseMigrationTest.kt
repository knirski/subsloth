package net.subsloth.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exercises [MIGRATION_5_6] directly on a bundled SQLite connection: the
 * migration must preserve rows, backfill the offline content type, and make
 * the new `(contentType, contentId)` keys accept a movie and an episode that
 * share a numeric id.
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
