package com.pira.ccloud.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoPlayerSettings(
    val seekTimeSeconds: Int = 10,
    // Stored as the enum name ("HW" / "HW_PLUS" / "SW") to keep this model decoupled
    // from the player package. Use DecoderMode.fromStorageValue(...) to parse it back.
    val decoderMode: String = "HW_PLUS"
) {
    companion object {
        val DEFAULT = VideoPlayerSettings()
    }
}