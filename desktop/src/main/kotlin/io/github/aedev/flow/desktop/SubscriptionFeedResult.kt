package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class SubscriptionFeedResult(
    val videos: List<Video>,
    val failedChannelNames: List<String>,
)

internal suspend fun loadSubscriptionFeed(
    subscriptions: List<DesktopSubscription>,
    fetch: suspend (DesktopSubscription) -> List<Video>,
): SubscriptionFeedResult =
    coroutineScope {
        val outcomes = mutableListOf<Pair<DesktopSubscription, Result<List<Video>>>>()
        subscriptions
            .chunked(MAX_CONCURRENT_CHANNEL_FETCHES)
            .forEach { batch ->
                outcomes +=
                    batch
                        .map { subscription ->
                            async {
                                subscription to
                                    try {
                                        Result.success(fetch(subscription))
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (failure: Throwable) {
                                        Result.failure(failure)
                                    }
                            }
                        }.awaitAll()
            }
        if (outcomes.isNotEmpty() && outcomes.all { (_, outcome) -> outcome.isFailure }) {
            throw outcomes.firstNotNullOf { (_, outcome) -> outcome.exceptionOrNull() }
        }
        SubscriptionFeedResult(
            videos =
                outcomes
                    .mapNotNull { (_, outcome) -> outcome.getOrNull() }
                    .flatten()
                    .distinctBy(Video::id)
                    .sortedByDescending(Video::timestamp),
            failedChannelNames = outcomes.mapNotNull { (subscription, outcome) -> subscription.channelName.takeIf { outcome.isFailure } },
        )
    }

private const val MAX_CONCURRENT_CHANNEL_FETCHES = 3
