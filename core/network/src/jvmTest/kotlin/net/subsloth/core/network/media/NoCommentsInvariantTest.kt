package net.subsloth.core.network.media

import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import net.subsloth.core.network.media.api.model.Episode as DtoEpisode
import net.subsloth.core.network.media.api.model.Movie as DtoMovie
import net.subsloth.core.network.media.api.model.MovieSummary as DtoMovieSummary
import net.subsloth.core.network.media.api.model.Show as DtoShow
import net.subsloth.core.network.media.api.model.ShowSummary as DtoShowSummary

/**
 * Cross-module invariant test ensuring no production code path fetches or
 * requires comments endpoints or web-only frontend comments resources.
 *
 * Complements [FixtureTest]'s fixture-level checks by scanning DTO field
 * names, mapper function names, and domain model field names for any
 * comment-related references.
 */
class NoCommentsInvariantTest {
    // ── DTO field names ──────────────────────────────────────────────────

    @Test
    fun `network DTOs have no comment-related fields`() {
        val dtoClasses: List<Class<*>> =
            listOf(
                DtoMovieSummary::class.java,
                DtoMovie::class.java,
                DtoShowSummary::class.java,
                DtoShow::class.java,
                DtoEpisode::class.java,
            )

        for (dtoClass in dtoClasses) {
            val offending =
                dtoClass.declaredFields
                    .map { it.name }
                    .filter { it.contains("comment", ignoreCase = true) }
            assertThat(offending).isEmpty()
        }
    }

    // ── Domain model field names ─────────────────────────────────────────

    @Test
    fun `domain media models have no comment-related fields`() {
        val domainClasses: List<Class<*>> =
            listOf(
                MovieSummary::class.java,
                MovieDetails::class.java,
                ShowSummary::class.java,
                ShowDetails::class.java,
                Media::class.java,
                MediaDetails::class.java,
                Episode::class.java,
            )

        for (domainClass in domainClasses) {
            val offending =
                domainClass.declaredFields
                    .map { it.name }
                    .filter { it.contains("comment", ignoreCase = true) }
            assertThat(offending).isEmpty()
        }
    }

    // ── Mapper function names ────────────────────────────────────────────

    @Test
    fun `Mapper has no comment or note-related functions`() {
        val mapperMethods = Mapper::class.java.declaredMethods
        val methodNames = mapperMethods.map { it.name }

        val offending =
            methodNames.filter { it.contains("comment", ignoreCase = true) || it.contains("note", ignoreCase = true) }
        assertThat(offending).isEmpty()
    }

    // ── Api methods ──────────────────────────────────────────────────────

    @Test
    fun `Api class has no comment-related methods`() {
        val methods = Api::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertThat(methodNames).doesNotContain("listComments")
        assertThat(methodNames).doesNotContain("getComments")
        assertThat(methodNames).doesNotContain("postComment")
        assertThat(methodNames).doesNotContain("deleteComment")
        val commentMethods = methodNames.filter { it.contains("comment", ignoreCase = true) }
        assertThat(commentMethods).isEmpty()
    }
}
