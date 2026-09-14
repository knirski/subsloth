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

/**
 * v6 -> v7: the remaining account-scoped library tables are keyed by content
 * type as well as content id.
 *
 * [MIGRATION_5_6] fixed playback progress, but `favorites`, `watch_later`,
 * `watched_state`, `subscriptions` and `local_library_records` still had a
 * `(profileKey, contentId)` unique index, and `offline_display_metadata` a
 * `(contentId)` index. Because movie, show and episode ids are independent
 * counters, `@Insert(onConflict = REPLACE)` silently overwrote one type's row
 * with another's (and a watched movie could mark an unrelated episode as
 * watched). Each table is rebuilt with a type-aware unique index;
 * `offline_display_metadata` gains a `contentType` column backfilled from
 * `downloaded_media.mediaType` (offline metadata only exists for downloads).
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.rebuildProfileScopedTable(
            table = "favorites",
            columns = listOf(
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "`profileKey` TEXT NOT NULL",
                "`contentId` TEXT NOT NULL",
                "`contentType` TEXT NOT NULL",
            ),
            selectColumns = listOf("id", "profileKey", "contentId", "contentType"),
        )
        connection.rebuildProfileScopedTable(
            table = "watch_later",
            columns = listOf(
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "`profileKey` TEXT NOT NULL",
                "`contentId` TEXT NOT NULL",
                "`contentType` TEXT NOT NULL",
            ),
            selectColumns = listOf("id", "profileKey", "contentId", "contentType"),
        )
        connection.rebuildProfileScopedTable(
            table = "watched_state",
            columns = listOf(
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "`profileKey` TEXT NOT NULL",
                "`contentId` TEXT NOT NULL",
                "`contentType` TEXT NOT NULL",
                "`isWatched` INTEGER NOT NULL",
                "`watchedAtEpochSeconds` INTEGER",
            ),
            selectColumns = listOf(
                "id",
                "profileKey",
                "contentId",
                "contentType",
                "isWatched",
                "watchedAtEpochSeconds",
            ),
        )
        connection.rebuildProfileScopedTable(
            table = "subscriptions",
            columns = listOf(
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "`profileKey` TEXT NOT NULL",
                "`contentId` TEXT NOT NULL",
                "`contentType` TEXT NOT NULL",
                "`subscribedAtEpochSeconds` INTEGER NOT NULL",
            ),
            selectColumns = listOf(
                "id",
                "profileKey",
                "contentId",
                "contentType",
                "subscribedAtEpochSeconds",
            ),
        )
        connection.rebuildProfileScopedTable(
            table = "local_library_records",
            columns = listOf(
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                "`profileKey` TEXT NOT NULL",
                "`contentId` TEXT NOT NULL",
                "`contentType` TEXT NOT NULL",
                "`addedAtEpochSeconds` INTEGER NOT NULL",
            ),
            selectColumns = listOf(
                "id",
                "profileKey",
                "contentId",
                "contentType",
                "addedAtEpochSeconds",
            ),
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `offline_display_metadata_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`contentId` TEXT NOT NULL, " +
                "`contentType` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`posterCacheKey` TEXT, " +
                "`backdropCacheKey` TEXT, " +
                "`episodeTitle` TEXT, " +
                "`seasonNumber` INTEGER, " +
                "`episodeNumber` INTEGER, " +
                "`effectiveQuality` TEXT, " +
                "`subtitleLanguages` TEXT, " +
                "`durationSeconds` INTEGER, " +
                "`localProgressSeconds` INTEGER)",
        )
        connection.execSQL(
            "INSERT INTO `offline_display_metadata_new` " +
                "(`id`, `contentId`, `contentType`, `title`, `posterCacheKey`, `backdropCacheKey`, " +
                "`episodeTitle`, `seasonNumber`, `episodeNumber`, `effectiveQuality`, " +
                "`subtitleLanguages`, `durationSeconds`, `localProgressSeconds`) " +
                "SELECT m.`id`, m.`contentId`, " +
                "COALESCE((SELECT d.`mediaType` FROM `downloaded_media` d " +
                "WHERE d.`contentId` = m.`contentId` ORDER BY d.`id` LIMIT 1), ''), " +
                "m.`title`, m.`posterCacheKey`, m.`backdropCacheKey`, m.`episodeTitle`, " +
                "m.`seasonNumber`, m.`episodeNumber`, m.`effectiveQuality`, m.`subtitleLanguages`, " +
                "m.`durationSeconds`, m.`localProgressSeconds` " +
                "FROM `offline_display_metadata` m",
        )
        connection.execSQL("DROP TABLE `offline_display_metadata`")
        connection.execSQL(
            "ALTER TABLE `offline_display_metadata_new` RENAME TO `offline_display_metadata`",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_display_metadata_contentType_contentId` " +
                "ON `offline_display_metadata` (`contentType`, `contentId`)",
        )
    }
}

private suspend fun SQLiteConnection.rebuildProfileScopedTable(
    table: String,
    columns: List<String>,
    selectColumns: List<String>,
) {
    execSQL(
        "CREATE TABLE IF NOT EXISTS `${table}_new` (" +
            columns.joinToString(", ") +
            ")",
    )
    execSQL(
        "INSERT OR REPLACE INTO `${table}_new` " +
            "(${selectColumns.joinToString(", ") { "`$it`" }}) " +
            "SELECT ${selectColumns.joinToString(", ") { "`$it`" }} FROM `$table`",
    )
    execSQL("DROP TABLE `$table`")
    execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
    execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_profileKey_contentType_contentId` " +
            "ON `$table` (`profileKey`, `contentType`, `contentId`)",
    )
}
