package io.github.aedev.flow.ui.tv.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aedev.flow.R

/** Stable top-level destinations for Flow's TV interface. */
enum class TvDestination(
    val route: String,
    val testTag: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", "tv_nav_home", R.string.nav_home, Icons.Outlined.Home),
    SUBSCRIPTIONS("subscriptions", "tv_nav_subscriptions", R.string.top_bar_subscriptions_title, Icons.Outlined.Subscriptions),
    SEARCH("search", "tv_nav_search", R.string.search, Icons.Outlined.Search),
    LIBRARY("library", "tv_nav_library", R.string.library, Icons.Outlined.VideoLibrary),
    SETTINGS("settings", "tv_nav_settings", R.string.settings, Icons.Outlined.Settings);

    companion object {
        val primary: List<TvDestination> = entries.toList()

        fun fromRoute(route: String?): TvDestination =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}
