package com.pira.ccloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import kotlin.math.abs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.pira.ccloud.data.model.SubtitleSettings
import com.pira.ccloud.data.model.VideoPlayerSettings
import com.pira.ccloud.data.model.FontSettings
import com.pira.ccloud.data.model.WatchedEpisode
import com.pira.ccloud.utils.StorageUtils
import com.pira.ccloud.ui.theme.FontManager
import com.pira.ccloud.ui.theme.tvFocusIndication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Minimum saved position (ms) before we bother offering to resume playback from it
private const val MIN_RESUMABLE_POSITION_MS = 5000L

// How long the "resume or start over" prompt stays on screen before defaulting to start-over
private const val RESUME_PROMPT_TIMEOUT_SECONDS = 5

// Extension function to set subtitle text size on PlayerView
fun PlayerView.setSubtitleTextSize(spSize: Float) {
    // Convert sp to pixels
    val displayMetrics = context.resources.displayMetrics
    val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spSize, displayMetrics)
    
    // Set the subtitle text size
    subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pixels)
}

// Extension function to set subtitle colors and font
fun PlayerView.setSubtitleColors(settings: SubtitleSettings, typeface: Typeface? = null) {
    // Create a custom CaptionStyleCompat with the typeface
    // Use transparent background as default
    val style = CaptionStyleCompat(
        settings.textColor,
        android.graphics.Color.TRANSPARENT, // Always use transparent background
        settings.borderColor,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        settings.borderColor,
        typeface
    )
    subtitleView?.setStyle(style)
    
    // Note: ExoPlayer's subtitle rendering has limited support for custom fonts.
    // The font may not be applied to all subtitle formats or on all Android versions.
    // This is a known limitation of ExoPlayer's subtitle rendering system.
}

class VideoPlayerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SEASON_ID = "season_id"
        const val EXTRA_EPISODE_ID = "episode_id"
        const val REQUEST_WRITE_SETTINGS = 1001
        
        fun start(context: Context, videoUrl: String) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
            context.startActivity(intent)
        }
        
        fun startWithEpisodeInfo(context: Context, videoUrl: String, seriesId: Int, seasonId: Int, episodeId: Int) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_SERIES_ID, seriesId)
                putExtra(EXTRA_SEASON_ID, seasonId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
            context.startActivity(intent)
        }
    }
    
    private var exoPlayer: ExoPlayer? = null
    private var videoUrl: String? = null
    private var seriesId: Int? = null
    private var seasonId: Int? = null
    private var episodeId: Int? = null
    private var playerInitialized = false
    private var isActivityResumed = false
    private var hasMarkedAsWatched = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set fullscreen landscape mode
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Enable immersive full-screen mode
        enableFullScreenMode()
        
        // Keep screen on while in video player
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        seriesId = intent.getIntExtra(EXTRA_SERIES_ID, -1).takeIf { it != -1 }
        seasonId = intent.getIntExtra(EXTRA_SEASON_ID, -1).takeIf { it != -1 }
        episodeId = intent.getIntExtra(EXTRA_EPISODE_ID, -1).takeIf { it != -1 }
        
        if (videoUrl != null) {
            setContent {
                VideoPlayerScreen(
                    videoUrl = videoUrl!!, 
                    seriesId = seriesId,
                    seasonId = seasonId,
                    episodeId = episodeId,
                    onBack = this::finish
                ) { player ->
                    exoPlayer = player
                    playerInitialized = true
                }
            }
        } else {
            finish()
        }
    }
    
    // Handle TV remote control key events
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        try {
            exoPlayer?.let { player ->
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                        player.playWhenReady = !player.playWhenReady
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.playWhenReady = true
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.playWhenReady = false
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val newPosition = (player.currentPosition - 10000).coerceAtLeast(0L) // Rewind 10 seconds
                        player.seekTo(newPosition)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration) // Forward 10 seconds
                        player.seekTo(newPosition)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        finish()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore key event errors
        }
        return super.onKeyDown(keyCode, event)
    }
    
    private fun enableFullScreenMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11 and above
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // For Android 4.4 to Android 10
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            } else {
                // For even older versions
                @Suppress("DEPRECATION")
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        } catch (e: Exception) {
            // Fallback to basic fullscreen if there's an issue
            try {
                @Suppress("DEPRECATION")
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } catch (e2: Exception) {
                // Ignore fullscreen errors
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            exoPlayer?.release()
        } catch (e: Exception) {
            // Ignore any exceptions during release
        }
        exoPlayer = null
        playerInitialized = false
        
        // Remove keep screen on flag to conserve battery
        try {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            // Ignore flag clear errors
        }
    }
    
    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        // Re-enable full-screen mode when resuming
        try {
            enableFullScreenMode()
        } catch (e: Exception) {
            // Ignore fullscreen errors
        }
        
        // Player will remain paused until user manually starts it
        try {
            if (playerInitialized && exoPlayer != null) {
                // Keep player paused - let user manually start playback
                exoPlayer?.playWhenReady = false
            }
        } catch (e: Exception) {
            // Ignore player state errors
        }
    }
    
    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        
        // Stop player completely when activity pauses (app switch or screen off)
        try {
            if (playerInitialized && exoPlayer != null) {
                exoPlayer?.playWhenReady = false
                // Note: currentPosition is managed in the Composable scope
                // Player will remain paused until user manually starts it
            }
        } catch (e: Exception) {
            // Ignore player stop errors
        }
    }
}

// Which on-screen player control is logically focused while paused - see the
// self-managed D-pad navigation in VideoPlayerScreen's onPreviewKeyEvent handler.
private enum class PlayerFocusTarget { BACK, SETTINGS, PLAY_PAUSE, SLIDER }

@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    seriesId: Int?,
    seasonId: Int?,
    episodeId: Int?,
    onBack: () -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit
) {
    val context = LocalContext.current

    // Resume playback support: look up any saved position for this exact content
    // (episode identified by series/season/episode ids, movie identified by its URL)
    val progressKey = remember(videoUrl, seriesId, seasonId, episodeId) {
        StorageUtils.buildPlaybackProgressKey(videoUrl, seriesId, seasonId, episodeId)
    }
    val resumePositionMs = remember(progressKey) {
        try {
            val saved = StorageUtils.getPlaybackProgress(context, progressKey)
            if (saved != null &&
                saved.durationMs > 0 &&
                saved.positionMs > MIN_RESUMABLE_POSITION_MS &&
                saved.positionMs < (saved.durationMs * 0.95).toLong()
            ) {
                saved.positionMs
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    // Show the resume/start-over prompt only when there is something to resume from
    var showResumePrompt by remember { mutableStateOf(resumePositionMs > 0L) }
    var resumePromptSecondsLeft by remember { mutableStateOf(RESUME_PROMPT_TIMEOUT_SECONDS) }

    var isPlaying by remember { mutableStateOf(resumePositionMs <= 0L) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isRetrying by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }
    var showRewindIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    // Device volume control (D-pad Up/Down while playing - see onPreviewKeyEvent below)
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedDropdown by remember { mutableStateOf(false) }
    var playerInitialized by remember { mutableStateOf(false) }
    var hasMarkedAsWatched by remember { mutableStateOf(false) }
    
    // Track selection state
    var showTrackSelectionDialog by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var trackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }

    // Focus target that owns TV remote / D-pad key events. onPreviewKeyEvent below
    // only fires for a node that is focused (or is an ancestor of the focused node) -
    // without an explicit focusable() + requestFocus() here, nothing in this screen
    // is ever focused, so the key handling below never actually runs and the remote
    // does nothing. This requester keeps focus pinned on the root player Box whenever
    // no dialog/dropdown is open, so play/pause/seek always work.
    val rootFocusRequester = remember { FocusRequester() }

    // Whether the root player Box itself currently owns focus (as opposed to a
    // descendant, e.g. one of the on-screen control buttons). Used to let the
    // global play/pause/seek shortcuts below yield to a focused button's own
    // click handling once the user has navigated onto it with the D-pad -
    // otherwise OK/Center always toggles play/pause instead of activating
    // whichever button is actually highlighted.
    var isRootFocused by remember { mutableStateOf(true) }

    // Focus target for the middle play/pause button, so a D-pad press that reveals
    // the controls (see onPreviewKeyEvent below) can hand focus straight to it,
    // the way VLC's Android TV player puts focus on play/pause as soon as the
    // overlay comes up via the remote.
    val playPauseFocusRequester = remember { FocusRequester() }
    var focusControlsOnShow by remember { mutableStateOf(false) }

    // Explicit, self-managed D-pad navigation between the on-screen controls while
    // paused. Compose's built-in focus system does not reliably auto-navigate (or
    // trigger a focused button's click) purely from arrow-key/Center presses in this
    // app's setup, so instead of depending on that, we track which control is
    // logically focused ourselves and move/activate it directly from the key
    // handler below - this behaves the same on every device regardless of Compose's
    // default key-to-focus behavior.
    val backFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val sliderFocusRequester = remember { FocusRequester() }
    var focusedControl by remember { mutableStateOf(PlayerFocusTarget.PLAY_PAUSE) }
    
    // Predefined playback speed options
    val speedOptions = remember {
        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 3.5f)
    }
    
    // Load font settings
    val fontSettings = remember(context) {
        try {
            StorageUtils.loadFontSettings(context)
        } catch (e: Exception) {
            com.pira.ccloud.data.model.FontSettings.DEFAULT
        }
    }
    
    // Load custom font typeface
    val customTypeface = remember(fontSettings.fontType) {
        try {
            when (fontSettings.fontType) {
                com.pira.ccloud.data.model.FontType.DEFAULT -> null
                com.pira.ccloud.data.model.FontType.VAZIRMATN -> {
                    try {
                        // Load the Vazirmatn font from assets
                        Typeface.createFromAsset(context.assets, "font/vazirmatn_regular.ttf")
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Load video player settings (without affecting playback speed)
    val videoPlayerSettings = remember(context) {
        try {
            StorageUtils.loadVideoPlayerSettings(context)
        } catch (e: Exception) {
            com.pira.ccloud.data.model.VideoPlayerSettings.DEFAULT
        }
    }
    
    // Load subtitle settings
    val subtitleSettings = remember(context) {
        try {
            StorageUtils.loadSubtitleSettings(context)
        } catch (e: Exception) {
            SubtitleSettings.getDefaultSettings(context)
        }
    }
    
    val exoPlayer = remember(context) {
        try {
            // Create track selector for track selection
            val selector = DefaultTrackSelector(context)
            trackSelector = selector
            
            ExoPlayer.Builder(context)
                .setTrackSelector(selector)
                .build().apply {
                    try {
                        setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                        prepare()
                        // If we're retrying, seek to the current position
                        if (isRetrying && currentPosition > 0) {
                            seekTo(currentPosition)
                        }
                        playWhenReady = isPlaying // Start with current play state
                        // Set initial playback speed
                        setPlaybackSpeed(playbackSpeed)
                    } catch (e: Exception) {
                        // Don't show error, just mark as retrying
                        isRetrying = true
                    }
                }
        } catch (e: Exception) {
            // Don't show error, just mark as retrying
            isRetrying = true
            null
        }
    }
    
    // Listen to track changes
    val trackListener = remember(exoPlayer) {
        object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
    }
    
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        
        try {
            exoPlayer.addListener(trackListener)
        } catch (e: Exception) {
            // Ignore listener errors
        }
    }
    
    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.removeListener(trackListener)
            } catch (e: Exception) {
                // Ignore listener removal errors
            }
        }
    }
    
    // Notify activity of player reference
    LaunchedEffect(Unit) {
        try {
            exoPlayer?.let { onPlayerReady(it) }
            playerInitialized = true
        } catch (e: Exception) {
            // Ignore callback errors
        }
    }
    
    // Update player state and mark episode as watched
    LaunchedEffect(isPlaying, exoPlayer) {
        try {
            exoPlayer?.playWhenReady = isPlaying
            
            // Mark episode as watched when playback starts (only once)
            if (isPlaying && !hasMarkedAsWatched && seriesId != null && seasonId != null && episodeId != null) {
                try {
                    val watchedEpisode = WatchedEpisode(
                        seriesId = seriesId!!,
                        seasonId = seasonId!!,
                        episodeId = episodeId!!
                    )
                    StorageUtils.saveWatchedEpisode(context, watchedEpisode)
                    hasMarkedAsWatched = true
                } catch (e: Exception) {
                    // Ignore storage errors
                }
            }
        } catch (e: Exception) {
            // Ignore player state errors
        }
    }
    
    // Update playback speed when it changes
    LaunchedEffect(playbackSpeed, exoPlayer) {
        try {
            exoPlayer?.setPlaybackSpeed(playbackSpeed)
        } catch (e: Exception) {
            // Ignore playback speed errors
        }
    }
    
    // Listen to player events and handle cleanup
    val playerListener = remember(exoPlayer) {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                // Only update isPlaying if we're not currently seeking
                if (!isSeeking) {
                    isPlaying = playing
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                try {
                    if (playbackState == Player.STATE_READY) {
                        duration = exoPlayer?.duration ?: 0L
                        
                        // After the player is ready (especially after a retry), 
                        // ensure the playWhenReady state is consistent with our UI state
                        if (exoPlayer != null && !isRetrying) {
                            exoPlayer?.playWhenReady = isPlaying
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        // Video ended, pause the player
                        isPlaying = false
                    }
                } catch (e: Exception) {
                    // Ignore duration errors
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                try {
                    if (!isSeeking) {
                        currentPosition = exoPlayer?.currentPosition ?: 0L
                    }
                } catch (e: Exception) {
                    // Ignore position errors
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Don't show error message, just mark as retrying
                isRetrying = true
                playerError = error.message
                
                // Store current position before retrying
                val retryPosition = currentPosition
                val wasPlaying = isPlaying // Store whether it was playing before the error
                
                // Attempt to retry after a delay
                CoroutineScope(Dispatchers.Main).launch {
                    delay(3000) // Wait 3 seconds before retrying
                    try {
                        exoPlayer?.let { player ->
                            // Retry loading the media
                            player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                            player.prepare()
                            // Seek to the stored position after preparing
                            player.seekTo(retryPosition)
                            
                            // Resume playback if it was playing before the error
                            player.playWhenReady = wasPlaying
                            
                            // Update the UI state to match the player state
                            isPlaying = wasPlaying
                            isRetrying = false
                            playerError = null
                        }
                    } catch (e: Exception) {
                        // If retry fails, keep isRetrying true
                    }
                }
            }
        }
    }
    
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        
        try {
            exoPlayer.addListener(playerListener)
        } catch (e: Exception) {
            // Ignore listener errors
        }
    }
    
    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.removeListener(playerListener)
            } catch (e: Exception) {
                // Ignore listener removal errors
            }
        }
    }
    
    // Periodically update the current position for real-time progress tracking
    LaunchedEffect(exoPlayer, isPlaying) {
        if (exoPlayer == null) return@LaunchedEffect
        
        try {
            while (true) {
                delay(100) // Update every 100ms for smooth progress tracking
                if (isPlaying && !isSeeking) {
                    try {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) {
                                currentPosition = player.currentPosition
                                duration = player.duration
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore position/duration errors
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore coroutine errors
        }
    }
    
    // Hide controls after a delay
    LaunchedEffect(showControls, isPlaying) {
        try {
            if (showControls && isPlaying) {
                delay(3000) // Hide controls after 3 seconds
                showControls = false
                // Controls (and whatever button had focus) are about to be removed
                // from composition - reclaim focus on the root player Box so the
                // remote keeps working instead of focus being left dangling.
                try {
                    rootFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore - view may not be laid out yet
                }
            }
        } catch (e: Exception) {
            // Ignore delay errors
        }
    }
    
    // Hide forward/rewind indicators after a delay
    LaunchedEffect(showForwardIndicator) {
        try {
            if (showForwardIndicator) {
                delay(500) // Hide after 500ms
                showForwardIndicator = false
            }
        } catch (e: Exception) {
            // Ignore delay errors
        }
    }
    
    LaunchedEffect(showRewindIndicator) {
        try {
            if (showRewindIndicator) {
                delay(500) // Hide after 500ms
                showRewindIndicator = false
            }
        } catch (e: Exception) {
            // Ignore delay errors
        }
    }

    // Hide the volume indicator a little longer than the seek ones, since the
    // user may press Up/Down several times in a row to reach the level they want.
    LaunchedEffect(showVolumeIndicator, currentVolume) {
        try {
            if (showVolumeIndicator) {
                delay(1200)
                showVolumeIndicator = false
            }
        } catch (e: Exception) {
            // Ignore delay errors
        }
    }
    
    // Resume prompt countdown: if the user doesn't choose "continue" in time,
    // default to playing from the beginning.
    LaunchedEffect(showResumePrompt) {
        if (!showResumePrompt) return@LaunchedEffect
        try {
            resumePromptSecondsLeft = RESUME_PROMPT_TIMEOUT_SECONDS
            while (resumePromptSecondsLeft > 0) {
                delay(1000)
                resumePromptSecondsLeft -= 1
            }
            if (showResumePrompt) {
                // Timed out without a choice - start from the beginning
                showResumePrompt = false
                isPlaying = true
            }
        } catch (e: Exception) {
            // Ignore countdown errors
        }
    }
    
    // Claim D-pad/remote focus for the root player Box on first composition, and
    // reclaim it whenever a dialog or dropdown that was using focus closes. Without
    // this, the onPreviewKeyEvent handler on the root Box below never receives any
    // key events at all (see comment on rootFocusRequester above).
    LaunchedEffect(showResumePrompt, showTrackSelectionDialog, showSpeedDropdown) {
        if (!showResumePrompt && !showTrackSelectionDialog && !showSpeedDropdown) {
            try {
                rootFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore - view may not be laid out yet
            }
        }
    }

    // When a D-pad press revealed the controls (see onPreviewKeyEvent above),
    // hand focus to the play/pause button once it actually exists in the
    // composition - the IconButton only gets composed after showControls flips
    // to true, so this can't happen synchronously in the key handler itself.
    LaunchedEffect(showControls, focusControlsOnShow) {
        if (showControls && focusControlsOnShow) {
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore - view may not be laid out yet
            }
            focusControlsOnShow = false
        }
    }

    // Periodically persist playback progress so it can be resumed later
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        try {
            while (true) {
                delay(5000) // Save progress every 5 seconds
                try {
                    exoPlayer?.let { player ->
                        val pos = player.currentPosition
                        val dur = player.duration
                        if (dur > 0 && pos > 0) {
                            if (pos >= (dur * 0.95).toLong()) {
                                // Practically finished watching - no need to offer resume next time
                                StorageUtils.removePlaybackProgress(context, progressKey)
                            } else {
                                StorageUtils.savePlaybackProgress(
                                    context,
                                    com.pira.ccloud.data.model.PlaybackProgress(
                                        key = progressKey,
                                        positionMs = pos,
                                        durationMs = dur
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore progress save errors
                }
            }
        } catch (e: Exception) {
            // Ignore coroutine errors
        }
    }
    
    // Clean up player, saving the final playback position first
    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0 && pos > 0) {
                        if (pos >= (dur * 0.95).toLong()) {
                            StorageUtils.removePlaybackProgress(context, progressKey)
                        } else {
                            StorageUtils.savePlaybackProgress(
                                context,
                                com.pira.ccloud.data.model.PlaybackProgress(
                                    key = progressKey,
                                    positionMs = pos,
                                    durationMs = dur
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore progress save errors
            }
            try {
                exoPlayer?.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Make this Box an actual focus target and pin D-pad/remote focus on it
            // (see rootFocusRequester above). A node must be focused for the
            // onPreviewKeyEvent below to ever be called - without focusRequester()
            // + focusable() here, none of this key handling runs.
            .focusRequester(rootFocusRequester)
            .onFocusChanged { isRootFocused = it.isFocused }
            .focusable()
            // TV remote / D-pad behavior has two distinct modes, matching how a
            // physical remote works: while the video is actually PLAYING, the D-pad
            // is a transport control (Left/Right seeks, Up/Down adjusts the device
            // volume, Center pauses) and the on-screen options are not reachable at
            // all - this avoids the confusing "what's currently selected?" state.
            // Once paused/stopped, the D-pad instead navigates normally between the
            // on-screen buttons (Compose's focus system + each button's own Center
            // handling), and seeking/volume shortcuts are disabled so arrow keys only
            // move focus. Dedicated hardware transport keys (physical play/pause/
            // rewind/fast-forward, on remotes that have them) always work either way.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the resume/start-over prompt, the track selection dialog, or
                // the speed dropdown is up, let their own buttons receive D-pad
                // focus/clicks normally.
                if (showResumePrompt || showTrackSelectionDialog || showSpeedDropdown) return@onPreviewKeyEvent false
                val player = exoPlayer ?: return@onPreviewKeyEvent false

                // Dedicated hardware transport keys always work, regardless of
                // whether the video is playing/paused or what has focus.
                when (keyEvent.key) {
                    Key.MediaPlay -> { isPlaying = true; return@onPreviewKeyEvent true }
                    Key.MediaPause -> { isPlaying = false; return@onPreviewKeyEvent true }
                    Key.MediaRewind, Key.MediaSkipBackward -> {
                        try {
                            val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                            val newPosition = (player.currentPosition - seekTimeMs).coerceAtLeast(0L)
                            player.seekTo(newPosition)
                            currentPosition = newPosition
                            showRewindIndicator = true
                        } catch (e: Exception) { /* Ignore seek errors */ }
                        return@onPreviewKeyEvent true
                    }
                    Key.MediaFastForward, Key.MediaSkipForward -> {
                        try {
                            val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                            val newPosition = (player.currentPosition + seekTimeMs).coerceAtMost(player.duration)
                            player.seekTo(newPosition)
                            currentPosition = newPosition
                            showForwardIndicator = true
                        } catch (e: Exception) { /* Ignore seek errors */ }
                        return@onPreviewKeyEvent true
                    }
                    else -> {}
                }

                if (isPlaying) {
                    // PLAYING: D-pad is a transport control. On-screen options are
                    // intentionally not reachable here - Center just pauses, which
                    // is when option selection becomes available (see below).
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            isPlaying = false
                            showControls = true
                            focusControlsOnShow = true
                            true
                        }
                        Key.DirectionLeft -> {
                            try {
                                val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                val newPosition = (player.currentPosition - seekTimeMs).coerceAtLeast(0L)
                                player.seekTo(newPosition)
                                currentPosition = newPosition
                                showRewindIndicator = true
                            } catch (e: Exception) { /* Ignore seek errors */ }
                            true
                        }
                        Key.DirectionRight -> {
                            try {
                                val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                val newPosition = (player.currentPosition + seekTimeMs).coerceAtMost(player.duration)
                                player.seekTo(newPosition)
                                currentPosition = newPosition
                                showForwardIndicator = true
                            } catch (e: Exception) { /* Ignore seek errors */ }
                            true
                        }
                        Key.DirectionUp -> {
                            try {
                                currentVolume = (currentVolume + 1).coerceAtMost(maxVolume)
                                audioManager.setStreamVolume(
                                    android.media.AudioManager.STREAM_MUSIC,
                                    currentVolume,
                                    0
                                )
                                showVolumeIndicator = true
                            } catch (e: Exception) { /* Ignore volume errors */ }
                            true
                        }
                        Key.DirectionDown -> {
                            try {
                                currentVolume = (currentVolume - 1).coerceAtLeast(0)
                                audioManager.setStreamVolume(
                                    android.media.AudioManager.STREAM_MUSIC,
                                    currentVolume,
                                    0
                                )
                                showVolumeIndicator = true
                            } catch (e: Exception) { /* Ignore volume errors */ }
                            true
                        }
                        Key.Back -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else {
                    // PAUSED/STOPPED: options become reachable. Navigation between
                    // Back/Settings/Play-Pause/Slider and activating whichever one is
                    // focused is handled explicitly here (via focusedControl) rather
                    // than relying on Compose's own arrow-key focus search or a
                    // button's default Center-to-click handling - both were found to
                    // be unreliable in this app's setup.
                    if (isRootFocused) {
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                            Key.DirectionUp, Key.DirectionDown,
                            Key.DirectionLeft, Key.DirectionRight -> {
                                showControls = true
                                focusControlsOnShow = true
                                true
                            }
                            Key.Back -> {
                                onBack()
                                true
                            }
                            else -> false
                        }
                    } else {
                        fun moveTo(target: PlayerFocusTarget, requester: FocusRequester) {
                            focusedControl = target
                            try { requester.requestFocus() } catch (e: Exception) { /* Ignore */ }
                        }
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                when (focusedControl) {
                                    PlayerFocusTarget.PLAY_PAUSE -> moveTo(PlayerFocusTarget.BACK, backFocusRequester)
                                    PlayerFocusTarget.SLIDER -> moveTo(PlayerFocusTarget.PLAY_PAUSE, playPauseFocusRequester)
                                    else -> {}
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                when (focusedControl) {
                                    PlayerFocusTarget.BACK, PlayerFocusTarget.SETTINGS ->
                                        moveTo(PlayerFocusTarget.PLAY_PAUSE, playPauseFocusRequester)
                                    PlayerFocusTarget.PLAY_PAUSE -> moveTo(PlayerFocusTarget.SLIDER, sliderFocusRequester)
                                    else -> {}
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                when (focusedControl) {
                                    PlayerFocusTarget.SETTINGS -> moveTo(PlayerFocusTarget.BACK, backFocusRequester)
                                    PlayerFocusTarget.SLIDER -> {
                                        // Nudge the paused position back a little while the
                                        // seek bar itself is focused.
                                        try {
                                            val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                            val newPosition = (player.currentPosition - seekTimeMs).coerceAtLeast(0L)
                                            player.seekTo(newPosition)
                                            currentPosition = newPosition
                                            showRewindIndicator = true
                                        } catch (e: Exception) { /* Ignore seek errors */ }
                                    }
                                    else -> {}
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                when (focusedControl) {
                                    PlayerFocusTarget.BACK -> moveTo(PlayerFocusTarget.SETTINGS, settingsFocusRequester)
                                    PlayerFocusTarget.SLIDER -> {
                                        // Nudge the paused position forward a little while the
                                        // seek bar itself is focused.
                                        try {
                                            val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                            val newPosition = (player.currentPosition + seekTimeMs).coerceAtMost(player.duration)
                                            player.seekTo(newPosition)
                                            currentPosition = newPosition
                                            showForwardIndicator = true
                                        } catch (e: Exception) { /* Ignore seek errors */ }
                                    }
                                    else -> {}
                                }
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                when (focusedControl) {
                                    PlayerFocusTarget.BACK -> onBack()
                                    PlayerFocusTarget.SETTINGS -> showTrackSelectionDialog = true
                                    PlayerFocusTarget.PLAY_PAUSE -> isPlaying = true
                                    PlayerFocusTarget.SLIDER -> isPlaying = true
                                }
                                true
                            }
                            Key.Back -> {
                                // First Back press while browsing options just collapses
                                // them and returns focus to the player, instead of
                                // exiting immediately.
                                showControls = false
                                try { rootFocusRequester.requestFocus() } catch (e: Exception) { /* Ignore */ }
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                try {
                    detectTapGestures(
                        onDoubleTap = { offset -> 
                            // Ignore seek gestures while the resume/start-over prompt is up
                            if (showResumePrompt) return@detectTapGestures
                            
                            // Calculate if the tap is on the left or right side
                            val screenWidth = size.width
                            val tapX = offset.x
                            
                            // Store the playing state before seeking
                            wasPlayingBeforeSeek = isPlaying
                            isSeeking = true
                            
                            if (tapX < screenWidth / 2) {
                                // Left side - rewind specified seconds
                                try {
                                    exoPlayer?.let { player ->
                                        val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                        val newPosition = (player.currentPosition - seekTimeMs).coerceAtLeast(0L)
                                        player.seekTo(newPosition)
                                        currentPosition = newPosition
                                        showRewindIndicator = true
                                        // Keep the player playing during seeking if it was playing before
                                        if (wasPlayingBeforeSeek) {
                                            player.playWhenReady = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore seek errors
                                }
                            } else {
                                // Right side - forward specified seconds
                                try {
                                    exoPlayer?.let { player ->
                                        val seekTimeMs = videoPlayerSettings.seekTimeSeconds * 1000L
                                        val newPosition = (player.currentPosition + seekTimeMs).coerceAtMost(player.duration)
                                        player.seekTo(newPosition)
                                        currentPosition = newPosition
                                        showForwardIndicator = true
                                        // Keep the player playing during seeking if it was playing before
                                        if (wasPlayingBeforeSeek) {
                                            player.playWhenReady = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore seek errors
                                }
                            }
                            
                            // Reset seeking state after a short delay using a coroutine scope
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    delay(500) // Reset after 500ms
                                    isSeeking = false
                                    // Restore the playing state after seeking is finished
                                    try {
                                        exoPlayer?.playWhenReady = wasPlayingBeforeSeek
                                        // Update isPlaying state to match the player's actual state
                                        isPlaying = wasPlayingBeforeSeek
                                    } catch (e: Exception) {
                                        // Ignore errors
                                    }
                                } catch (e: Exception) {
                                    // Ignore delay errors
                                }
                            }
                        },
                        onTap = {
                            // Ignore control-toggle taps while the resume/start-over prompt is up
                            if (showResumePrompt) return@detectTapGestures
                            
                            showControls = !showControls
                            // Reset the auto-hide timer when controls are shown
                            if (showControls && isPlaying) {
                                // The LaunchedEffect above will handle the auto-hide
                            }
                        }
                    )
                } catch (e: Exception) {
                    // Ignore gesture detection errors
                }
            }
    ) {
        // Check if player is initialized
        if (exoPlayer == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Initializing player...",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Box
        }
        
        // Video player
        AndroidView(
            factory = { ctx ->
                try {
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // We're using our own controls
                        // Never let the raw video surface pick up Android (native) focus.
                        // On TV, the system auto-focuses the first focusable view when
                        // nothing else has focus yet; if this view wins that race instead
                        // of the Compose controls Box, D-pad key events go to a plain
                        // video surface that does nothing with them and the remote
                        // appears completely dead.
                        isFocusable = false
                        isFocusableInTouchMode = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Make the player view fill the entire screen
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        
                        // Apply subtitle settings to the player view
                        // Set subtitle styling with custom font
                        setSubtitleTextSize(subtitleSettings.textSize)
                        setSubtitleColors(subtitleSettings, customTypeface)
                    }
                } catch (e: Exception) {
                    // Return a simple view if PlayerView fails to initialize
                    View(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { playerView ->
                try {
                    // Update the player view when subtitle settings change
                    // Update subtitle styling when settings change
                    if (playerView is PlayerView) {
                        playerView.setSubtitleTextSize(subtitleSettings.textSize)
                        playerView.setSubtitleColors(subtitleSettings, customTypeface)
                    }
                } catch (e: Exception) {
                    // Ignore update errors
                }
            }
        )
        
        // Rewind indicator
        if (showRewindIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Rewind ${videoPlayerSettings.seekTimeSeconds} seconds",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "${videoPlayerSettings.seekTimeSeconds}s",
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        
        // Forward indicator
        if (showForwardIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward,
                        contentDescription = "Forward ${videoPlayerSettings.seekTimeSeconds} seconds",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "${videoPlayerSettings.seekTimeSeconds}s",
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Volume indicator (D-pad Up/Down while playing)
        if (showVolumeIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (currentVolume > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .width(120.dp)
                            .height(6.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }


        // Custom controls overlay (hidden while the resume prompt is up, so the two don't overlap)
        if (showControls && !showResumePrompt) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top bar with back button and settings
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val backInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteractionSource,
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(backFocusRequester)
                            .onFocusChanged { if (it.isFocused) focusedControl = PlayerFocusTarget.BACK }
                            .tvFocusIndication(backInteractionSource, shape = androidx.compose.foundation.shape.CircleShape)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    // Settings button in top right corner
                    val settingsInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { showTrackSelectionDialog = true },
                        interactionSource = settingsInteractionSource,
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(settingsFocusRequester)
                            .onFocusChanged { if (it.isFocused) focusedControl = PlayerFocusTarget.SETTINGS }
                            .tvFocusIndication(settingsInteractionSource, shape = androidx.compose.foundation.shape.CircleShape)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
                
                // Middle play/pause button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val playPauseInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        interactionSource = playPauseInteractionSource,
                        modifier = Modifier
                            .size(64.dp)
                            .focusRequester(playPauseFocusRequester)
                            .onFocusChanged { if (it.isFocused) focusedControl = PlayerFocusTarget.PLAY_PAUSE }
                            .tvFocusIndication(playPauseInteractionSource, shape = androidx.compose.foundation.shape.CircleShape)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    // Progress slider with retry animation
                    if (isRetrying) {
                        // Show animated progress bar when retrying
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    } else {
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { progress ->
                                // Store the playing state before seeking
                                if (!isSeeking) {
                                    wasPlayingBeforeSeek = isPlaying
                                }
                                isSeeking = true
                                val newPosition = (progress * duration).toLong()
                                try {
                                    exoPlayer?.seekTo(newPosition)
                                    currentPosition = newPosition
                                    // Keep the player playing during seeking if it was playing before
                                    if (wasPlayingBeforeSeek) {
                                        exoPlayer?.playWhenReady = true
                                    }
                                } catch (e: Exception) {
                                    // Ignore seek errors
                                }
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                // Restore the playing state after seeking is finished
                                try {
                                    exoPlayer?.playWhenReady = wasPlayingBeforeSeek
                                    // Update isPlaying state to match the player's actual state
                                    isPlaying = wasPlayingBeforeSeek
                                } catch (e: Exception) {
                                    // Ignore errors
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(sliderFocusRequester)
                                .onFocusChanged { if (it.isFocused) focusedControl = PlayerFocusTarget.SLIDER }
                        )
                    }
                    
                    // Time and controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Retry button when there's an error
                        if (isRetrying) {
                            Text(
                                text = "Retrying...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .clickable { 
                                        // Manual retry
                                        try {
                                            exoPlayer?.let { player ->
                                                // Store current position and playback state before retrying
                                                val retryPosition = currentPosition
                                                val wasPlaying = isPlaying // Store whether it was playing before the retry
                                                player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                                                player.prepare()
                                                // Seek to the stored position after preparing
                                                player.seekTo(retryPosition)
                                                // Resume playback if it was playing before the retry
                                                player.playWhenReady = wasPlaying
                                                // Update the UI state to match the player state
                                                isPlaying = wasPlaying
                                                isRetrying = false
                                                playerError = null
                                            }
                                        } catch (e: Exception) {
                                            // If manual retry fails, keep isRetrying true
                                        }
                                    }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                        
                        // Video speed controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showSpeedDropdown = true }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Playback speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    
                                    Text(
                                        text = String.format("%.2fx", playbackSpeed),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showSpeedDropdown,
                                    onDismissRequest = { showSpeedDropdown = false },
                                    modifier = Modifier.background(Color.Black)
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = String.format("%.2fx", speed),
                                                    color = if (speed == playbackSpeed) MaterialTheme.colorScheme.primary else Color.White,
                                                    fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
                                                )
                                            },
                                            onClick = {
                                                playbackSpeed = speed
                                                showSpeedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // Normal speed button
                            Text(
                                text = "Normal",
                                color = if (playbackSpeed == 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (playbackSpeed == 1.0f) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clickable { playbackSpeed = 1.0f }
                                    .padding(4.dp),
                                fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
                            )
                        }
                        
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType)
                        )
                    }
                }
            }
        }
        
        // Resume playback prompt - appears briefly at the top of the player when there is
        // a saved position for this content. If the user picks "continue", playback jumps
        // to that position; otherwise (including a timeout) it plays from the beginning.
        if (showResumePrompt) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(50) // pill-shaped bar, matches app's rounded style
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ادامه از ${formatTime(resumePositionMs)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                try {
                                    exoPlayer?.seekTo(resumePositionMs)
                                    currentPosition = resumePositionMs
                                } catch (e: Exception) {
                                    // Ignore seek errors
                                }
                                showResumePrompt = false
                                isPlaying = true
                            }
                            .padding(vertical = 6.dp, horizontal = 6.dp)
                    )

                    Text(
                        text = "$resumePromptSecondsLeft",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontManager.loadFontFamily(context, fontSettings.fontType),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = {
                            // Start from the beginning
                            showResumePrompt = false
                            isPlaying = true
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "شروع از ابتدا",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Track selection dialog
        if (showTrackSelectionDialog) {
            TrackSelectionDialog(
                tracks = currentTracks,
                trackSelector = trackSelector,
                onDismiss = { showTrackSelectionDialog = false }
            )
        }
    }
}

@Composable
fun TrackSelectionDialog(
    tracks: Tracks,
    trackSelector: DefaultTrackSelector?,
    onDismiss: () -> Unit
) {
    val audioTrackGroups = remember(tracks) {
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }
    
    val textTrackGroups = remember(tracks) {
        tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    }
    
    // State for dropdown menus
    var showAudioDropdown by remember { mutableStateOf(false) }
    var showSubtitleDropdown by remember { mutableStateOf(false) }
    
    // Current selections
    val currentAudioSelection = remember(audioTrackGroups) {
        audioTrackGroups.firstOrNull { it.isSelected }?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { index ->
                val format = group.getTrackFormat(index)
                format.language?.let { 
                    if (it.isNotEmpty()) it else format.label ?: "Track $index"
                } ?: format.label ?: "Track $index"
            }
        } ?: "None"
    }
    
    val currentSubtitleSelection = remember(textTrackGroups) {
        textTrackGroups.firstOrNull { it.isSelected }?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { index ->
                val format = group.getTrackFormat(index)
                format.language?.let { 
                    if (it.isNotEmpty()) it else format.label ?: "Subtitle $index"
                } ?: format.label ?: "Subtitle $index"
            }
        } ?: "None"
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Track Selection",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Audio track selection
                if (audioTrackGroups.isNotEmpty()) {
                    Text(
                        text = "Audio Tracks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Audio dropdown
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAudioDropdown = true }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentAudioSelection,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Expand audio tracks",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showAudioDropdown,
                            onDismissRequest = { showAudioDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // None option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "None",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    trackSelector?.setParameters(
                                        trackSelector.buildUponParameters()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                    )
                                    showAudioDropdown = false
                                }
                            )
                            
                            // Audio track options
                            audioTrackGroups.forEachIndexed { groupIndex, trackGroup ->
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getTrackFormat(i)
                                    val trackName = format.language?.let { 
                                        if (it.isNotEmpty()) it else format.label ?: "Track ${groupIndex + 1}.${i + 1}"
                                    } ?: format.label ?: "Track ${groupIndex + 1}.${i + 1}"
                                    
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = trackName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            trackSelector?.setParameters(
                                                trackSelector.buildUponParameters()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                                                    )
                                            )
                                            showAudioDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Subtitle track selection
                if (textTrackGroups.isNotEmpty()) {
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Subtitle dropdown
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSubtitleDropdown = true }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSubtitleSelection,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Expand subtitle tracks",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSubtitleDropdown,
                            onDismissRequest = { showSubtitleDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // None option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "None",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    trackSelector?.setParameters(
                                        trackSelector.buildUponParameters()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    )
                                    showSubtitleDropdown = false
                                }
                            )
                            
                            // Subtitle track options
                            textTrackGroups.forEachIndexed { groupIndex, trackGroup ->
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getTrackFormat(i)
                                    val trackName = format.language?.let { 
                                        if (it.isNotEmpty()) it else format.label ?: "Subtitle ${groupIndex + 1}.${i + 1}"
                                    } ?: format.label ?: "Subtitle ${groupIndex + 1}.${i + 1}"
                                    
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = trackName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            trackSelector?.setParameters(
                                                trackSelector.buildUponParameters()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                                                    )
                                            )
                                            showSubtitleDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No subtitles available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
    } else {
        String.format("%02d:%02d", remainingMinutes, remainingSeconds)
    }
}