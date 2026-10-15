package com.awbuilds.auraspend.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.TestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi", application = TestApplication::class)
class AppLockManagerTest {

    private fun manager(): AppLockManager =
        AppLockManager(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `the lock is off until a pin is set and enabled`() {
        val m = manager()
        assertFalse(m.hasPin)
        assertFalse(m.shouldLock())
    }

    @Test
    fun `a cold start locks once a pin is set and enabled`() {
        val m = manager()
        m.setPin("1234")
        m.isEnabled = true
        assertTrue(m.shouldLock())
    }

    @Test
    fun `returning within the grace period does not re-lock`() {
        val m = manager()
        m.setPin("1234")
        m.isEnabled = true
        val now = 1_800_000_000_000L
        m.lastBackgroundedAt = now - 30_000L
        assertFalse(m.shouldLock(now))
    }

    @Test
    fun `returning beyond the grace period locks again`() {
        val m = manager()
        m.setPin("1234")
        m.isEnabled = true
        val now = 1_800_000_000_000L
        m.lastBackgroundedAt = now - AppLockManager.BACKGROUND_GRACE_MS - 1L
        assertTrue(m.shouldLock(now))
    }

    @Test
    fun `set, verify and clear cover the lifecycle`() {
        val m = manager()
        m.setPin("1234")
        assertTrue(m.verifyPin("1234"))
        assertFalse(m.verifyPin("9999"))
        m.clearPin()
        assertFalse(m.hasPin)
        assertFalse(m.isEnabled)
        assertFalse(m.shouldLock())
    }
}
