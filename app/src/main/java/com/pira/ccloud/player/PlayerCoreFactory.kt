package com.pira.ccloud.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.pira.ccloud.utils.DeviceUtils
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the ExoPlayer (Media3) instance that powers video playback.
 *
 * This is the "core" of the player: the network/data-source layer, the
 * renderer configuration, the buffering strategy and the track-selection
 * defaults. None of this touches PlayerView, the Compose controls, colors
 * or layout - the on-screen player theme stays exactly as it was.
 *
 * The settings below are tuned separately for Android TV and for
 * phones/tablets (via [DeviceUtils.isTv]) since the two have different
 * needs: TVs benefit from a bigger buffer and tunneled video for smoother,
 * stutter-free playback on a big screen, while phones favor a smaller
 * buffer for a faster start and lower memory use.
 */
object PlayerCoreFactory {

    // Shared OkHttp client for all playback requests. Reusing one client
    // means connections/DNS/TLS sessions are pooled across videos instead
    // of being re-negotiated every time a new player is created, and gives
    // us generous timeouts + automatic retry for the kind of flaky
    // redirects/hiccups common with IPTV streams.
    private val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Creates a fully configured [ExoPlayer]. Caller is still responsible
     * for setMediaItem/prepare/playWhenReady etc., exactly as before - only
     * how the player itself is built has changed.
     *
     * @param decoderMode HW / HW+ / SW decoder core selection - see [DecoderMode].
     */
    @OptIn(markerClass = [UnstableApi::class])
    fun create(
        context: Context,
        trackSelector: DefaultTrackSelector,
        decoderMode: DecoderMode = DecoderMode.HW_PLUS
    ): ExoPlayer {
        val appContext = context.applicationContext
        val isTv = DeviceUtils.isTv(appContext)

        // Data source: OkHttp-backed instead of the platform HttpURLConnection
        // default, for better connection reuse and redirect handling.
        val httpDataSourceFactory = OkHttpDataSource.Factory(sharedHttpClient)
            .setUserAgent("CCloud/${if (isTv) "AndroidTV" else "Android"}")

        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(dataSourceFactory)
            // Strips leftover ASS/SSA override-tag text (positioning, fades,
            // karaoke timings, etc.) that Media3's own SSA parser doesn't fully
            // consume, so those raw tag codes/numbers stop leaking into what's
            // displayed on screen next to the actual subtitle line. See
            // CleanedSubtitleParserFactory's own doc comment for details/limits.
            .setSubtitleParserFactory(CleanedSubtitleParserFactory(DefaultSubtitleParserFactory()))

        // Decoder core: HW / HW+ / SW, mirroring MX Player's well known decoder
        // switcher. See DecoderMode.kt for what each mode does.
        val renderersFactory = buildRenderersFactory(appContext, decoderMode)

        // Buffering profile: TVs get a larger buffer window since they're
        // usually on a fixed network connection and benefit most from
        // smoothing out throughput dips; phones stay leaner for a quicker
        // start and to respect limited memory/mobile data.
        val loadControl = if (isTv) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
                .build()
        }

        // Track-selection defaults: enable tunneled playback on TV (smoother,
        // less frame drop on the big screen), and let the selector pick a
        // slightly-out-of-spec track rather than refuse to play at all when a
        // device reports it can't quite handle a given stream.
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTunnelingEnabled(isTv)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
        )

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
    }
}
