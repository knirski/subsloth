package net.subsloth.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import net.subsloth.database.dao.AccountPlaybackProgressDao
import net.subsloth.database.dao.CachedCatalogDao
import net.subsloth.database.dao.DownloadedMediaDao
import net.subsloth.database.dao.DownloadedSubtitleDao
import net.subsloth.database.dao.FavoriteDao
import net.subsloth.database.dao.LocalLibraryRecordDao
import net.subsloth.database.dao.OfflineDisplayMetadataDao
import net.subsloth.database.dao.OfflinePlaybackProgressDao
import net.subsloth.database.dao.SeasonQueueDao
import net.subsloth.database.dao.SubscriptionDao
import net.subsloth.database.dao.WatchLaterDao
import net.subsloth.database.dao.WatchedStateDao
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import net.subsloth.database.entity.OfflineDisplayMetadataEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import net.subsloth.database.entity.SubscriptionEntity
import net.subsloth.database.entity.WatchLaterEntity
import net.subsloth.database.entity.WatchedStateEntity

@ConstructedBy(SubSlothDatabaseCtor::class)
@Database(
    entities = [
        CachedCatalogItemEntity::class,
        CachedCatalogGenreEntity::class,
        CachedCatalogCountryEntity::class,
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
        SeasonQueueEntity::class,
        QueueItemEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SubSlothDatabase : RoomDatabase() {
    abstract fun cachedCatalogDao(): CachedCatalogDao

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

    abstract fun seasonQueueDao(): SeasonQueueDao
}
