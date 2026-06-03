package net.subsloth.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity

@Dao
interface SeasonQueueDao {
    @Query("SELECT * FROM season_queues")
    fun getAllQueues(): Flow<List<SeasonQueueEntity>>

    @Query("SELECT * FROM season_queues WHERE id = :queueId")
    suspend fun getQueue(queueId: String): SeasonQueueEntity?

    @Query("SELECT * FROM queue_items WHERE queueId = :queueId")
    suspend fun getItemsForQueue(queueId: String): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueue(entity: SeasonQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(entity: QueueItemEntity)

    @Query("DELETE FROM season_queues WHERE id = :queueId")
    suspend fun deleteQueue(queueId: String)

    @Query("DELETE FROM season_queues WHERE status = 'completed' AND createdAtEpochSeconds < :beforeEpochSeconds")
    suspend fun deleteCompletedQueuesOlderThan(beforeEpochSeconds: Long)
}
