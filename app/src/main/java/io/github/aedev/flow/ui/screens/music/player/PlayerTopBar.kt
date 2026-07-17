package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.theme.FlowTouchTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopBar(
    playingFrom: String,
    onBackClick: () -> Unit,
    onSleepTimerClick: () -> Unit = {},
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    showSleepTimerAction: Boolean = true
) {
    CenterAlignedTopAppBar(
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = contentColor.copy(alpha = 0.9f)
                )
                Text(
                    text = playingFrom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        },
        navigationIcon = {
            val closeLabel = stringResource(R.string.close)
            IconButton(
                modifier = Modifier
                    .size(FlowTouchTarget.minimum)
                    .semantics { contentDescription = closeLabel },
                onClick = onBackClick,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = contentColor,
                )
            }
        },
        actions = {
            if (showSleepTimerAction) {
                val sleepTimerLabel = stringResource(R.string.sleep_timer)
                IconButton(
                    modifier = Modifier
                        .size(FlowTouchTarget.minimum)
                        .semantics { contentDescription = sleepTimerLabel },
                    onClick = onSleepTimerClick,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = if (SleepTimerManager.isActive) activeColor else contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            val moreOptionsLabel = stringResource(R.string.more_options)
            IconButton(
                modifier = Modifier
                    .size(FlowTouchTarget.minimum)
                    .semantics { contentDescription = moreOptionsLabel },
                onClick = onMoreOptionsClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}
