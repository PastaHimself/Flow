package io.github.aedev.flow.ui.tv.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

internal fun NavGraphBuilder.tvChannelDestination(content: @Composable (String) -> Unit) {
    composable(
        route = TvRoutes.CHANNEL,
        arguments =
            listOf(
                navArgument(TvRoutes.CHANNEL_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) { entry ->
        // Navigation already decodes the route argument; the channel URL may itself contain escapes.
        content(entry.arguments?.getString(TvRoutes.CHANNEL_ARG).orEmpty())
    }
}
