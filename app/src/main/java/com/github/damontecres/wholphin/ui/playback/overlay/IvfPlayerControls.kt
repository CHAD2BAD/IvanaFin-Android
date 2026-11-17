package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.skipStringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// IvanaFin player controls (2026-11-17, user: "I much prefer the player controls and slider on Samsung ...
// mirror them so Android looks like Samsung"). Same layout as Jellyfin web's TV player with the ElegantFin
// theme, measured from a photo of the Samsung:
//   [prev] [chapter back] [rewind] (PLAY) [forward] [chapter next] [next]  Ends at 21:10 ....  [skip] [heart] [cc] [audio] [settings]
//   0:09 ======================== seek bar with chapter ticks ======================== -2:27:01
// Buttons have no background until selected (then a #d1d5db circle with a dark icon), play/pause is the big
// one, played part of the bar #d1d5db, the rest 50% grey.

internal val IvfOsdSelected = Color(0xFFD1D5DB)
internal val IvfOsdSelectedIcon = Color(0xFF131414)
internal val IvfOsdTrack = Color(0x80808080)
private val IvfOsdText =
    TextStyle(
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 4f),
    )

@Composable
fun IvfPlaybackControls(
    player: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    chapters: List<Chapter>,
    favorite: Boolean,
    onControllerInteraction: () -> Unit,
    initialFocusRequester: FocusRequester,
    seekBarInteractionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // left: transport buttons, then "Ends at"
                Row(
                    modifier = Modifier.focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (previousEnabled) {
                        IvfOsdButton(R.drawable.baseline_skip_previous_24, onControllerInteraction) {
                            onPlaybackActionClick(PlaybackAction.Previous)
                        }
                    }
                    if (chapters.isNotEmpty()) {
                        IvfOsdButton(R.drawable.baseline_undo_24, onControllerInteraction) {
                            // like Jellyfin web: back to the start of this chapter, or the one before if just started
                            val pos = player.currentPosition
                            val target = chapters.lastOrNull { it.position.inWholeMilliseconds < pos - 3_000 }
                            player.seekTo(target?.position?.inWholeMilliseconds ?: 0L)
                        }
                    }
                    IvfOsdButton(R.drawable.baseline_fast_rewind_24, onControllerInteraction) { player.seekBack(seekBack) }
                    IvfOsdButton(
                        iconRes = if (showPlay) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24,
                        onControllerInteraction = onControllerInteraction,
                        size = 56.dp,
                        iconSize = 34.dp,
                        modifier = Modifier.padding(horizontal = 6.dp).focusRequester(initialFocusRequester),
                    ) {
                        if (showPlay) {
                            player.play()
                            skipBackOnResume?.let { player.seekBack(it) }
                        } else {
                            player.pause()
                        }
                    }
                    IvfOsdButton(R.drawable.baseline_fast_forward_24, onControllerInteraction) { player.seekForward(seekForward) }
                    if (chapters.isNotEmpty()) {
                        IvfOsdButton(R.drawable.ivf_redo_24, onControllerInteraction) {
                            val pos = player.currentPosition
                            chapters.firstOrNull { it.position.inWholeMilliseconds > pos + 1_000 }?.let {
                                player.seekTo(it.position.inWholeMilliseconds)
                            }
                        }
                    }
                    if (nextEnabled) {
                        IvfOsdButton(R.drawable.baseline_skip_next_24, onControllerInteraction) {
                            onPlaybackActionClick(PlaybackAction.Next)
                        }
                    }
                }
                IvfEndsAt(player, Modifier.padding(start = 24.dp))
                Spacer(Modifier.weight(1f))
                // right: skip intro/credits, favourite, subtitles, audio, settings
                Row(
                    modifier = Modifier.focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    currentSegment?.let { segment ->
                        TextButton(
                            stringRes = segment.type.skipStringRes,
                            onClick = { player.seekTo(segment.endTicks.ticks.inWholeMilliseconds) },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                    IvfOsdButton(
                        iconRes = R.drawable.ivf_favorite_24,
                        onControllerInteraction = onControllerInteraction,
                        // Jellyfin web: the heart turns red for a favourite
                        iconColor = if (favorite) Color(0xFFE53935) else null,
                    ) { onPlaybackActionClick(PlaybackAction.ToggleFavorite(!favorite)) }
                    IvfOsdButton(R.drawable.ivf_closed_caption_24, onControllerInteraction) {
                        onClickPlaybackDialogType(PlaybackDialogType.CAPTIONS)
                    }
                    IvfOsdButton(R.drawable.ivf_audiotrack_24, onControllerInteraction) {
                        onClickPlaybackDialogType(PlaybackDialogType.AUDIO)
                    }
                    IvfOsdButton(R.drawable.vector_settings, onControllerInteraction) {
                        onClickPlaybackDialogType(PlaybackDialogType.SETTINGS)
                    }
                }
            }
            IvfSeekRow(
                player = player,
                controllerViewState = controllerViewState,
                onSeekProgress = onSeekProgress,
                seekEnabled = seekEnabled,
                seekBack = seekBack,
                seekForward = seekForward,
                chapters = chapters,
                interactionSource = seekBarInteractionSource,
            )
        }
    }
}

/** A round player button: just the icon, a #d1d5db circle with a dark icon when selected. */
@Composable
fun IvfOsdButton(
    @DrawableRes iconRes: Int,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
    iconColor: Color? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            onControllerInteraction()
            onClick()
        },
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                focusedContainerColor = IvfOsdSelected,
                focusedContentColor = IvfOsdSelectedIcon,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
        modifier =
            modifier
                .size(size)
                .onFocusChanged {
                    focused = it.isFocused
                    onControllerInteraction()
                },
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (iconColor != null && !focused) iconColor else androidx.tv.material3.LocalContentColor.current,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun IvfEndsAt(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var endTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(player, context) {
        while (isActive) {
            val remaining =
                (player.duration - player.currentPosition)
                    .div(player.playbackParameters.speed)
                    .toLong()
                    .milliseconds
            endTimeStr = formatTime(context, LocalTime.now().plusSeconds(remaining.inWholeSeconds))
            delay(1.seconds)
        }
    }
    if (endTimeStr.isNotEmpty()) {
        Text(text = stringResource(R.string.ends_at, endTimeStr), style = IvfOsdText, modifier = modifier)
    }
}

/** Jellyfin web's time format: 0:09, 12:34, 2:27:01 */
internal fun ivfClock(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) / 1000)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun IvfSeekRow(
    player: Player,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    seekEnabled: Boolean,
    seekBack: Duration,
    seekForward: Duration,
    chapters: List<Chapter>,
    interactionSource: MutableInteractionSource,
) {
    var bufferedProgress by remember(player) { mutableFloatStateOf(player.bufferedPosition.toFloat() / player.duration) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(player) { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            position = player.currentPosition
            duration = player.duration
            delay(250L)
        }
    }
    val markers =
        remember(chapters, duration) {
            if (duration <= 0) emptyList() else chapters.map { it.position.inWholeMilliseconds.toFloat() / duration }.filter { it > 0f && it < 1f }
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = ivfClock(position), style = IvfOsdText, modifier = Modifier.widthIn(min = 56.dp))
        IntervalSeekBarImpl(
            progress = position.toFloat() / duration,
            bufferedProgress = bufferedProgress,
            onSeek = onSeekProgress,
            controllerViewState = controllerViewState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            interactionSource = interactionSource,
            enabled = seekEnabled,
            durationMs = duration,
            seekBack = seekBack,
            seekForward = seekForward,
            ivfStyle = true,
            markers = markers,
        )
        Text(
            text = "-" + ivfClock(duration - position),
            style = IvfOsdText,
            modifier = Modifier.width(80.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
