package io.github.aedev.flow.data.local

import android.content.Context
import android.net.Uri
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpmlSubscriptionImporter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val subscriptionRepository = SubscriptionRepository.getInstance(appContext)

    suspend fun import(
        uri: Uri,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val xml =
                    appContext.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return@withContext Result.failure(Exception("Could not read file"))

                val entries = OpmlSubscriptionParser.parse(xml)
                if (entries.isEmpty()) {
                    return@withContext Result.failure(Exception("no_entries"))
                }

                onProgress?.invoke(0, entries.size)
                val subscribedAt = System.currentTimeMillis()
                val subscriptions =
                    entries.mapIndexed { index, entry ->
                        ChannelSubscription(
                            channelId = entry.channelId,
                            channelName = entry.channelName,
                            channelThumbnail = "",
                            subscribedAt = subscribedAt - index,
                        )
                    }
                subscriptionRepository.subscribeAll(subscriptions)
                onProgress?.invoke(subscriptions.size, subscriptions.size)

                val channelNames = subscriptions.map(ChannelSubscription::channelName).filter(String::isNotBlank)
                if (channelNames.isNotEmpty()) {
                    runCatching {
                        FlowNeuroEngine.bootstrapFromSubscriptions(appContext, channelNames)
                    }
                }

                Result.success(subscriptions.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
