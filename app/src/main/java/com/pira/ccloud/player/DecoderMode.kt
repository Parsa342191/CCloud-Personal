package com.pira.ccloud.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import kotlinx.serialization.Serializable

/**
 * Decoder core selection, mirroring MX Player's well known "HW / HW+ / SW" switcher.
 *
 * - [HW]  Platform hardware decoders only (androidx.media3 MediaCodec renderers). Fastest,
 *         most power efficient, but limited to whatever codecs the device's chipset supports.
 * - [HW_PLUS] Platform hardware decoders first, with automatic fallback to the bundled FFmpeg
 *         software decoders (audio + video) when the hardware decoder can't handle a stream
 *         (unsupported codec/profile, or a decoder init/runtime failure). This was the app's
 *         previous fixed behavior (EXTENSION_RENDERER_MODE_PREFER + decoder fallback), now
 *         kept as the default and exposed as one of three choices instead of the only option.
 * - [SW]  FFmpeg software decoders are tried first, falling back to hardware only if the
 *         software decoder itself is unavailable. Slower / more battery hungry, but the most
 *         compatible option - closest to MX Player's "SW decoder" mode.
 */
@Serializable
enum class DecoderMode {
    HW,
    HW_PLUS,
    SW;

    companion object {
        fun fromStorageValue(value: String?): DecoderMode =
            entries.firstOrNull { it.name == value } ?: HW_PLUS
    }
}

/**
 * Builds a [RenderersFactory] configured for the given [DecoderMode].
 *
 * Requires the `org.jellyfin.media3:media3-ffmpeg-decoder` dependency (prebuilt FFmpeg
 * extension for Media3) to be on the classpath for [DecoderMode.HW_PLUS] and [DecoderMode.SW]
 * to actually provide software decoding; without it those two modes silently behave like
 * [DecoderMode.HW] because there is no extension renderer for ExoPlayer to fall back to /
 * prefer.
 */
fun buildRenderersFactory(context: Context, mode: DecoderMode): RenderersFactory {
    val factory = DefaultRenderersFactory(context.applicationContext)
    when (mode) {
        DecoderMode.HW -> {
            // Hardware only: never use the FFmpeg extension renderers.
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            factory.setEnableDecoderFallback(false)
        }
        DecoderMode.HW_PLUS -> {
            // Hardware first; fall back to the FFmpeg extension (or another hardware decoder)
            // automatically if the preferred decoder fails to initialize or decode.
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            factory.setEnableDecoderFallback(true)
        }
        DecoderMode.SW -> {
            // Prefer the FFmpeg software decoders over platform hardware decoders.
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            factory.setEnableDecoderFallback(true)
        }
    }
    return factory
}

/** Human readable labels for the settings UI (Persian). */
fun displayName(mode: DecoderMode): String = when (mode) {
    DecoderMode.HW -> "HW (سخت‌افزاری)"
    DecoderMode.HW_PLUS -> "HW+ (سخت‌افزاری با پشتیبانی نرم‌افزاری)"
    DecoderMode.SW -> "SW (نرم‌افزاری - FFmpeg)"
}
