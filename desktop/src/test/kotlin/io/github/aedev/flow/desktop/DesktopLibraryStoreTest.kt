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
