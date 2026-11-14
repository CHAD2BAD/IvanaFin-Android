package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween as tweenSpec
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import coil3.request.crossfade
import com.github.damontecres.wholphin.ui.FontAwesome
import kotlin.math.abs

/*
 * IvanaFin Music, Spotify TV look (design-refs/spotify-tv-home.png in jellyfin-web-custom):
 * shared pieces for the Music home, album, artist, Liked Songs and library pages.
 * Sizes are the same dp values the web/Samsung music-page.js uses (960 x 540 dp screen).
 */

// MuGreen (the accent) and MuBg (the page colour) now follow the page's picture: IvanaFinMusicColour.kt
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

/** Whether the music is playing (not paused): the playing bars on cards move only then. */
internal val LocalMuPlaying = compositionLocalOf { false }

/** A picture that fades in (250 ms) instead of popping in. Pictures already in memory show at once. */
@Composable
internal fun MuImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    onError: ((coil3.compose.AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val req =
        remember(model) {
            if (model is String) coil3.request.ImageRequest.Builder(context).data(model).crossfade(250).build() else model
        }
    AsyncImage(model = req, contentDescription = contentDescription, contentScale = contentScale, alignment = alignment, onError = onError, modifier = modifier)
}

/** Focus lift (Spotify / Apple TV): the selected card grows ~6% and gets a soft shadow, with a springy ease. */
@Composable
internal fun muLift(
    focused: Boolean,
    scale: Float = 1.06f,
): Float {
    val s by androidx.compose.animation.core.animateFloatAsState(
        if (focused) scale else 1f,
        androidx.compose.animation.core.spring(dampingRatio = .62f, stiffness = 900f),
        label = "lift",
    )
    return s
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
    const val CHECK = ""
    const val LYRICS = ""
    const val PLUS = ""
    const val RADIO = ""
    const val REMOVE = ""
}

/**
 * The selected item's picture, top-right. Wide pictures (artists) fade into the page on the left and at
 * the bottom; album covers (square) are shown whole and sharp - rounded corners, a soft shadow, no fade
 * (user 2026-11-13: the colour fading into the cover looked odd) - small enough to sit above the
 * shortcut cards and the track list.
 */
@Composable
internal fun HeroArt(
    url: String?,
    liked: Boolean,
    scrollPx: () -> Float,
    modifier: Modifier = Modifier,
    // album covers are square: shown whole on the right (cropping one to a wide strip zooms right in)
    square: Boolean = false,
) {
    // an album cover: Now Playing's cover flies from here
    val flight = LocalMuFlight.current
    if (square && flight != null) androidx.compose.runtime.DisposableEffect(flight) { onDispose { flight.hero = null } }
    Box(
        modifier =
            modifier
                .then(if (square) Modifier.padding(top = 24.dp, end = 40.dp).size(230.dp) else Modifier.fillMaxWidth(.66f).height(340.dp))
                .graphicsLayer {
                    translationY = -scrollPx()
                    if (square) {
                        shadowElevation = 18.dp.toPx()
                        shape = RoundedCornerShape(8.dp)
                        clip = true
                    } else {
                        alpha = .8f
                    }
                }.then(
                    if (square && flight != null && !liked) {
                        Modifier.onGloballyPositioned { flight.hero = it.boundsInWindow() }
                    } else {
                        Modifier
                    },
                ),
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
            Crossfade(targetState = url, animationSpec = tweenSpec(500), label = "hero") { u ->
                MuImage(
                    model = u,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -.4f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (square) return@Box // (a sharp cover: no fade)
        val edge = LocalMuColors.current.edge
        Box(
            Modifier.fillMaxSize().drawBehind {
                val e = edge.value
                drawRect(
                    if (square) {
                        Brush.horizontalGradient(0f to e, .14f to e.copy(alpha = .55f), .38f to Color.Transparent)
                    } else {
                        Brush.horizontalGradient(0f to e, .22f to e.copy(alpha = .6f), .5f to Color.Transparent)
                    },
                )
                drawRect(if (square) Brush.verticalGradient(.25f to Color.Transparent, .8f to e, 1f to e) else Brush.verticalGradient(.45f to Color.Transparent, 1f to e))
            },
        )
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
    // album pages: the artist (avatar + name) is a button to the artist's page; meta is the rest
    artistName: String? = null,
    onArtist: (() -> Unit)? = null,
    artistModifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Top,
        modifier = modifier.fillMaxWidth(.52f).padding(start = 40.dp, top = 124.dp).height(minHeight),
    ) {
        if (kind != null) Text(kind, fontSize = MuType.label, fontWeight = FontWeight.Medium, color = MuDim, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            title,
            fontSize = MuType.heroTitle,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (artistName != null && onArtist != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Surface(
                    modifier = artistModifier,
                    onClick = onArtist,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)) {
                        if (avatarUrl != null) {
                            var show by remember(avatarUrl) { mutableStateOf(true) }
                            if (show) {
                                MuImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    onError = { show = false },
                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF535353)),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                        Text(artistName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                }
                if (meta.isNotEmpty()) Text(" • $meta", fontSize = 11.sp, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            if (likedAvatar) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(LikedBrush))
                Spacer(Modifier.width(5.dp))
            } else if (avatarUrl != null) {
                var show by remember(avatarUrl) { mutableStateOf(true) }
                if (show) {
                    MuImage(
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
    // the heart: null = not a heart; true = filled (on), false = outline (off)
    heartOn: Boolean? = null,
) {
    val (markFocus, mark) = rememberClickMark()
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            mark()
            onClick()
        },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) MuGreen else Color.White.copy(alpha = .12f),
                focusedContainerColor = if (primary) MuGreen else Color.White.copy(alpha = .22f),
                contentColor = if (primary) MuInk else Color.White,
                focusedContentColor = if (primary) MuInk else Color.White,
            ),
        // the white (primary) pill: a ring with a negative inset never showed, so it gets one drawn
        // outside it (below) with a dark gap between - same as the selected white chip
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = if (primary) Border.None else Border(BorderStroke(2.dp, Color.White)),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier =
            modifier
                .focusRequester(markFocus)
                .height(36.dp)
                .then(if (text == null) Modifier.width(36.dp) else Modifier)
                .onFocusChanged { focused = it.hasFocus || it.isFocused }
                .drawWithContent {
                    drawContent()
                    if (primary && focused) {
                        val g = 4.dp.toPx()
                        val w = 2.dp.toPx()
                        drawRoundRect(
                            color = Color.White,
                            topLeft = androidx.compose.ui.geometry.Offset(-g - w / 2, -g - w / 2),
                            size = androidx.compose.ui.geometry.Size(size.width + 2 * g + w, size.height + 2 * g + w),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + g),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(w),
                        )
                    }
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight().then(if (text == null) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 18.dp)),
        ) {
            if (heartOn != null) {
                androidx.tv.material3.Icon(
                    if (heartOn) androidx.compose.material.icons.Icons.Filled.Favorite else androidx.compose.material.icons.Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = tint ?: Color.White,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(glyph, fontFamily = FontAwesome, fontSize = 14.sp, color = tint ?: (if (primary) MuInk else Color.White))
            }
            if (text != null) {
                Spacer(Modifier.width(7.dp))
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (primary) MuInk else Color.White)
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
    val wash = (if (liked) LikedPal else rememberMuPal(imageUrl))?.wash
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = wash ?: Color.White.copy(alpha = .09f),
                focusedContainerColor = wash?.let { lighter(it) } ?: Color.White.copy(alpha = .16f),
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        // focus lift (the TV Surface animates its own scale; a wide card grows a little less)
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Color.Black.copy(alpha = .5f), 10.dp)),
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
                    MuImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 10.dp),
            )
            if (playing) {
                Box(Modifier.padding(end = 12.dp)) { MuEqualizer(!LocalMuPlaying.current) }
            }
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
    // Library / Browse grids: the card sits on a panel washed with its picture's colour
    panel: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    // (from the Surface's own focus state: the interaction source's focus can be missed)
    var focused by remember { mutableStateOf(false) }
    val shape = if (round) CircleShape else RoundedCornerShape(4.dp)
    val panelColor = if (panel) rememberMuPal(imageUrl)?.wash ?: Color.White.copy(alpha = .06f) else null
    val lift = muLift(focused)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        // focus lift: the whole card grows (the Surface clips what is inside it, so growing the
        // picture alone was cut off); square cards with the name on the picture also get a soft shadow
        modifier =
            modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    if (!nameBelow && !panel) {
                        shadowElevation = ((lift - 1f) / .06f).coerceIn(0f, 1f) * 10.dp.toPx()
                        this.shape = RoundedCornerShape(4.dp)
                    }
                }.onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
    ) {
        Column(if (panelColor != null) Modifier.background(panelColor, RoundedCornerShape(8.dp)).padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 9.dp) else Modifier) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    // white ring, then a dark gap, like web (beta round 5: on a white picture - the Kanye West
                    // artist circle - the ring alone could not be seen)
                    .then(if (focused) Modifier.background(Color.White, shape).padding(2.dp).background(Color(0xFF121212), shape).padding(2.dp) else Modifier)
                    .clip(shape)
                    .background(MuCard),
            ) {
                MuImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (playing) {
                    Box(
                        Modifier
                            .align(if (round) Alignment.BottomCenter else Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = .6f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                    ) { MuEqualizer(!LocalMuPlaying.current) }
                }
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
                            fontSize = MuType.cardTitle,
                            lineHeight = 13.sp,
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
                    fontSize = MuType.cardTitle,
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
                        fontSize = MuType.label,
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
    Text(text, fontSize = MuType.rowTitle, fontWeight = FontWeight.Bold, color = Color.White, modifier = modifier.padding(start = 40.dp, bottom = 8.dp, top = 22.dp))
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
        modifier = modifier.fillMaxWidth().focusRequester(markFocus).padding(horizontal = 34.dp).height(44.dp),
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
                Text(title, fontSize = MuType.body, color = if (playing) MuGreen else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = MuType.cardTitle, color = Color.White.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** A card colour a step lighter (selected) */
internal fun lighter(c: Color): Color = Color(red = c.red + (1 - c.red) * .12f, green = c.green + (1 - c.green) * .12f, blue = c.blue + (1 - c.blue) * .12f, alpha = c.alpha)
/**
 * Loading placeholder: grey rounded shapes where the hero, the cards and a row will appear, with a slow
 * shimmer (an opacity pulse: cheap on the Fire TV), so Music never shows a blank page while it loads.
 */
@Composable
internal fun MuSkeleton(
    shortcuts: Boolean = false,
    songs: Boolean = false,
) {
    val t = rememberInfiniteTransition(label = "skel")
    val a by t.animateFloat(.06f, .13f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skela")
    val grey = Color.White.copy(alpha = a)
    val r = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 124.dp)) {
        Box(Modifier.width(320.dp).height(32.dp).background(grey, r))
        Box(Modifier.padding(top = 10.dp).width(200.dp).height(12.dp).background(grey, r))
        Spacer(Modifier.height(28.dp))
        if (shortcuts) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    repeat(4) { Box(Modifier.weight(1f).height(50.dp).background(grey, r)) }
                }
            }
        }
        if (songs) {
            repeat(6) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                    Box(Modifier.width(260.dp).height(12.dp).background(grey, r))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.width(30.dp).height(10.dp).background(grey, r))
                }
            }
        } else {
            Box(Modifier.padding(top = 22.dp, bottom = 8.dp).width(140.dp).height(16.dp).background(grey, r))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(7) { Box(Modifier.size(106.dp).background(grey, r)) }
            }
        }
    }
}

/** A Made for you mix card: 2 x 2 album covers with the mix name on top (Spotify mixes), the line below. */
@Composable
internal fun MixCard(
    name: String,
    sub: String,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val lift = muLift(focused)
    val shape = RoundedCornerShape(4.dp)
    // the mix starts playing: Back from Now Playing comes back to this card (it went to the mini player - round 6)
    val (mark, onMark) = rememberClickMark()
    Surface(
        onClick = {
            onMark()
            onClick()
        },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .focusRequester(mark)
                .width(106.dp)
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                }.onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                },
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
                Column(Modifier.fillMaxSize()) {
                    for (r in 0..1) {
                        Row(Modifier.weight(1f)) {
                            for (c in 0..1) {
                                // fewer than 4 covers: repeat them
                                val u = covers.getOrNull(r * 2 + c) ?: covers.getOrNull((r * 2 + c) % covers.size.coerceAtLeast(1))
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    if (u != null) MuImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .78f), .35f to Color.Black.copy(alpha = .45f), .6f to Color.Transparent)),
                // (darker: the name was hard to read on bright collages - Mixtapes & More, beta round 6)
                )
                Text(
                    name,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .9f), blurRadius = 6f)),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(sub, fontSize = MuType.label, color = Color.White.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Start radio (Instant Mix) from an album / artist id: the Radio buttons (IvanaFinMusicLibrary provides it) */
internal val LocalMuRadio = compositionLocalOf<((java.util.UUID) -> Unit)?> { null }

/**
 * Now Playing opening (plan part 1): where the cover was on screen, so Now Playing's cover can fly
 * from there into place - the album picture on the page (album pages, Home's album hero) or the
 * mini player's cover in the top bar. (Window coordinates; null = no cover there.)
 */
internal class MuFlight {
    var hero: androidx.compose.ui.geometry.Rect? = null
    var mini: androidx.compose.ui.geometry.Rect? = null
}

internal val LocalMuFlight = androidx.compose.runtime.staticCompositionLocalOf<MuFlight?> { null }

/**
 * One set of text sizes for all of Music (plan part 5; same list as music-page.js MU_TYPE on the
 * web/Samsung, in the 960-wide dp both use). The plan's 48/40/22/14/12 are for a 1920-wide screen:
 * these are the same steps at this scale, kept at the sizes the pages already had.
 */
internal object MuType {
    val heroTitle = 32.sp // album / artist / playlist / Now Playing title
    val pageTitle = 26.sp // Your Library
    val rowTitle = 16.sp // row headings: Recently added, Up next, About the artist
    val body = 13.sp // a song's name in a list
    val cardTitle = 11.sp // a card's name, a song's artist line
    val label = 10.sp // small grey lines: "Album", times, a card's sub line
}
