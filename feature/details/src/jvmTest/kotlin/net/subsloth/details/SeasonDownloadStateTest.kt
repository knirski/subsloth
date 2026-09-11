package net.subsloth.details

import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class SeasonDownloadStateTest {
    @Test
    fun `no queue is idle`() {
        assertThat(seasonDownloadState(emptyList(), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Idle)
    }

    @Test
    fun `pending confirmation is queued`() {
        val queue = queue(SeasonQueueExecution.PendingConfirmation)
        assertThat(seasonDownloadState(listOf(queue), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Queued)
    }

    @Test
    fun `running is downloading`() {
        val queue = queue(SeasonQueueExecution.Running(Media.MediaId.Episode(EpisodeId(1))))
        assertThat(seasonDownloadState(listOf(queue), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Downloading)
    }

    @Test
    fun `completed is completed`() {
        val queue = queue(SeasonQueueExecution.Completed)
        assertThat(seasonDownloadState(listOf(queue), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Completed)
    }

    @Test
    fun `paused and failed are failed`() {
        val paused = queue(SeasonQueueExecution.Paused(DownloadFailureReason.NeedsWifi))
        val failed = queue(SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed))

        assertThat(seasonDownloadState(listOf(paused), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Failed)
        assertThat(seasonDownloadState(listOf(failed), seasonNumber = 1)).isEqualTo(SeasonDownloadState.Failed)
    }

    @Test
    fun `queue for another season is ignored`() {
        assertThat(seasonDownloadState(listOf(queue(SeasonQueueExecution.Completed)), seasonNumber = 2))
            .isEqualTo(SeasonDownloadState.Idle)
    }

    private fun queue(execution: SeasonQueueExecution) = SeasonDownloadQueue(
        queueId = QueueId("1-1"),
        showId = ShowId(1),
        seasonNumber = 1,
        items = persistentListOf(),
        execution = execution,
        transferPreference = TransferPreference.WifiOnly,
    )
}
