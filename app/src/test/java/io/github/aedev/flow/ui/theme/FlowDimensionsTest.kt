package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowDimensionsTest {
    @Test
    fun `content rhythm uses larger section gaps than item gaps`() {
        assertTrue(Dimensions.ItemSpacing < Dimensions.SectionSpacing)
        assertEquals(16f, Dimensions.ContentPaddingHorizontal.value, 0.001f)
    }

    @Test
    fun `media surfaces use rounded but distinct corner scales`() {
        assertEquals(12f, Dimensions.ThumbnailCornerRadius.value, 0.001f)
        assertEquals(20f, Dimensions.CardCornerRadius.value, 0.001f)
        assertTrue(Dimensions.CardCornerRadius > Dimensions.ThumbnailCornerRadius)
    }

    @Test
    fun `spacing scale increases monotonically and is anchored to the content gutter`() {
        val scale =
            listOf(
                Dimensions.Spacing.Hairline,
                Dimensions.Spacing.Xxs,
                Dimensions.Spacing.Xs,
                Dimensions.Spacing.Sm,
                Dimensions.Spacing.Md,
                Dimensions.Spacing.MdPlus,
                Dimensions.Spacing.Lg,
                Dimensions.Spacing.Xl,
                Dimensions.Spacing.Xxl,
                Dimensions.Spacing.Xxxl,
                Dimensions.Spacing.Xxxxl,
            )
        assertTrue(scale.zipWithNext().all { (a, b) -> a < b })
        assertEquals(8f, Dimensions.Spacing.Md.value, 0.001f)
        assertEquals(12f, Dimensions.Spacing.Lg.value, 0.001f)
        assertEquals(16f, Dimensions.Spacing.Xl.value, 0.001f)
    }

    @Test
    fun `radius and control-height scales expose consistent standard anchors`() {
        assertEquals(12f, Dimensions.Radius.Md.value, 0.001f)
        assertEquals(20f, Dimensions.Radius.Xl.value, 0.001f)
        assertEquals(48f, Dimensions.ControlHeight.Touch.value, 0.001f)
        assertEquals(24f, Dimensions.IconSize.Xl.value, 0.001f)
    }
}
