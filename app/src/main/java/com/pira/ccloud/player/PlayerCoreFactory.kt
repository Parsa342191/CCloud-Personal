package com.pira.ccloud.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.pira.ccloud.utils.DeviceUtils
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
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

        // libass engine for .ass/.ssa subtitles: real positioning, colors/fonts
        // and karaoke taken from the subtitle file itself (rasterized by libass),
        // instead of Media3's own simplified SSA parser. CUES mode feeds that
        // rendering through the player's existing SubtitleView - the same view
        // plain .srt/.vtt subtitles already use - so no extra view wiring is
        // needed and the player's theme/layout is untouched either way. The
        // trade-off vs. the library's OVERLAY modes: continuous ASS animation
        // (movement, fades) plays back as timed segments rather than a smooth
        // per-frame overlay - style, position and color are unaffected.
        //
        // buildWithAssSupport builds its own MediaSourceFactory/subtitle parser
        // internally, so our OkHttp data source and HW/HW+/SW renderersFactory
        // are passed in by name here (instead of via ExoPlayer.Builder/
        // .setMediaSourceFactory as before) so it wraps *our* setup instead of
        // silently replacing it with its own defaults.
        return ExoPlayer.Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .buildWithAssSupport(
                context = appContext,
                renderType = AssRenderType.CUES,
                dataSourceFactory = dataSourceFactory,
                renderersFactory = renderersFactory
            )
    }
}
