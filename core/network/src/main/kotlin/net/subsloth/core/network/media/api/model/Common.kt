package net.subsloth.core.network.media.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaginationMeta(
    val page: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    val total: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
)

@Serializable
data class VideoQuality(
    val label: String? = null,
    val resolution: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
)

@Serializable
data class SubtitleTrack(
    val lang: String? = null,
    val language: String? = null,
    val code: String? = null,
    val label: String? = null,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val format: String? = null,
)

@Serializable
data class VideoSource(
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val resolution: String? = null,
    val quality: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val type: String? = null,
)
