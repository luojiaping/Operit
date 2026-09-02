package com.ai.assistance.operit.ui.theme

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.theme.packages.ThemeSurfaceIdV2
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneImageFitV1
import com.ai.assistance.operit.util.AppLogger
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView

/** Renders only media explicitly enabled by the active schema-4 package presentation. */
@Composable
internal fun ThemePackageBackgroundMediaV2(
    runtime: ThemePackageUiRuntimeV2,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val media = runtime.backgroundMedia() ?: return
    when (media.type) {
        ThemeBackgroundMediaTypeV2.IMAGE ->
            ThemePackageImageBackgroundMediaV2(
                media = media,
                modifier = modifier,
            )

        ThemeBackgroundMediaTypeV2.VIDEO ->
            ThemePackageVideoBackgroundMediaV2(
                media = media,
                darkTheme = runtime.darkTheme,
                modifier = modifier,
            )
    }
}

/** Reuses the active package backdrop for non-scene hosts such as message-image export. */
@Composable
internal fun ThemePackageSurfaceBackdropV2(
    runtime: ThemePackageUiRuntimeV2,
    surface: ThemeSurfaceIdV2,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(modifier = modifier) {
        ThemePackageBackgroundMediaV2(runtime)
        runtime.stageImage(surface)?.let { image ->
            Image(
                painter = rememberAsyncImagePainter(model = Uri.parse(image.uri)),
                contentDescription = null,
                contentScale =
                    when (image.fit) {
                        ThemeSceneImageFitV1.FILL -> ContentScale.FillBounds
                        ThemeSceneImageFitV1.FIT -> ContentScale.Fit
                        ThemeSceneImageFitV1.CROP -> ContentScale.Crop
                    },
                modifier = Modifier.fillMaxSize().alpha(image.opacity),
            )
        }
    }
}

@Composable
private fun ThemePackageImageBackgroundMediaV2(
    media: ResolvedThemeBackgroundMediaV2,
    modifier: Modifier,
) {
    val painter =
        rememberAsyncImagePainter(
            model = Uri.parse(media.uri),
        )
    val state = painter.state
    LaunchedEffect(state, media.uri) {
        if (state is AsyncImagePainter.State.Error) {
            AppLogger.e(
                TAG,
                "Unable to load package background image: ${media.uri}",
                state.result.throwable,
            )
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxSize()
                .alpha(media.opacity)
                .then(if (media.blurEnabled) Modifier.blur(media.blurRadiusDp.dp) else Modifier)
                .then(modifier),
    )
}

@Composable
private fun ThemePackageVideoBackgroundMediaV2(
    media: ResolvedThemeBackgroundMediaV2,
    darkTheme: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestUri by rememberUpdatedState(media.uri)
    val player =
        remember(media.uri, media.videoLoop, media.videoMuted) {
            ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(5_000, 10_000, 500, 1_000)
                        .setTargetBufferBytes(5 * 1024 * 1024)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build(),
                ).build()
                .apply {
                    repeatMode = if (media.videoLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                    volume = if (media.videoMuted) 0f else 1f
                    playWhenReady = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    setMediaItem(MediaItem.fromUri(Uri.parse(media.uri)))
                    prepare()
                }
        }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    AppLogger.e(TAG, "Package background video playback failed: $latestUri", error)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> player.play()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundColor = if (darkTheme) AndroidColor.BLACK else AndroidColor.WHITE
    val overlayColor =
        AndroidColor.argb(
            ((1f - media.opacity) * 255f).toInt().coerceIn(0, 255),
            if (darkTheme) 0 else 255,
            if (darkTheme) 0 else 255,
            if (darkTheme) 0 else 255,
        )
    AndroidView(
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.view_background_texture_player, null, false) as StyledPlayerView)
                .apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(backgroundColor)
                    setShutterBackgroundColor(backgroundColor)
                    setKeepContentOnPlayerReset(true)
                    foreground = ColorDrawable(overlayColor)
                }
        },
        update = { view ->
            view.player = player
            view.setBackgroundColor(backgroundColor)
            view.setShutterBackgroundColor(backgroundColor)
            view.foreground = ColorDrawable(overlayColor)
        },
        modifier = Modifier.fillMaxSize().then(modifier),
    )
}

private const val TAG = "ThemePackageBackground"
