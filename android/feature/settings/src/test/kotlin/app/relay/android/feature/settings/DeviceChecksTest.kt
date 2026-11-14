package app.relay.android.feature.settings

import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.port.SmsQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceChecksTest {
    private class FakeDevice(
        val battery: Boolean = true,
        val quota: SmsQuota? = null,
        val send: Boolean = true,
        val twoWay: Boolean = true,
        val receive: Boolean = true,
        val notifications: Boolean = true,
    ) : DeviceStatusProvider {
        override fun ignoresBatteryOptimisations() = battery
        override fun smsQuota() = quota
        override fun smsPermissionGranted() = send
        override fun receivesSms() = twoWay
        override fun receiveSmsPermissionGranted() = receive
        override fun notificationsEnabled() = notifications
        override fun defaultSmsAppLiftsLimit() = false
        override fun isDefaultSmsApp() = false
    }

    private val settings = GatewaySettings(maxRatePerMinute = 30)

    @Test
    fun `a healthy phone lists every check and needs nothing`() {
        val checks = deviceChecks(FakeDevice(), settings)
        assertEquals(listOf("Battery optimisation", "Android SMS limit", "Send SMS", "Receive SMS", "Notifications"), checks.map { it.label })
        assertTrue(checks.needingAttention().isEmpty())
    }

    @Test
    fun `failing checks need attention in list order, each with its fix`() {
        val device = FakeDevice(battery = false, send = false, receive = false, notifications = false)
        assertEquals(
            listOf(CheckAction.BATTERY, CheckAction.SMS_PERMISSION, CheckAction.RECEIVE_SMS_PERMISSION, CheckAction.NOTIFICATIONS),
            deviceChecks(device, settings).needingAttention().map { it.action },
        )
    }

    @Test
    fun `an SMS limit under the send rate is amber but only slows sending`() {
        // 100 parts in 30 minutes: about 3 a minute, under the 30 a minute the phone is set to.
        val checks = deviceChecks(FakeDevice(quota = SmsQuota(maxParts = 100, windowMs = 30 * 60_000L)), settings)
        val limit = checks.single { it.action == CheckAction.SMS_LIMIT }
        assertEquals("3 / min", limit.value)
        assertEquals(CheckTone.WARN, limit.tone)
        assertTrue(checks.needingAttention().isEmpty())
    }

    @Test
    fun `the send-only build has no receive check to fail`() {
        val checks = deviceChecks(FakeDevice(twoWay = false, receive = false), settings)
        assertNull(checks.firstOrNull { it.label == "Receive SMS" })
        assertTrue(checks.needingAttention().isEmpty())
    }
}
