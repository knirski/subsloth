package net.subsloth.core.model.download

import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SeasonDownloadQueueTest {
    @Test
    fun `SeasonDownloadQueue accepts valid season number`() {
        val queue = SeasonDownloadQueue(
            queueId = QueueId("q1"),
            showId = ShowId(1),
            seasonNumber = 0,
            items = persistentListOf(),
            execution = SeasonQueueExecution.PendingConfirmation,
            transferPreference = TransferPreference.WifiOnly,
        )
        assertThat(queue.seasonNumber).isEqualTo(0)
    }

    @Test
    fun `SeasonDownloadQueue rejects negative season number`() {
        assertThrows<IllegalArgumentException> {
            SeasonDownloadQueue(
                queueId = QueueId("q1"),
                showId = ShowId(1),
                seasonNumber = -1,
                items = persistentListOf(),
                execution = SeasonQueueExecution.PendingConfirmation,
                transferPreference = TransferPreference.WifiOnly,
            )
        }
    }

    @Test
    fun `SeasonQueueItemExecution Downloading requires 0 to 100 percent`() {
        SeasonQueueItemExecution.Downloading(0)
        SeasonQueueItemExecution.Downloading(50)
        SeasonQueueItemExecution.Downloading(100)
    }

    @Test
    fun `SeasonQueueItemExecution Downloading rejects negative`() {
        assertThrows<IllegalArgumentException> { SeasonQueueItemExecution.Downloading(-1) }
    }

    @Test
    fun `SeasonQueueItemExecution Downloading rejects over 100`() {
        assertThrows<IllegalArgumentException> { SeasonQueueItemExecution.Downloading(101) }
    }

    @Test
    fun `SeasonQueueExecution variants carry correct types`() {
        assertThat(SeasonQueueExecution.PendingConfirmation).isInstanceOf(SeasonQueueExecution::class.java)
        assertThat(SeasonQueueExecution.Queued).isInstanceOf(SeasonQueueExecution::class.java)
        assertThat(SeasonQueueExecution.Running(Media.MediaId.Episode(EpisodeId(1))))
            .isInstanceOf(SeasonQueueExecution::class.java)
        assertThat(SeasonQueueExecution.Completed).isInstanceOf(SeasonQueueExecution::class.java)
        assertThat(SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed))
            .isInstanceOf(SeasonQueueExecution::class.java)
    }

    @Test
    fun `SeasonQueueItemExecution variants carry correct types`() {
        assertThat(SeasonQueueItemExecution.Pending).isInstanceOf(SeasonQueueItemExecution::class.java)
        assertThat(SeasonQueueItemExecution.Completed).isInstanceOf(SeasonQueueItemExecution::class.java)
        assertThat(SeasonQueueItemExecution.Cancelled).isInstanceOf(SeasonQueueItemExecution::class.java)
        assertThat(SeasonQueueItemExecution.Failed(DownloadFailureReason.Unavailable))
            .isInstanceOf(SeasonQueueItemExecution::class.java)
    }
}
