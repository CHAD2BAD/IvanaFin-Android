package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/*
 * IvanaFin: the Music library laid out like Spotify's desktop app (same as the web/Samsung
 * music-page.js): "Your Library" album list on the left, the selected album (IvanaFinAlbumPage) on
 * the right, a Spotify-style player bar at the bottom. Music only plays while you're in here:
 * leaving the Music screen stops it.
 */

private val Green = Color(0xFF1ED760)
private val Panel = Color(0xFF121212)
private val Dim = Color.White.copy(alpha = .7f)

private data class MusicSort(
    val label: String,
    val by: List<ItemSortBy>,
    val order: SortOrder,
)

private val MusicSorts =
    listOf(
        MusicSort("Recents", listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME), SortOrder.DESCENDING),
        MusicSort("Alphabetical", listOf(ItemSortBy.SORT_NAME), SortOrder.ASCENDING),
        MusicSort("Creator", listOf(ItemSortBy.ALBUM_ARTIST, ItemSortBy.SORT_NAME), SortOrder.ASCENDING),
        MusicSort("Release date", listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME), SortOrder.DESCENDING),
    )

@HiltViewModel
class IvanaFinMusicViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val api: ApiClient,
        private val serverRepository: ServerRepository,
        val musicService: MusicService,
        @param:DefaultCoroutineScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("ivanafin", Context.MODE_PRIVATE)
        private val _albums = MutableStateFlow<List<BaseItem>>(emptyList())
        val albums: StateFlow<List<BaseItem>> = _albums
        private val _sort = MutableStateFlow(prefs.getInt("music_sort", 0).coerceIn(0, MusicSorts.size - 1))
        val sort: StateFlow<Int> = _sort
        private var libraryId: UUID? = null

        var lastAlbum: UUID?
            get() = prefs.getString("music_last", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            set(value) {
                prefs.edit().putString("music_last", value?.toString()).apply()
            }

        fun load(library: UUID?) {
            viewModelScope.launchIO {
                libraryId = library ?: findMusicLibrary()
                fetch()
            }
        }

        private suspend fun findMusicLibrary(): UUID? =
            try {
                api.userViewsApi
                    .getUserViews(userId = serverRepository.currentUser?.id)
                    .content.items
                    .firstOrNull { it.collectionType == CollectionType.MUSIC }
                    ?.id
            } catch (ex: Exception) {
                Timber.w(ex, "No music library")
                null
            }

        private suspend fun fetch() {
            val id = libraryId ?: return
            val s = MusicSorts[_sort.value]
            try {
                _albums.value =
                    GetItemsRequestHandler
                        .execute(
                            api,
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                parentId = id,
                                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                recursive = true,
                                sortBy = s.by,
                                sortOrder = listOf(s.order),
                                enableImageTypes = listOf(ImageType.PRIMARY),
                                imageTypeLimit = 1,
                            ),
                        ).toBaseItems(api, false)
            } catch (ex: Exception) {
                Timber.w(ex, "Loading albums failed")
            }
        }

        fun cycleSort() {
            _sort.value = (_sort.value + 1) % MusicSorts.size
            prefs.edit().putInt("music_sort", _sort.value).apply()
            viewModelScope.launchIO { fetch() }
        }

        /** Leaving Music: the music stops (it only plays while you're in Music). */
        fun stopMusic() {
            appScope.launch { musicService.stop() }
        }
    }

@Composable
fun IvanaFinMusicLibrary(
    preferences: UserPreferences,
    libraryId: UUID?,
    initialAlbumId: UUID?,
    modifier: Modifier = Modifier,
    viewModel: IvanaFinMusicViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val albums by viewModel.albums.collectAsState()
    val sortIdx by viewModel.sort.collectAsState()
    var selected by remember { mutableStateOf(initialAlbumId ?: viewModel.lastAlbum) }
    LaunchedEffect(Unit) { viewModel.load(libraryId) }
    LaunchedEffect(albums) {
        if (albums.isNotEmpty() && (selected == null || (initialAlbumId == null && albums.none { it.id == selected }))) {
            selected = albums.first().id
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopMusic() } }

    // What's playing (polled from the player: position moves every second)
    val player = viewModel.musicService.player
    var nowItem by remember { mutableStateOf<AudioItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    LaunchedEffect(Unit) {
        while (true) {
            nowItem = player.currentMediaItem?.localConfiguration?.tag as? AudioItem
            playing = player.isPlaying
            posMs = player.currentPosition.coerceAtLeast(0)
            durMs = player.duration.takeIf { it > 0 } ?: 0L
            shuffle = player.shuffleModeEnabled
            repeat = player.repeatMode
            delay(500)
        }
    }

    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    var sideHasFocus by remember { mutableStateOf(false) }
    fun focusSide() {
        scope.launch {
            val i = albums.indexOfFirst { it.id == selected }
            if (i >= 0) listState.scrollToItem(i)
            delay(30)
            selectedFocus.tryRequestFocus()
        }
    }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(albums, selected) {
        if (!focusedOnce && albums.isNotEmpty() && albums.any { it.id == selected }) {
            focusedOnce = true
            focusSide()
        }
    }
    // Back from the album or the player bar: back to Your Library first
    BackHandler(enabled = !sideHasFocus && albums.isNotEmpty()) { focusSide() }

    // Full screen width: the navigation container pads pages left/right
    val screenW = LocalWindowInfo.current.containerSize.width
    var leftPx by remember { mutableStateOf(0f) }
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { leftPx = it.positionInWindow().x }) {
        Column(
            modifier =
                Modifier
                    .layout { m, c ->
                        val w = if (screenW > 0) screenW else c.maxWidth
                        val p = m.measure(Constraints.fixed(w, c.maxHeight))
                        layout(c.maxWidth, c.maxHeight) { p.place(-leftPx.roundToInt(), 0) }
                    }.background(Color.Black),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            ) {
                // ---- Your Library ----
                Column(
                    modifier =
                        Modifier
                            .width(264.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Panel)
                            .onFocusChanged { sideHasFocus = it.hasFocus },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    ) {
                        Text("", fontFamily = FontAwesome, fontSize = 18.sp, color = Dim)
                        Text("Your Library", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("Albums", fontSize = 13.sp, color = Color.Black) }
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                    ) {
                        SmallButton(onClick = { viewModel.cycleSort() }) {
                            Text(MusicSorts[sortIdx].label, fontSize = 13.sp, color = it)
                            Spacer(Modifier.width(6.dp))
                            Text("", fontFamily = FontAwesome, fontSize = 13.sp, color = it)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(albums, key = { _, a -> a.id }) { _, a ->
                            val isSel = a.id == selected
                            LibraryRow(
                                name = a.name ?: "",
                                sub = "Album • " + (a.data.albumArtist ?: ""),
                                imageUrl = viewModel.api.imageApi.getItemImageUrl(itemId = a.id, imageType = ImageType.PRIMARY, maxWidth = 120),
                                selected = isSel,
                                playing = nowItem?.albumId == a.id,
                                onClick = {
                                    selected = a.id
                                    viewModel.lastAlbum = a.id
                                },
                                modifier = if (isSel) Modifier.focusRequester(selectedFocus) else Modifier,
                            )
                        }
                    }
                }
                // ---- the album ----
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Panel),
                ) {
                    selected?.let { id ->
                        key(id) {
                            IvanaFinAlbumPage(
                                itemId = id,
                                preferences = preferences,
                                onOpenAlbum = {
                                    selected = it
                                    viewModel.lastAlbum = it
                                },
                                requestInitialFocus = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            // ---- player bar ----
            PlayerBar(
                item = nowItem,
                playing = playing,
                posMs = posMs,
                durMs = durMs,
                shuffle = shuffle,
                repeat = repeat,
                onShuffle = { player.shuffleModeEnabled = !player.shuffleModeEnabled },
                onPrev = { player.seekToPrevious() },
                onPlayPause = { if (player.isPlaying) player.pause() else if (player.mediaItemCount > 0) player.play() },
                onNext = { player.seekToNext() },
                onRepeat = {
                    player.repeatMode =
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                },
            )
        }
    }
}

@Composable
private fun SmallButton(
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            content(if (focused) Color.White else Dim)
        }
    }
}

@Composable
private fun LibraryRow(
    name: String,
    sub: String,
    imageUrl: String,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Color(0xFF2A2A2A) else Color.Transparent,
                focusedContainerColor = Color(0xFF2A2A2A),
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF282828)),
            )
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, color = if (playing) Green else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = 13.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlayerBar(
    item: AudioItem?,
    playing: Boolean,
    posMs: Long,
    durMs: Long,
    shuffle: Boolean,
    repeat: Int,
    onShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    val idle = item == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
    ) {
        // left: cover + title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            AsyncImage(
                model = item?.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF282828)),
            )
            Column(Modifier.weight(1f)) {
                Text(item?.title ?: "", fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item?.artistNames ?: "", fontSize = 12.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // centre: controls + progress
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1.4f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BarButton("", active = shuffle, enabled = !idle, onClick = onShuffle)
                BarButton("", enabled = !idle, onClick = onPrev)
                BarButton(if (playing) "" else "", big = true, enabled = !idle, onClick = onPlayPause)
                BarButton("", enabled = !idle, onClick = onNext)
                BarButton(if (repeat == Player.REPEAT_MODE_ONE) "" else "", active = repeat != Player.REPEAT_MODE_OFF, enabled = !idle, onClick = onRepeat, badge = if (repeat == Player.REPEAT_MODE_ONE) "1" else null)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (idle) "-:--" else clock(posMs), fontSize = 11.sp, color = Dim, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4D4D4D)),
                ) {
                    val f = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Color.White))
                }
                Text(if (idle) "-:--" else clock(durMs), fontSize = 11.sp, color = Dim, modifier = Modifier.width(34.dp))
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BarButton(
    glyph: String,
    onClick: () -> Unit,
    big: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val size = if (big) 32.dp else 30.dp
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (big) Color.White else Color.Transparent,
                focusedContainerColor = if (big) Color.White else Color.Transparent,
                disabledContainerColor = if (big) Color.White.copy(alpha = .4f) else Color.Transparent,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = Modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val c =
                when {
                    big -> Color.Black
                    active -> Green
                    focused -> Color.White
                    enabled -> Dim
                    else -> Dim.copy(alpha = .3f)
                }
            Text(
                glyph,
                fontFamily = FontAwesome,
                fontSize = if (big) 13.sp else 14.sp,
                color = c,
                modifier = if (glyph == "") Modifier.padding(start = 2.dp) else Modifier,
            )
            if (badge != null) {
                Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Green, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000.0).roundToInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
