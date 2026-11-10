package com.github.damontecres.wholphin.ui.detail.music

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.MusicContextActions
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * IvanaFin: a music album laid out like Spotify's album page (same as the web/Samsung album-page.js):
 *   coloured header (colour from the cover): cover, "Album", big bold title,
 *   "(artist photo) Artist • 2024 • 16 songs, 55 min 41 sec"
 *   round green Play (Pause while this album plays), Shuffle, Favourite, More
 *   # | Title (artists under it) | duration, the playing track green with moving bars
 *   release year, then "More by <artist>"
 * Playing, queueing and the More menu are Wholphin's own (AlbumViewModel / MusicService).
 */

private val SpotifyGreen = Color(0xFF1ED760)
private val SpotifyGreenLight = Color(0xFF3BE477)
private val PageBg = Color(0xFF121212)
private val Dim = Color.White.copy(alpha = .7f)

private const val ROW_BUTTONS = 0
private const val ROW_SONGS = 1
private const val ROW_MORE = 2

@Composable
fun IvanaFinAlbumPage(
    itemId: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    initialSongId: UUID? = null,
    // IvanaFin music layout: the album sits in the right-hand panel; other albums open there too
    onOpenAlbum: ((UUID) -> Unit)? = null,
    requestInitialFocus: Boolean = true,
    viewModel: AlbumViewModel =
        hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(
            key = "album-$itemId",
            creationCallback = { it.create(itemId, initialSongId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val currentMusic by viewModel.currentMusic.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    val moreDialogActions =
        remember {
            MusicContextActions(
                navigateTo = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { index, _ -> viewModel.play(false, index) },
                onClickPlayNext = { _, song -> viewModel.playNext(song) },
                onClickAddToQueue = { item -> viewModel.addToQueue(item, -1) },
                onClickFavorite = { id, favorite -> viewModel.setFavorite(id, favorite) },
                onClickAddPlaylist = { id ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(id)
                },
                onClickRemoveFromQueue = { _, _ -> },
                onDeleteItem = viewModel::deleteItem,
            )
        }
    fun menuFor(
        item: BaseItem,
        index: Int,
        fromLongClick: Boolean,
    ) {
        showContextMenu =
            ContextMenu.ForMusic(
                fromLongClick = fromLongClick,
                item = item,
                index = index,
                canDelete = viewModel.canDelete(item, preferences.appPreferences),
                canRemoveFromQueue = false,
                actions = moreDialogActions,
            )
    }

    when (val loading = state.loading) {
        is LoadingState.Error -> ErrorMessage(loading, modifier)
        LoadingState.Loading, LoadingState.Pending -> LoadingPage(modifier)
        LoadingState.Success -> {
            val album = state.album!!
            val songs = state.songs
            val api = viewModel.api

            // Header colour from the cover (same recipe as the web page)
            var coverColor by remember(album.id) { mutableStateOf(Color(0xFF3A3A3A)) }
            val bandColor by animateColorAsState(coverColor, tween(400), label = "band")
            LaunchedEffect(state.imageUrl) {
                val url = state.imageUrl ?: return@LaunchedEffect
                val bmp =
                    withContext(Dispatchers.IO) {
                        try {
                            val req =
                                ImageRequest
                                    .Builder(context)
                                    .data(url)
                                    .size(48)
                                    .allowHardware(false)
                                    .build()
                            (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                bmp?.let { coverColor = coverTone(it) }
            }

            // More by <album artist>
            val artist = album.data.albumArtists?.firstOrNull()
            var moreBy by remember(album.id) { mutableStateOf<List<BaseItem>>(emptyList()) }
            LaunchedEffect(album.id) {
                val artistId = artist?.id ?: return@LaunchedEffect
                moreBy =
                    withContext(Dispatchers.IO) {
                        try {
                            GetItemsRequestHandler
                                .execute(
                                    api,
                                    GetItemsRequest(
                                        userId = viewModel.serverRepository.currentUser?.id,
                                        albumArtistIds = listOf(artistId),
                                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                        recursive = true,
                                        sortBy = listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        limit = 30,
                                    ),
                                ).toBaseItems(api, false)
                                .filter { it.id != album.id }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
            }

            val albumPlaying = songs.any { it != null && it.id == currentMusic.currentItemId }
            val isPlayingNow = currentMusic.status == NowPlayingStatus.PLAYING
            val multiDisc = remember(songs) { songs.mapNotNull { it?.data?.parentIndexNumber }.distinct().size > 1 }
            val totalTicks = album.data.runTimeTicks ?: songs.sumOf { it?.data?.runTimeTicks ?: 0L }

            var position by rememberPosition(
                row = if (state.initialSongIndex != null) ROW_SONGS else ROW_BUTTONS,
                column = state.initialSongIndex ?: 0,
            )
            val playFocus = remember { FocusRequester() }
            val songFocus = remember { FocusRequester() }
            val moreFocus = remember { FocusRequester() }
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) {
                if (!requestInitialFocus) return@LaunchedEffect
                when (position.row) {
                    ROW_SONGS -> songFocus.tryRequestFocus()
                    ROW_MORE -> moreFocus.tryRequestFocus()
                    else -> playFocus.tryRequestFocus()
                }
            }
            // Back from further down the page: first back to the top (Play)
            BackHandler(listState.firstVisibleItemIndex > 2) {
                scope.launch {
                    listState.animateScrollToItem(0)
                    playFocus.tryRequestFocus()
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = modifier.fillMaxSize().background(PageBg),
            ) {
                // ---- header ----
                item("header") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(bandColor)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f))))
                                .padding(start = 22.dp, end = 22.dp, top = 40.dp, bottom = 22.dp),
                    ) {
                        AsyncImage(
                            model = state.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(193.dp)
                                    .shadow(24.dp, RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Album", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            val name = album.name ?: ""
                            val maxSp = if (name.length > 40) 44 else if (name.length > 22) 58 else 73
                            BasicText(
                                text = name,
                                style =
                                    TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-0.04).em,
                                        lineHeight = 1.05.em,
                                    ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = maxSp.sp, stepSize = 2.sp),
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                var showAvatar by remember(artist?.id) { mutableStateOf(artist != null) }
                                if (artist != null && showAvatar) {
                                    AsyncImage(
                                        model = api.imageApi.getItemImageUrl(itemId = artist.id, imageType = ImageType.PRIMARY, maxWidth = 96),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        onError = { showAvatar = false },
                                        modifier = Modifier.size(22.dp).clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                val bits = mutableListOf<String>()
                                album.data.productionYear?.let { bits.add(it.toString()) }
                                val count = songs.size
                                bits.add("$count ${if (count == 1) "song" else "songs"}" + if (totalTicks > 0) ", ${longDuration(totalTicks)}" else "")
                                Text(
                                    text = album.data.albumArtist ?: artist?.name ?: "",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = (if (album.data.albumArtist != null || artist != null) " • " else "") + bits.joinToString(" • "),
                                    color = Dim,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                // ---- buttons + table head on a dark wash that fades into the page ----
                item("actions") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(bandColor)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), PageBg))),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            modifier =
                                Modifier
                                    .padding(22.dp)
                                    .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                        ) {
                            val pauseShown = albumPlaying && isPlayingNow
                            RoundButton(
                                glyph = if (pauseShown) "" else "",
                                size = 51,
                                glyphSize = 20,
                                container = SpotifyGreen,
                                focusedContainer = SpotifyGreenLight,
                                content = Color.Black,
                                onClick = {
                                    position = RowColumn(ROW_BUTTONS, 0)
                                    if (albumPlaying) {
                                        val p = viewModel.musicService.player
                                        if (p.isPlaying) p.pause() else p.play()
                                    } else {
                                        viewModel.play(false, 0)
                                    }
                                },
                                modifier = Modifier.focusRequester(playFocus),
                            )
                            RoundButton(glyph = "", size = 40, glyphSize = 22, onClick = {
                                position = RowColumn(ROW_BUTTONS, 1)
                                viewModel.play(true, 0)
                            })
                            RoundButton(
                                glyph = if (album.favorite) "" else "",
                                size = 40,
                                glyphSize = 26,
                                content = if (album.favorite) SpotifyGreen else Dim,
                                onClick = { viewModel.setFavorite(album.id, !album.favorite) },
                            )
                            RoundButton(glyph = "", size = 40, glyphSize = 22, onClick = { menuFor(album, 0, false) })
                        }
                        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 14.dp),
                            ) {
                                Text("#", color = Dim, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
                                Text("Title", color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("", fontFamily = FontAwesome, color = Dim, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
                // ---- tracks ----
                itemsIndexed(songs) { index, song ->
                    val disc = song?.data?.parentIndexNumber
                    val prevDisc = if (index > 0) songs.getOrNull(index - 1)?.data?.parentIndexNumber else null
                    Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                        if (multiDisc && disc != null && (index == 0 || disc != prevDisc)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.height(40.dp).padding(start = 14.dp, top = 10.dp),
                            ) {
                                Text("", fontFamily = FontAwesome, color = Dim, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
                                Text("Disc $disc", color = Dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        TrackRow(
                            number = (song?.data?.indexNumber ?: (index + 1)).toString(),
                            title = song?.name ?: "",
                            artists = song?.data?.artists?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: album.data.albumArtist ?: "",
                            duration = song?.data?.runTimeTicks?.let { minSec(it) } ?: "",
                            playing = song != null && song.id == currentMusic.currentItemId,
                            paused = !isPlayingNow,
                            onClick = {
                                position = RowColumn(ROW_SONGS, index)
                                if (song != null && song.id == currentMusic.currentItemId) {
                                    val p = viewModel.musicService.player
                                    if (p.isPlaying) p.pause() else p.play()
                                } else {
                                    viewModel.play(false, index)
                                }
                            },
                            onLongClick = { if (song != null) menuFor(song, index, true) },
                            modifier =
                                if (position.row == ROW_SONGS && position.column == index) {
                                    Modifier.focusRequester(songFocus)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
                // ---- release + More by ----
                item("release") {
                    val year = album.data.productionYear
                    val studios = album.data.studios.orEmpty().mapNotNull { it.name }
                    if (year != null || studios.isNotEmpty()) {
                        Column(modifier = Modifier.padding(start = 36.dp, end = 36.dp, top = 28.dp)) {
                            year?.let { Text(it.toString(), color = Dim, fontSize = 14.sp) }
                            if (studios.isNotEmpty()) Text("© " + studios.joinToString(", "), color = Dim, fontSize = 13.sp)
                        }
                    }
                }
                if (moreBy.isNotEmpty()) {
                    item("more") {
                        Column(modifier = Modifier.padding(top = 36.dp)) {
                            Text(
                                text = "More by " + (album.data.albumArtist ?: artist?.name ?: ""),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 22.dp, bottom = 8.dp),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                itemsIndexed(moreBy) { i, a ->
                                    AlbumCard(
                                        title = a.name ?: "",
                                        subtitle = listOfNotNull(a.data.productionYear?.toString(), "Album").joinToString(" • "),
                                        imageUrl = api.imageApi.getItemImageUrl(itemId = a.id, imageType = ImageType.PRIMARY, maxWidth = 300),
                                        onClick = {
                                            position = RowColumn(ROW_MORE, i)
                                            onOpenAlbum?.invoke(a.id) ?: viewModel.navigationManager.navigateTo(a.destination())
                                        },
                                        modifier =
                                            if (position.row == ROW_MORE && position.column == i) Modifier.focusRequester(moreFocus) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { id ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, id)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, id)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

@Composable
private fun RoundButton(
    glyph: String,
    size: Int,
    glyphSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Transparent,
    focusedContainer: Color = Color.Transparent,
    content: Color = Dim,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                focusedContainerColor = focusedContainer,
                pressedContainerColor = focusedContainer,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier.size(size.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                fontFamily = FontAwesome,
                fontSize = glyphSize.sp,
                color = if (focused && content == Dim) Color.White else content,
                // the play triangle looks centred when nudged right a little
                modifier = if (glyph == "") Modifier.padding(start = 3.dp) else Modifier,
            )
        }
    }
}

@Composable
private fun TrackRow(
    number: String,
    title: String,
    artists: String,
    duration: String,
    playing: Boolean,
    paused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
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
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(51.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        ) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                when {
                    focused -> Text("", fontFamily = FontAwesome, fontSize = 13.sp, color = Color.White)
                    playing -> EqualizerBars(paused)
                    else -> Text(number, fontSize = 14.sp, color = Dim)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = if (playing) SpotifyGreen else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = artists, fontSize = 13.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(duration, fontSize = 13.sp, color = Dim, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
        }
    }
}

@Composable
private fun EqualizerBars(paused: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "eqp")
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(13.dp)) {
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
                    }.background(SpotifyGreen),
            )
        }
    }
}

@Composable
private fun AlbumCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = .1f),
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.width(140.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(8.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF282828)),
            )
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(text = subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .6f), maxLines = 1)
        }
    }
}

private fun minSec(ticks: Long): String {
    val s = (ticks / 10_000_000.0).roundToInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun longDuration(ticks: Long): String {
    val s = (ticks / 10_000_000.0).roundToInt()
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "$h hr $m min" else "$m min ${s % 60} sec"
}

/** Average colour of the cover (colourful pixels count more), darkened and calmed like Spotify. */
private fun coverTone(bmp: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var w = 0.0
    for (y in 0 until 24) {
        for (x in 0 until 24) {
            val p = small.getPixel(x, y)
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            val k = 1.0 + (max(pr, max(pg, pb)) - min(pr, min(pg, pb))) / 32.0
            r += pr * k
            g += pg * k
            b += pb * k
            w += k
        }
    }
    val rf = (r / w / 255).toFloat()
    val gf = (g / w / 255).toFloat()
    val bf = (b / w / 255).toFloat()
    val mx = max(rf, max(gf, bf))
    val mn = min(rf, min(gf, bf))
    val l = (mx + mn) / 2
    val d = mx - mn
    var h = 0f
    var s = 0f
    if (d > 0f) {
        s = d / (1 - abs(2 * l - 1))
        h =
            when (mx) {
                rf -> ((gf - bf) / d) % 6
                gf -> (bf - rf) / d + 2
                else -> (rf - gf) / d + 4
            } * 60
        if (h < 0) h += 360
    }
    // same rounding as the web page
    val hh = h.roundToInt().toFloat()
    val ss = (min(s, .55f) * 100).roundToInt() / 100f
    val ll = (max(.16f, min(l, .3f)) * 100).roundToInt() / 100f
    return Color.hsl(hh % 360, ss.coerceIn(0f, 1f), ll)
}
