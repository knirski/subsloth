package net.subsloth.testing.contract

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class CaptureApiPlanTest {
    @Test
    fun `capture plan contains only native kodi endpoints`() {
        val requests = CaptureApi.capturePlan()

        assertThat(requests.map { it.endpoint })
            .containsExactly(
                Endpoint.Movies,
                Endpoint.Shows,
                Endpoint.MovieDetail,
                Endpoint.ShowDetail,
                Endpoint.EpisodeDetail,
            ).inOrder()
        assertThat(requests.map { it.path })
            .containsExactly(
                "/movies",
                "/shows",
                "/movies/{id}",
                "/shows/{id}",
                "/episodes/{id}",
            ).inOrder()
    }
}
