package net.subsloth.details

import androidx.compose.ui.unit.dp
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class DetailHeroHeightTest {

    @Test
    fun `narrow screens keep the 16 by 9 ratio`() {
        assertThat(detailHeroHeight(411.dp)).isEqualTo(411.dp * 9f / 16f)
    }

    @Test
    fun `wide screens are capped`() {
        assertThat(detailHeroHeight(800.dp)).isEqualTo(300.dp)
        assertThat(detailHeroHeight(1200.dp)).isEqualTo(300.dp)
    }

    @Test
    fun `the cap applies exactly where 16 by 9 exceeds it`() {
        assertThat(detailHeroHeight(533.dp)).isEqualTo(533.dp * 9f / 16f)
        assertThat(detailHeroHeight(534.dp)).isEqualTo(300.dp)
    }
}
