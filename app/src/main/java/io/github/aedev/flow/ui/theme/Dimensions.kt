package io.github.aedev.flow.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central design-token store for the Flow UI.
 *
 * Dimension values should be pulled from here rather than hardcoded in composables so spacing,
 * corner radius, icon sizing and control heights stay pixel-consistent across every surface.
 *
 * Legacy flat tokens (e.g. [ListItemHeight], [ThumbnailCornerRadius]) are retained for call sites
 * that predate the token groups; new code should prefer the named groups below.
 */
object Dimensions {
    // ---------------------------------------------------------------- legacy flat tokens
    val ListItemHeight: Dp = 68.dp
    val ListThumbnailSize: Dp = 52.dp
    val SuggestionItemHeight: Dp = 56.dp

    val GridThumbnailHeightBig: Dp = 136.dp
    val GridThumbnailHeightSmall: Dp = 112.dp
    val AlbumThumbnailSize: Dp = 144.dp

    val ThumbnailCornerRadius: Dp = 12.dp
    val CardCornerRadius: Dp = 20.dp

    val MoodButtonHeight: Dp = 48.dp

    val AppBarHeight: Dp = 64.dp
    val MiniPlayerHeight: Dp = 64.dp
    val NavigationBarHeight: Dp = 80.dp

    val ContentPaddingHorizontal: Dp = 16.dp
    val ContentPaddingVertical: Dp = 12.dp
    val ItemSpacing: Dp = 12.dp
    val SectionSpacing: Dp = 20.dp

    val PlayerHorizontalPadding: Dp = 32.dp

    // ---------------------------------------------------------------- spacing scale
    object Spacing {
        /** Hairline rule / micro-gaps (dividers, badge offsets). */
        val Hairline: Dp = 1.dp

        /** Tight 2dp gaps used inside compact controls. */
        val Xxs: Dp = 2.dp

        /** 4dp — dense inner padding and small gaps. */
        val Xs: Dp = 4.dp

        /** 6dp — vertical rhythm between a thumbnail and its text. */
        val Sm: Dp = 6.dp

        /** 8dp — standard inner gutter inside rows and buttons. */
        val Md: Dp = 8.dp

        /** 10dp — fine vertical gaps between meta lines. */
        val MdPlus: Dp = 10.dp

        /** 12dp — default item gap and thumbnail padding. */
        val Lg: Dp = 12.dp

        /** 16dp — primary screen horizontal padding. */
        val Xl: Dp = 16.dp

        /** 20dp — section and card internal padding. */
        val Xxl: Dp = 20.dp

        /** 24dp — generous padding for large cards and bottom sheets. */
        val Xxxl: Dp = 24.dp

        /** 32dp — immersive player and hero spacing. */
        val Xxxxl: Dp = 32.dp
    }

    // ---------------------------------------------------------------- radius scale
    object Radius {
        /** 6dp — tight corners for badges and small chips. */
        val Xs: Dp = 6.dp

        /** 8dp — list rows, compact cards. */
        val Sm: Dp = 8.dp

        /** 12dp — default thumbnail and standard card corner. */
        val Md: Dp = 12.dp

        /** 16dp — large cards and inner panels. */
        val Lg: Dp = 16.dp

        /** 20dp — hero cards and prominent surfaces. */
        val Xl: Dp = 20.dp

        /** 24dp — generous rounded surfaces and bottom sheets. */
        val Xxl: Dp = 24.dp
    }

    // ---------------------------------------------------------------- icon size scale
    object IconSize {
        /** 14dp — inline meta icons. */
        val Xs: Dp = 14.dp

        /** 16dp — default compact icons. */
        val Sm: Dp = 16.dp

        /** 18dp — secondary action icons. */
        val Md: Dp = 18.dp

        /** 20dp — standard action icons. */
        val Lg: Dp = 20.dp

        /** 24dp — primary / prominent action icons. */
        val Xl: Dp = 24.dp

        /** 32dp — hero and overlay icons. */
        val Xxl: Dp = 32.dp
    }

    // ---------------------------------------------------------------- elevation scale
    object Elevation {
        /** 0.5dp — resting, nearly-flat surfaces. */
        val None: Dp = 0.dp

        /** 1dp — subtle separation for standard cards. */
        val Card: Dp = 1.dp

        /** 3dp — elevated cards and hover states. */
        val Raised: Dp = 3.dp

        /** 8dp — floating elements such as the mini player. */
        val Floating: Dp = 8.dp

        /** 12dp — bottom sheets and modal surfaces. */
        val Sheet: Dp = 12.dp
    }

    // ---------------------------------------------------------------- control height scale
    object ControlHeight {
        /** 32dp — compact chips and small controls. */
        val Small: Dp = 32.dp

        /** 40dp — standard tappable controls. */
        val Standard: Dp = 40.dp

        /** 48dp — Material minimum touch-target height. */
        val Touch: Dp = 48.dp

        /** 56dp — prominent controls / action rows. */
        val Large: Dp = 56.dp
    }
}

enum class GridItemSize {
    BIG,
    SMALL,
    ;

    val thumbnailHeight: Dp
        get() =
            when (this) {
                BIG -> Dimensions.GridThumbnailHeightBig
                SMALL -> Dimensions.GridThumbnailHeightSmall
            }
}
