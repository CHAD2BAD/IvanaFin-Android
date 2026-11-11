package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.FontAwesome
import kotlin.math.abs

/*
 * IvanaFin Music, Spotify TV look (design-refs/spotify-tv-home.png in jellyfin-web-custom):
 * shared pieces for the Music home, album, artist, Liked Songs and library pages.
 * Sizes are the same dp values the web/Samsung music-page.js uses (960 x 540 dp screen).
 */

internal val MuGreen = Color(0xFF1ED760)
internal val MuBg = Color(0xFF121212)
internal val MuCard = Color(0xFF282828)
internal val MuDim = Color.White.copy(alpha = .7f)
/** Goes up by one each time a tab in the Music top bar gets the focus: pages then scroll back to the top. */
internal val LocalMuToTop = compositionLocalOf { 0 }

/**
 * The song row / pill button that was selected last: starting a song opens Now Playing, and Back
 * from it puts the selection back on that row (IvanaFinMusicLibrary provides the holder).
 */
internal val LocalMuLastClicked = compositionLocalOf<androidx.compose.runtime.MutableState<androidx.compose.ui.focus.FocusRequester?>?> { null }

@Composable
internal fun rememberClickMark(): Pair<androidx.compose.ui.focus.FocusRequester, () -> Unit> {
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    val holder = LocalMuLastClicked.current
    return fr to { holder?.value = fr }
}

internal val LikedBrush = Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFF8E8EE5), Color(0xFFC4EFD9)))

/** Font Awesome (solid) glyphs */
internal object Fa {
    const val CHEVRON_UP = ""
    const val CHEVRON_DOWN = ""
    const val PLAY = ""
    const val PAUSE = ""
    const val SHUFFLE = ""
    const val PREV = ""
    const val NEXT = ""
    const val REPEAT = ""
    const val HEART = ""
    const val HOME = ""
    const val SEARCH = ""
    const val MUSIC = ""
    const val DISC = ""
    const val MORE = ""
    const val SORT = ""
}

/** The selected item's picture, top-right, fading into the page on the left and at the bottom. */
@Composable
internal fun HeroArt(
    url: String?,
    liked: Boolean,
    scrollPx: () -> Float,
    modifier: Modifier = Modifier,
    // album covers are square: shown whole on the right (cropping one to a wide strip zooms right in)
    square: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .then(if (square) Modifier.height(410.dp).aspectRatio(1f) else Modifier.fillMaxWidth(.66f).height(340.dp))
                .graphicsLayer {
                    translationY = -scrollPx()
                    alpha = .8f
                },
    ) {
        if (liked) {
            Box(Modifier.fillMaxSize().background(LikedBrush))
            Text(
                Fa.HEART,
                fontFamily = FontAwesome,
                fontSize = 96.sp,
                color = Color.White.copy(alpha = .9f),
                modifier = Modifier.align(BiasAlignment(.3f, -.25f)),
            )
        } else if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -.4f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                if (square) {
                    Brush.horizontalGradient(0f to MuBg, .14f to MuBg.copy(alpha = .55f), .38f to Color.Transparent)
                } else {
                    Brush.horizontalGradient(0f to MuBg, .22f to MuBg.copy(alpha = .6f), .5f to Color.Transparent)
                },
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to MuBg)))
    }
}

/** Big title + "(avatar) Artist • Album • N songs" on the left of the hero. */
@Composable
internal fun HeroText(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    kind: String? = null,
    avatarUrl: String? = null,
    likedAvatar: Boolean = false,
    minHeight: Dp = 141.dp,
) {
    Column(
        verticalArrangement = Arrangement.Top,
        modifier = modifier.fillMaxWidth(.52f).padding(start = 40.dp, top = 124.dp).height(minHeight),
    ) {
        if (kind != null) Text(kind, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MuDim, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            title,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            if (likedAvatar) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(LikedBrush))
                Spacer(Modifier.width(5.dp))
            } else if (avatarUrl != null) {
                var show by remember(avatarUrl) { mutableStateOf(true) }
                if (show) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { show = false },
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF535353)),
                    )
                    Spacer(Modifier.width(5.dp))
                }
            }
            Text(meta, fontSize = 11.sp, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pill button: white "Play", dark "Shuffle", or round icon-only ones. */
@Composable
internal fun MuPill(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    primary: Boolean = false,
    tint: Color? = null,
) {
    val (markFocus, mark) = rememberClickMark()
    Surface(
        onClick = {
            mark()
            onClick()
        },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) Color.White else Color.White.copy(alpha = .12f),
                focusedContainerColor = if (primary) Color.White else Color.White.copy(alpha = .22f),
                contentColor = if (primary) Color.Black else Color.White,
                focusedContentColor = if (primary) Color.Black else Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), inset = if (primary) (-4).dp else 0.dp),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.focusRequester(markFocus).height(36.dp).then(if (text == null) Modifier.width(36.dp) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().then(if (text == null) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 18.dp)),
        ) {
            Text(glyph, fontFamily = FontAwesome, fontSize = 14.sp, color = tint ?: (if (primary) Color.Black else Color.White))
            if (text != null) {
                Spacer(Modifier.width(7.dp))
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.Black else Color.White)
            }
        }
    }
}

/** Home's 2 x 4 shortcut card: small cover on the left, bold name. */
@Composable
internal fun ShortcutCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    playing: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = MuCard, focusedContainerColor = Color(0xFF3A3A3A)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(50.dp).onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).background(if (liked) LikedBrush else Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF333333)))),
                contentAlignment = Alignment.Center,
            ) {
                if (liked) {
                    Text(Fa.HEART, fontFamily = FontAwesome, fontSize = 18.sp, color = Color.White)
                } else {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                name,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (playing) MuGreen else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp),
            )
        }
    }
}

/** Square cover card with the name over the bottom of the picture (or round, name below: artists). */
@Composable
internal fun CoverCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    round: Boolean = false,
    playing: Boolean = false,
    sub: String? = null,
    nameBelow: Boolean = round || sub != null,
    width: Dp? = 106.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = if (round) CircleShape else RoundedCornerShape(4.dp)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier).onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(if (focused) Modifier.background(Color.White, shape).padding(2.dp) else Modifier)
                    .clip(shape)
                    .background(MuCard),
            ) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (!nameBelow) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                            .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 7.dp),
                    ) {
                        Text(
                            name,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 3f)),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (playing) MuGreen else Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (nameBelow) {
                Text(
                    name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (playing) MuGreen else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (round) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                if (sub != null) {
                    Text(
                        sub,
                        fontSize = 9.5.sp,
                        color = Color.White.copy(alpha = .6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (round) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MuSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = modifier.padding(start = 40.dp, bottom = 8.dp, top = 22.dp))
}

/** One song: # (bars while playing, ▶ when selected) | title + artists | duration. */
@Composable
internal fun MuTrackRow(
    number: String,
    title: String,
    sub: String,
    duration: String,
    playing: Boolean,
    paused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (markFocus, mark) = rememberClickMark()
    Surface(
        onClick = {
            mark()
            onClick()
        },
        onLongClick = onLongClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = .1f),
                pressedContainerColor = Color.White.copy(alpha = .1f),
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.focusRequester(markFocus).fillMaxWidth().padding(horizontal = 34.dp).height(44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
                when {
                    focused -> Text(Fa.PLAY, fontFamily = FontAwesome, fontSize = 11.sp, color = Color.White)
                    playing -> MuEqualizer(paused)
                    else -> Text(number, fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = if (playing) MuGreen else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 11.sp, color = Color.White.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(duration, fontSize = 11.sp, color = Color.White.copy(alpha = .65f))
        }
    }
}

@Composable
internal fun MuEqualizer(paused: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "eqp")
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(12.dp)) {
        listOf(0f, .33f, .66f, .5f).forEach { off ->
            val p = (phase + off) % 1f
            val h = if (paused) .4f else .25f + .75f * (1f - abs(2f * p - 1f))
            Box(
                Modifier
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = h
                        transformOrigin = TransformOrigin(.5f, 1f)
                    }.background(MuGreen),
            )
        }
    }
}

internal fun minSecTicks(ticks: Long): String {
    val s = (ticks / 10_000_000.0).let { kotlin.math.round(it).toInt() }
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

internal fun songsLabel(n: Int) = "$n ${if (n == 1) "song" else "songs"}"

/**
 * Scroll only when the selected item is out of view, keeping it [start] clear of the top (under the
 * top bar) or the left edge, and [end] clear of the bottom / right edge.
 */
internal class MuBringIntoView(
    private val start: Float,
    private val end: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val endEdge = containerSize - end
        return when {
            offset < start -> offset - start
            offset + size > endEdge -> kotlin.math.min(offset + size - endEdge, offset - start)
            else -> 0f
        }
    }
}

/** Never scrolls by itself (Now Playing scrolls on its own) */
internal object MuNoAutoScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = 0f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MuScroll(
    start: Dp,
    end: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val d = LocalDensity.current
    val spec = remember(start, end, d) { MuBringIntoView(with(d) { start.toPx() }, with(d) { end.toPx() }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** A row of cards, 40dp in from the sides like the page */
@Composable
internal fun MuLazyRow(
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    MuScroll(start = 40.dp, end = 40.dp) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 3.dp),
            content = content,
        )
    }
}
