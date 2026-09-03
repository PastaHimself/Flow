package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.OpmlSubscriptionImporter
import io.github.aedev.flow.notification.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpmlImportViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val importer = OpmlSubscriptionImporter(context)
        private val _state = MutableStateFlow<ImportViewModel.State>(ImportViewModel.State.Idle)
        val state: StateFlow<ImportViewModel.State> = _state.asStateFlow()

        fun importSubscriptions(uri: Uri) {
            if (_state.value is ImportViewModel.State.Running) return

            val label = context.getString(R.string.import_subscriptions_xml_title)
            viewModelScope.launch {
                try {
                    updateProgress(label, 0, 0)
                    val result =
                        importer.import(uri) { current, total ->
                            updateProgress(label, current, total)
                        }
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        _state.value = ImportViewModel.State.Success(label, count = count)
                        if (NotificationHelper.hasNotificationPermission(context)) {
                            NotificationHelper.showImportComplete(context, label, count)
                        }
                    } else {
                        val message = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                        _state.value = ImportViewModel.State.Error(label, message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value =
                        ImportViewModel.State.Error(
                            label,
                            e.message ?: context.getString(R.string.unknown_error),
                        )
                } finally {
                    NotificationHelper.cancelImportNotification(context)
                }
            }
        }

        fun dismiss() {
            _state.value = ImportViewModel.State.Idle
        }

        private fun updateProgress(
            label: String,
            current: Int,
            total: Int,
        ) {
            _state.value = ImportViewModel.State.Running(label, current, total)
            if (NotificationHelper.hasNotificationPermission(context)) {
                NotificationHelper.showImportProgress(context, label, current, total)
            }
        }
    }
