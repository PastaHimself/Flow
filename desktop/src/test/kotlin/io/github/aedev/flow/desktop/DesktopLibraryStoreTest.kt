package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopLibraryStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesAndLoadsLibrary() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        val video = video(id = "abc", title = "Kotlin Desktop Guide")

        store.save(listOf(video))

        val restored = store.load()
        assertEquals(1, restored.size)
        assertEquals(video.id, restored.single().id)
        assertEquals(video.title, restored.single().title)
        assertTrue(store.file.toFile().isFile)
    }

    @Test
    fun recommendationQueryUsesSharedFlowNeuroTextNormalization() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        val videos =
            listOf(
                video(id = "1", title = "𝙆𝙊𝙏𝙇𝙄𝙉 Desktop Tutorial"),
                video(id = "2", title = "Kotlin Coroutines Explained"),
            )

        assertEquals("kotlin", store.recommendationQuery(videos))
    }

    @Test
    fun recommendationQueryUsesMostRecentSavedVideos() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        val videos =
            (1..30).map { index -> video(id = "new-$index", title = "Kotlin", channelName = "x") } +
                (1..30).map { index -> video(id = "old-$index", title = "Retro", channelName = "x") }

        assertEquals("kotlin", store.recommendationQuery(videos))
    }

    @Test
    fun malformedExistingLibraryIsNotSilentlyOverwritten() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        store.file.parent
            .toFile()
            .mkdirs()
        store.file.toFile().writeText("not-json")

        assertTrue(store.load().isEmpty())
        assertNotNull(store.loadError)

        val failure = runCatching { store.save(listOf(video(id = "abc", title = "Kotlin"))) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("not-json", store.file.toFile().readText())
    }

    @Test
    fun malformedSecondaryStoreIsNotSilentlyOverwritten() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        store.historyFile.parent
            .toFile()
            .mkdirs()
        store.historyFile.toFile().writeText("not-json")

        assertTrue(store.loadHistory().isEmpty())
        assertNotNull(store.loadErrors[store.historyFile])

        val failure = runCatching { store.recordWatched(video(id = "abc", title = "Kotlin")) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("not-json", store.historyFile.toFile().readText())

        store.clearHistory()
        assertTrue(store.loadErrors.isEmpty())
        assertEquals(listOf("abc"), store.recordWatched(video(id = "abc", title = "Kotlin")).map { it.id })
    }

    @Test
    fun recordsHistoryNewestFirstWithoutDuplicates() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        val first = video(id = "first", title = "First")
        val second = video(id = "second", title = "Second")

        store.recordWatched(first)
        store.recordWatched(second)
        val updated = store.recordWatched(first)

        assertEquals(listOf("first", "second"), updated.map { it.id })
        assertEquals(listOf("first", "second"), store.loadHistory().map { it.id })
    }

    @Test
    fun togglesSubscriptionsAndPersistsPlaylists() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())
        val subscribedVideo = video(id = "video", title = "Video", channelName = "Channel").copy(channelId = "UCchannel")

        val subscribed = store.toggleSubscription(subscribedVideo)
        assertEquals(listOf("UCchannel"), subscribed.map { it.channelId })
        assertTrue(store.toggleSubscription(subscribedVideo).isEmpty())

        store.addToPlaylist("Desktop", subscribedVideo)
        val restored = DesktopLibraryStore(temporaryFolder.root.toPath()).loadPlaylists()
        assertEquals("Desktop", restored.single().name)
        assertEquals(
            "video",
            restored
                .single()
                .videos
                .single()
                .id,
        )
    }

    @Test
    fun recordsUniqueSearchHistory() {
        val store = DesktopLibraryStore(temporaryFolder.root.toPath())

        store.recordSearch("Kotlin")
        store.recordSearch("Compose")
        val history = store.recordSearch("kotlin")

        assertEquals(listOf("kotlin", "Compose"), history)
    }

    private fun video(
        id: String,
        title: String,
        channelName: String = "Flow Test",
    ) = Video(
        id = id,
        title = title,
        channelName = channelName,
        channelId = "channel",
        thumbnailUrl = "https://example.invalid/thumb.jpg",
        duration = 120,
        viewCount = 42,
        uploadDate = "today",
    )
}
