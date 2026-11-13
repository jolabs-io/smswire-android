package app.relay.protocol

import kotlinx.serialization.Serializable

/*
 * Contract between the Android app and the API (REST part). The phone is a gateway:
 * projects, keys and batches are managed on the web; the phone pairs, then reads.
 * Every route except pairing needs `Authorization: Bearer <device token>`.
 */

/** Redeems a code shown on the web dashboard (or its QR) to join that workspace. */
@Serializable
data class PairDeviceRequest(
    val code: String,
    val name: String,
    val model: String,
    /** The phone's stable id: pairing the same phone again takes over its old record. */
    val phoneKey: String? = null,
)

@Serializable
data class WorkspaceRef(val id: String, val name: String)

@Serializable
data class PairDeviceResponse(val deviceId: String, val token: String, val workspace: WorkspaceRef)

/** A project this phone sends for (read-only on the phone). */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val slug: String,
    val endpoint: String,
    val webhookUrl: String? = null,
    val enabled: Boolean,
    val createdAt: Long,
    /** SIM slot (1-based) the project sends from unless a request names one; null = device default. */
    val sim: Int? = null,
    val defaultType: MessageType = MessageType.TRANSACTIONAL,
)

@Serializable
data class BatchDto(
    val id: String,
    val projectId: String,
    val name: String,
    val source: BatchSource,
    val total: Int,
    val rate: Int,
    val status: BatchStatus,
    val createdAt: Long,
)
