package io.github.aedev.flow.desktop

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopInstanceLockTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun onlyOneInstanceCanHoldTheDataLock() {
        val path = temporaryFolder.root.toPath().resolve("flow.lock")
        val first = DesktopInstanceLock.tryAcquire(path)
        assertNotNull(first)

        assertNull(DesktopInstanceLock.tryAcquire(path))

        first!!.close()
        DesktopInstanceLock.tryAcquire(path).use { third -> assertNotNull(third) }
    }
}
