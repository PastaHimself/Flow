package io.github.aedev.flow.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

internal sealed class OpmlImportException : Exception() {
    data object UnreadableFile : OpmlImportException()

    data object NoSubscriptions : OpmlImportException()
}

class OpmlSubscriptionImporter
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        private val appContext = context.applicationContext

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
                            ?: return@withContext Result.failure(OpmlImportException.UnreadableFile)

                    val entries = OpmlSubscriptionParser.parse(xml)
                    if (entries.isEmpty()) {
                        return@withContext Result.failure(OpmlImportException.NoSubscriptions)
                    }

                    val existingIds = subscriptionRepository.getAllSubscriptionIds()
                    val missingSubscriptions =
                        buildMissingOpmlSubscriptions(
                            entries = entries,
                            existingIds = existingIds,
                            subscribedAt = System.currentTimeMillis(),
                        )
                    val subscriptions =
                        enrichOpmlSubscriptionAvatars(
                            subscriptions = missingSubscriptions,
                            avatarFetcher = ::fetchYouTubeChannelAvatar,
                            onProgress = onProgress,
                        )
                    subscriptionRepository.subscribeAll(subscriptions)

                    val channelNames = subscriptions.map(ChannelSubscription::channelName).filter(String::isNotBlank)
                    if (channelNames.isNotEmpty()) {
                        runCatching {
                            FlowNeuroEngine.bootstrapFromSubscriptions(appContext, channelNames)
                        }
                    }

                    Result.success(subscriptions.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
    }

internal fun buildMissingOpmlSubscriptions(
    entries: List<OpmlSubscriptionEntry>,
    existingIds: Set<String>,
    subscribedAt: Long,
): List<ChannelSubscription> =
    entries
        .filterNot { it.channelId in existingIds }
        .mapIndexed { index, entry ->
            ChannelSubscription(
                channelId = entry.channelId,
                channelName = entry.channelName,
                channelThumbnail = "",
                subscribedAt = subscribedAt - index,
            )
        }

internal suspend fun enrichOpmlSubscriptionAvatars(
    subscriptions: List<ChannelSubscription>,
    avatarFetcher: suspend (String) -> String,
    onProgress: ((current: Int, total: Int) -> Unit)? = null,
): List<ChannelSubscription> {
    if (subscriptions.isEmpty()) {
        onProgress?.invoke(0, 0)
        return emptyList()
    }

    val semaphore = Semaphore(5)
    val completed = AtomicInteger(0)
    onProgress?.invoke(0, subscriptions.size)

    return supervisorScope {
        subscriptions
            .map { subscription ->
                async(Dispatchers.IO) {
                    val enriched =
                        semaphore.withPermit {
                            val avatar = runCatching { avatarFetcher(subscription.channelId) }.getOrDefault("")
                            if (avatar.isBlank()) subscription else subscription.copy(channelThumbnail = avatar)
                        }
                    onProgress?.invoke(completed.incrementAndGet(), subscriptions.size)
                    enriched
                }
            }.awaitAll()
    }
}