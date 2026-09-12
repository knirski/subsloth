package net.subsloth

import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavKeyValueTest {
    @Test
    fun `detail nav key values round trip through parseMediaId`() {
        val contentTypes = mapOf(
            Media.MediaId.Movie(MovieId(11)) to "movie",
            Media.MediaId.Show(ShowId(22)) to "show",
            Media.MediaId.Episode(EpisodeId(33)) to "episode",
        )

        contentTypes.forEach { (id, contentType) ->
            assertEquals(id, parseMediaId(id.toNavKeyValue(), contentType))
        }
    }
}
