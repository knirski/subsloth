package net.subsloth.core.domain.policy

import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.media.Episode

/**
 * Pure policies for next-episode calculation and offline availability.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object NextEpisodePolicy {
    /**
     * Returns the available episodes sorted by (season, episode) order.
     *
     * This is exposed so callers that work with the same episode list
     * repeatedly can avoid redundant O(N log N) sorting by sorting once
     * and passing the result to [nextEpisodeFromSorted].
     */
    fun sortedAvailableEpisodes(episodes: List<Episode>): List<Episode> =
        episodes
            .filter { it.availability is Availability.Available }
            .sortedWith(compareBy<Episode> { it.seasonNumber }.thenBy { it.episodeNumber })

    /**
     * Finds the next playable episode after [current] in the [episodes] list.
     *
     * - Filters out unreleased episodes and sorts by (season, episode) order.
     * - Returns `null` when [current] is the last available episode or
     *   no subsequent playable episode exists.
     *
     * When calling this function repeatedly with the same [episodes] list,
     * use [nextEpisodeFromSorted] with a pre-sorted list to avoid redundant
     * O(N log N) sorting on every call.
     */
    fun nextEpisode(
        current: Episode,
        episodes: List<Episode>,
    ): Episode? {
        val sorted = sortedAvailableEpisodes(episodes)

        val currentIndex = sorted.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return null

        val nextIndex = currentIndex + 1
        if (nextIndex >= sorted.size) return null

        return sorted[nextIndex]
    }

    /**
     * Finds the next playable episode after [current] in a pre-sorted list.
     *
     * - [sortedEpisodes] must already be filtered to available episodes and
     *   sorted by (season, episode) order — see [sortedAvailableEpisodes].
     * - Returns `null` when [current] is the last available episode or
     *   no subsequent playable episode exists.
     *
     * Prefer this function when calling repeatedly with the same episode list
     * to avoid redundant O(N log N) sorting on every call.
     */
    fun nextEpisodeFromSorted(
        current: Episode,
        sortedEpisodes: List<Episode>,
    ): Episode? {
        val currentIndex = sortedEpisodes.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return null

        val nextIndex = currentIndex + 1
        if (nextIndex >= sortedEpisodes.size) return null

        return sortedEpisodes[nextIndex]
    }

    /**
     * Finds the next offline-playable episode after [current], considering
     * only episodes that are already downloaded.
     *
     * This is a convenience function that filters and sorts [episodes]
     * internally. When calling repeatedly, use [nextEpisodeOfflineFromSorted]
     * with a pre-sorted list to avoid redundant sorting.
     *
     * - Follows the same ordering as [nextEpisode].
     * - Only returns an episode whose [id] is in [downloadedEpisodeIds].
     * - Returns `null` when no subsequent downloaded episode exists.
     */
    fun nextEpisodeOffline(
        current: Episode,
        episodes: List<Episode>,
        downloadedEpisodeIds: Set<EpisodeId>,
    ): Episode? {
        val sorted = sortedAvailableEpisodes(episodes)
        return nextEpisodeOfflineFromSorted(current, sorted, downloadedEpisodeIds)
    }

    /**
     * Finds the next offline-playable episode after [current] in a pre-sorted
     * list, considering only episodes that are already downloaded.
     *
     * - [sortedEpisodes] must already be filtered and sorted via
     *   [sortedAvailableEpisodes].
     * - Only returns an episode whose [id] is in [downloadedEpisodeIds].
     * - Returns `null` when no subsequent downloaded episode exists.
     *
     * Prefer this function when calling repeatedly to avoid redundant sorting.
     */
    fun nextEpisodeOfflineFromSorted(
        current: Episode,
        sortedEpisodes: List<Episode>,
        downloadedEpisodeIds: Set<EpisodeId>,
    ): Episode? {
        val currentIndex = sortedEpisodes.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return null

        return sortedEpisodes
            .asSequence()
            .drop(currentIndex + 1)
            .firstOrNull { it.id in downloadedEpisodeIds }
    }
}
