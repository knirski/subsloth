package net.subsloth.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v5 -> v6: playback progress rows are keyed by content type as well as
 * content id.
 *
 * `movie` and `episode` ids are independent counters, so the old
 * `(profileKey, contentId)` / `(contentId)` unique indices let an episode
 * overwrite a movie's progress row. This migration rebuilds both tables with
 * a type-aware unique index and backfills the new
 * `offline_playback_progress.contentType` column from `downloaded_media`
 * (offline progress only exists for downloaded media). Rows whose download
 * record is already gone get an empty type and are ignored by readers.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_playback_progress_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileKey` TEXT NOT NULL, " +
                "`contentId` TEXT NOT NULL, " +
                "`contentType` TEXT NOT NULL, " +
                "`positionSeconds` INTEGER NOT NULL, " +
                "`durationSeconds` INTEGER NOT NULL, " +
                "`updatedAtEpochSeconds` INTEGER NOT NULL)",
        )
        connection.execSQL(
            "INSERT OR REPLACE INTO `account_playback_progress_new` " +
                "(`id`, `profileKey`, `contentId`, `contentType`, " +
                "`positionSeconds`, `durationSeconds`, `updatedAtEpochSeconds`) " +
                "SELECT `id`, `profileKey`, `contentId`, `contentType`, " +
                "`positionSeconds`, `durationSeconds`, `updatedAtEpochSeconds` " +
                "FROM `account_playback_progress`",
        )
        connection.execSQL("DROP TABLE `account_playback_progress`")
        connection.execSQL(
            "ALTER TABLE `account_playback_progress_new` RENAME TO `account_playback_progress`",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_account_playback_progress_profileKey_contentType_contentId` " +
                "ON `account_playback_progress` (`profileKey`, `contentType`, `contentId`)",
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `offline_playback_progress_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`contentId` TEXT NOT NULL, " +
                "`contentType` TEXT NOT NULL, " +
                "`positionSeconds` INTEGER NOT NULL, " +
                "`durationSeconds` INTEGER NOT NULL, " +
                "`updatedAtEpochSeconds` INTEGER NOT NULL)",
        )
        connection.execSQL(
            "INSERT INTO `offline_playback_progress_new` " +
                "(`id`, `contentId`, `contentType`, `positionSeconds`, `durationSeconds`, `updatedAtEpochSeconds`) " +
                "SELECT p.`id`, p.`contentId`, " +
                "COALESCE((SELECT d.`mediaType` FROM `downloaded_media` d " +
                "WHERE d.`contentId` = p.`contentId` ORDER BY d.`id` LIMIT 1), ''), " +
                "p.`positionSeconds`, p.`durationSeconds`, p.`updatedAtEpochSeconds` " +
                "FROM `offline_playback_progress` p",
        )
        connection.execSQL("DROP TABLE `offline_playback_progress`")
        connection.execSQL(
            "ALTER TABLE `offline_playback_progress_new` RENAME TO `offline_playback_progress`",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_playback_progress_contentType_contentId` " +
                "ON `offline_playback_progress` (`contentType`, `contentId`)",
        )
    }
}
