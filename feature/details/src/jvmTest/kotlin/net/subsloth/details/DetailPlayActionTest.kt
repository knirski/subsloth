package net.subsloth.details

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class DetailPlayActionTest {
    @Test
    fun `null fraction plays from start`() {
        assertThat(detailPlayAction(null)).isEqualTo(DetailPlayAction.Play)
    }

    @Test
    fun `zero fraction plays from start`() {
        assertThat(detailPlayAction(0.0)).isEqualTo(DetailPlayAction.Play)
    }

    @Test
    fun `negative fraction plays from start`() {
        assertThat(detailPlayAction(-0.1)).isEqualTo(DetailPlayAction.Play)
    }

    @Test
    fun `sub one percent fraction resumes without percentage`() {
        assertThat(detailPlayAction(55.0 / 8060.0)).isEqualTo(DetailPlayAction.Resume)
    }

    @Test
    fun `one percent fraction shows resume percentage`() {
        assertThat(detailPlayAction(0.01)).isEqualTo(DetailPlayAction.ResumeAt(1))
    }

    @Test
    fun `half fraction shows resume percentage`() {
        assertThat(detailPlayAction(0.5)).isEqualTo(DetailPlayAction.ResumeAt(50))
    }
}
