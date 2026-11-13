package app.relay.android

import io.sentry.Breadcrumb
import io.sentry.android.core.SentryAndroidOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingTest {
    @Test
    fun `sends no personal data, screenshots or traces`() {
        val options = SentryAndroidOptions()
        CrashReporting.configure(options, "https://key@o1.ingest.de.sentry.io/2", "1.2.0", 412)
        assertEquals("smswire-android@1.2.0+412", options.release)
        assertEquals("production", options.environment)
        assertFalse(options.isSendDefaultPii)
        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
        assertNull(options.tracesSampleRate)
        assertFalse(options.isEnableUserInteractionBreadcrumbs)
        assertTrue(options.isAnrEnabled)
    }

    @Test
    fun `keeps lifecycle breadcrumbs without their data and drops the rest`() {
        val lifecycle = Breadcrumb().apply { category = "ui.lifecycle"; message = "MainActivity resumed"; setData("screen", "MainActivity") }
        assertEquals("MainActivity resumed", CrashReporting.keep(lifecycle)?.message)
        assertTrue(lifecycle.data.isEmpty())

        for (category in listOf("http", "navigation", "console", "ui.click", null)) {
            val crumb = Breadcrumb().apply { this.category = category; message = "+14155550123: your code is 482913" }
            assertNull("$category should be dropped", CrashReporting.keep(crumb))
        }
    }
}
