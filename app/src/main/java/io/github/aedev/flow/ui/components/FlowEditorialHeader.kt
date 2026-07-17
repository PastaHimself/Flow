package io.github.aedev.flow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.ui.theme.FlowSpacing
import io.github.aedev.flow.ui.theme.FlowTouchTarget

@Composable
fun FlowEditorialHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    avatarUrl: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showMetadata: Boolean = true,
    tabs: List<String> = emptyList(),
    selectedTab: Int? = null,
    onTabSelected: ((Int) -> Unit)? = null,
) {
    val activeTab = selectedTab?.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)) ?: 0

    Column(modifier = modifier.fillMaxWidth()) {
        if (showMetadata) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlowSpacing.md, vertical = FlowSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(FlowSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FlowSpacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(FlowSpacing.xs),
                    content = actions,
                )
            }
        }

        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                edgePadding = FlowSpacing.md,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { positions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(positions[activeTab]),
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = index == activeTab,
                        onClick = { onTabSelected?.invoke(index) },
                        modifier = Modifier.heightIn(min = FlowTouchTarget.minimum),
                        text = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}
