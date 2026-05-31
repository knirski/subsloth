package subsloth.database

import androidx.room3.ConstructedBy
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

@ConstructedBy(SubSlothDatabaseCtor::class)
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
