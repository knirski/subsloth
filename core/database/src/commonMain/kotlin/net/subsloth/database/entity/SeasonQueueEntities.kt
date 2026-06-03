package net.subsloth.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "season_queues",
    indices = [Index(value = ["showId", "seasonNumber"], unique = true)],
)
data class SeasonQueueEntity(
    @PrimaryKey val id: String,
    val showId: String,
    val seasonNumber: Int,
    val status: String,
    val createdAtEpochSeconds: Long,
)

@Entity(
    tableName = "queue_items",
    foreignKeys = [
        ForeignKey(
            entity = SeasonQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["queueId"])],
)
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queueId: String,
    val episodeId: String,
    val episodeTitle: String,
    val qualityLabel: String?,
    val subtitleLanguages: String?,
    val sizeBytes: Long?,
    val status: String,
)
