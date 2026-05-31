package subsloth.testing.contract

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import subsloth.testing.assertions.assertThat

class EndpointReplayMetadataTest {
    @Test
    fun `parse handles frontend comments after prefix normalisation`() {
        val endpoint =
            Endpoint.parse(
                "https://media.tv/api/frontend/comments?commentable_type=Show&commentable_id=1723&sort=newest",
            )

        assertThat(endpoint).isEqualTo(Endpoint.Comments)
    }

    @Test
    fun `parse handles subscriptions before generic show detail routing`() {
        val postEndpoint =
            Endpoint.parse(
                "https://media.tv/en/shows/the-boys/subscriptions?kind=email",
            )
        val deleteEndpoint =
            Endpoint.parse(
                "https://media.tv/en/shows/the-boys/subscriptions/574880",
            )

        assertThat(postEndpoint).isEqualTo(Endpoint.Subscriptions)
        assertThat(deleteEndpoint).isEqualTo(Endpoint.Subscriptions)
    }

    @Test
    fun `replay metadata matches real transport semantics`() {
        assertThat(Endpoint.Statistics.methods).containsExactly(HttpMethod.POST)
        assertThat(Endpoint.PushSubscriptions.methods).containsExactly(HttpMethod.POST)
        assertThat(Endpoint.FavoriteMedia.methods).containsExactly(HttpMethod.POST, HttpMethod.DELETE)
        assertThat(Endpoint.SubtitleDownload.responseKind).isEqualTo(ResponseKind.SubRip)
        assertThat(Endpoint.Download.responseKind).isEqualTo(ResponseKind.RedirectLocation)
        assertThat(Endpoint.Download.responseStatus).isEqualTo(302)
    }

    @Test
    fun `multi-method endpoints require explicit method for fixture path`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                Endpoint.FavoriteMedia.resourcePath
            }

        assertThat(error).hasMessageThat().contains("method-specific fixture path")
    }
}
