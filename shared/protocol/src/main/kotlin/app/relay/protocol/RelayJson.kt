package app.relay.protocol

import kotlinx.serialization.json.Json

/** Single JSON configuration used on both ends of every Relay contract. */
val RelayJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}

object RelayPaths {
    const val DEVICE_PAIR = "/v1/devices/pair"
    const val DEVICE_PROJECTS = "/v1/device/projects"
    /** DELETE: the phone unpairs itself (revokes its own token). */
    const val DEVICE_SELF = "/v1/device"
    const val DEVICE_SOCKET = "/v1/device/connect"

    fun projectMessages(slug: String) = "/v1/p/$slug/messages"
    fun projectOtp(slug: String) = "/v1/p/$slug/otp"
    fun projectSchedules(slug: String) = "/v1/p/$slug/schedules"
}
