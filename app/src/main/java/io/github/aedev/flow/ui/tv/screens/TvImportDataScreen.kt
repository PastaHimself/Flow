package io.github.aedev.flow.ui.tv.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.settings.ImportViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * TV-native entry point for file based restores. The system document picker keeps
 * storage access permissionless and works with local, USB and document-provider files.
 */
@Composable
fun TvImportDataScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val viewModel: ImportViewModel = hiltViewModel(activity)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    val flowBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importFlowBackup)
        }
    val masterBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importMasterBackup)
        }
    val newPipeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importNewPipe)
        }
    val libreTubeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importLibreTube)
        }
    val takeoutLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importYouTubeTakeout)
        }

    TvScreenScaffold(
        title = stringResource(R.string.import_data_title),
        subtitle = stringResource(R.string.import_migrate_description),
        modifier = modifier,
        action = {
            TvButton(
                text = stringResource(R.string.close),
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                onClick = onNavigateBack,
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.overscanHorizontal)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ImportStatus(state = state, onDismiss = viewModel::dismiss)

            Column(
                modifier = Modifier.widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvSelectionRow(
                    label = stringResource(R.string.import_flow_backup_item_title),
                    supportingText = stringResource(R.string.import_flow_backup_desc),
                    selected = false,
                    onClick = { flowBackupLauncher.launch(jsonMimeTypes) },
                    modifier = Modifier.tvInitialFocus(),
                )
                TvSelectionRow(
                    label = stringResource(R.string.import_master_backup_title),
                    supportingText = stringResource(R.string.import_master_backup_desc),
                    selected = false,
                    onClick = { masterBackupLauncher.launch(zipMimeTypes) },
                )
                TvSelectionRow(
                    label = stringResource(R.string.import_from_newpipe),
                    supportingText = stringResource(R.string.import_from_newpipe_desc),
                    selected = false,
                    onClick = { newPipeLauncher.launch(jsonMimeTypes) },
                )
                TvSelectionRow(
                    label = stringResource(R.string.import_from_libretube),
                    supportingText = stringResource(R.string.import_from_libretube_desc),
                    selected = false,
                    onClick = { libreTubeLauncher.launch(jsonMimeTypes) },
                )
                TvSelectionRow(
                    label = stringResource(R.string.import_yt_takeout_all),
                    supportingText = stringResource(R.string.import_yt_takeout_all_desc),
                    selected = false,
                    onClick = { takeoutLauncher.launch(zipMimeTypes) },
                )
            }
        }
    }
}

@Composable
private fun ImportStatus(
    state: ImportViewModel.State,
    onDismiss: () -> Unit,
) {
    when (state) {
        ImportViewModel.State.Idle -> Unit
        is ImportViewModel.State.Running -> {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = state.label, style = MaterialTheme.typography.titleMedium)
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.current.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }

        is ImportViewModel.State.Success -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = state.message ?: stringResource(R.string.import_success, state.label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TvButton(text = stringResource(R.string.dismiss), onClick = onDismiss)
            }
        }

        is ImportViewModel.State.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.import_failed_template, state.message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                TvButton(text = stringResource(R.string.dismiss), onClick = onDismiss)
            }
        }
    }
}

private val jsonMimeTypes =
    arrayOf(
        "application/json",
        "text/json",
        "text/plain",
        "application/octet-stream",
    )

private val zipMimeTypes =
    arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )
