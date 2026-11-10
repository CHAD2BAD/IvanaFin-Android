package com.github.damontecres.wholphin.ui.detail.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
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
import kotlin.math.roundToInt

/*
 * IvanaFin: a music album in the Spotify TV look (same as the web/Samsung music-page.js):
 *   the cover large top-right fading into the dark, "Album", the big title,
 *   "(artist photo) Artist • 2010 • 14 songs, 1 hr 14 min"
 *   white Play (Pause while this album plays), Shuffle, Favourite, More
 *   # | Title (artists under it) | duration, the playing track green with moving bars
 *   then "More by <artist>"
 * Playing, queueing and the More menu are Wholphin's own (AlbumViewModel / MusicService).
 */

private const val ROW_BUTTONS = 0
private const val ROW_SONGS = 1
private const val ROW_MORE = 2

@Composable
fun IvanaFinAlbumPage(
    itemId: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    initialSongId: UUID? = null,
    // IvanaFin music layout: other albums ("More by") open inside Music too
    onOpenAlbum: ((UUID) -> Unit)? = null,
    requestInitialFocus: Boolean = true,
    onScrolled: ((Boolean) -> Unit)? = null,
    viewModel: AlbumViewModel =
        hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(
            key = "album-$itemId",
            creationCallback = { it.create(itemId, initialSongId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
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
        LoadingState.Loading, LoadingState.Pending -> LoadingPage(modifier.background(MuBg))
        LoadingState.Success -> {
            val album = state.album!!
            val songs = state.songs
            val api = viewModel.api

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
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
                    .collect { onScrolled?.invoke(it) }
            }
            // Back from further down the page: first back to the top (Play)
            BackHandler(listState.firstVisibleItemIndex > 2) {
                scope.launch {
                    listState.animateScrollToItem(0)
                    playFocus.tryRequestFocus()
                }
            }

            Box(modifier = modifier.fillMaxSize().background(MuBg)) {
                HeroArt(
                    url = api.imageApi.getItemImageUrl(itemId = album.id, imageType = ImageType.PRIMARY, maxWidth = 1000),
                    liked = false,
                    scrollPx = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ---- header ----
                    item("header") {
                        val count = songs.size
                        val bits =
                            listOfNotNull(
                                album.data.albumArtist ?: artist?.name,
                                album.data.productionYear?.toString(),
                                songsLabel(count) + if (totalTicks > 0) ", ${longDuration(totalTicks)}" else "",
                            )
                        HeroText(
                            kind = "Album",
                            title = album.name ?: "",
                            meta = bits.joinToString(" • "),
                            avatarUrl = artist?.let { api.imageApi.getItemImageUrl(itemId = it.id, imageType = ImageType.PRIMARY, maxWidth = 48) },
                        )
                    }
                    // ---- Play / Shuffle / Favourite / More ----
                    item("actions") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier =
                                Modifier
                                    .padding(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 10.dp)
                                    .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                        ) {
                            val pauseShown = albumPlaying && isPlayingNow
                            MuPill(
                                glyph = if (pauseShown) Fa.PAUSE else Fa.PLAY,
                                text = if (pauseShown) "Pause" else "Play",
                                primary = true,
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
                            MuPill(glyph = Fa.SHUFFLE, text = "Shuffle", onClick = {
                                position = RowColumn(ROW_BUTTONS, 1)
                                viewModel.play(true, 0)
                            })
                            MuPill(
                                glyph = Fa.HEART,
                                tint = if (album.favorite) MuGreen else Color.White,
                                onClick = { viewModel.setFavorite(album.id, !album.favorite) },
                            )
                            MuPill(glyph = Fa.MORE, onClick = { menuFor(album, 0, false) })
                        }
                    }
                    // ---- tracks ----
                    itemsIndexed(songs) { index, song ->
                        val disc = song?.data?.parentIndexNumber
                        val prevDisc = if (index > 0) songs.getOrNull(index - 1)?.data?.parentIndexNumber else null
                        Column {
                            if (multiDisc && disc != null && (index == 0 || disc != prevDisc)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.height(34.dp).padding(start = 44.dp, top = 8.dp),
                                ) {
                                    Text(Fa.DISC, fontFamily = FontAwesome, color = MuDim, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                                    Text("Disc $disc", color = MuDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            MuTrackRow(
                                number = (song?.data?.indexNumber ?: (index + 1)).toString(),
                                title = song?.name ?: "",
                                sub = song?.data?.artists?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: album.data.albumArtist ?: "",
                                duration = song?.data?.runTimeTicks?.let { minSecTicks(it) } ?: "",
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
                    // ---- More by ----
                    if (moreBy.isNotEmpty()) {
                        item("more") {
                            Column {
                                MuSectionTitle("More by " + (album.data.albumArtist ?: artist?.name ?: ""))
                                MuLazyRow() {
                                    itemsIndexed(moreBy) { i, a ->
                                        CoverCard(
                                            name = a.name ?: "",
                                            imageUrl = api.imageApi.getItemImageUrl(itemId = a.id, imageType = ImageType.PRIMARY, maxWidth = 240),
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

private fun longDuration(ticks: Long): String {
    val s = (ticks / 10_000_000.0).roundToInt()
    val h = s / 3600
    val m = s % 3600 / 60
    return if (h > 0) "$h hr $m min" else "$m min ${s % 60} sec"
}
