package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
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
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * IvanaFin: the Music library laid out like Spotify's TV app (same as the web/Samsung
 * music-page.js; pictures in jellyfin-web-custom/design-refs):
 *   top bar: now-playing pill (opens Now Playing) | Home / Search / Your Library | Welcome <name>
 *   Home: the selected card's picture large top-right, its name + "Artist • Album • N songs";
 *         2 x 4 shortcuts (Liked Songs + recently played albums); Recently added / Albums / Artists
 *   Album (IvanaFinAlbumPage), Liked Songs and Artist pages in the same style; Your Library grid; Search
 *   Now Playing: full-screen picture, playing from album, cover + title, progress, controls,
 *         About the artist
 * Music only plays while you're in here: leaving the Music screen stops it.
 * Back: Now Playing -> page -> previous page -> Music Home -> out of Music.
 */

private sealed interface MusicView {
    data object Home : MusicView

    data object Library : MusicView

    data object Search : MusicView

    data object Liked : MusicView

    data class Album(
        val id: UUID,
    ) : MusicView

    data class Artist(
        val id: UUID,
    ) : MusicView
}

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

data class MusicHomeData(
    val liked: Int,
    val recent: List<BaseItem>,
    val added: List<BaseItem>,
    val all: List<BaseItem>,
    val artists: List<BaseItem>,
)

data class MusicSearchResults(
    val songs: List<BaseItem> = emptyList(),
    val albums: List<BaseItem> = emptyList(),
    val artists: List<BaseItem> = emptyList(),
)

data class MusicArtistData(
    val artist: BaseItem,
    /** newest first */
    val albums: List<BaseItem>,
    /** Popular songs (server ProviderIds.IvfPopular, from Deezer via jellyfin-web-custom/backup/music-artist-data.ps1) */
    val popular: List<BaseItem> = emptyList(),
    val songCount: Int = 0,
)

/** Discography row of an album: server ProviderIds.IvfType (album/compile/ep/single/mixtape), unknown = album */
internal fun discographyRow(album: BaseItem): Int =
    when (album.data.providerIds?.get("IvfType")) {
        "ep", "single" -> 1
        "mixtape" -> 2
        else -> 0
    }

internal val DiscographyTitles = listOf("Albums", "EPs & Singles", "Mixtapes & More")

@HiltViewModel
class IvanaFinMusicViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val api: ApiClient,
        private val serverRepository: ServerRepository,
        val musicService: MusicService,
        private val favoriteWatchManager: FavoriteWatchManager,
        @param:DefaultCoroutineScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("ivanafin", Context.MODE_PRIVATE)
        private var libraryId: UUID? = null
        val userId: UUID? get() = serverRepository.currentUser?.id
        val userName: String get() = serverRepository.currentUser?.name ?: ""

        val home = MutableStateFlow<MusicHomeData?>(null)
        val library = MutableStateFlow<List<BaseItem>>(emptyList())
        val libraryArtists = MutableStateFlow(prefs.getBoolean("music_lib_artists", false))
        val sort = MutableStateFlow(prefs.getInt("music_sort", 0).coerceIn(0, MusicSorts.size - 1))
        val search = MutableStateFlow(MusicSearchResults())
        val liked = MutableStateFlow<List<BaseItem>?>(null)
        val artist = MutableStateFlow<MusicArtistData?>(null)

        /** The song that's playing: is it liked, and its artist (for Now Playing) */
        val nowFav = MutableStateFlow(false)
        val nowArtist = MutableStateFlow<BaseItem?>(null)

        private suspend fun request(r: GetItemsRequest) =
            GetItemsRequestHandler.execute(api, r.copy(userId = userId))

        private fun albumsRequest(
            sortBy: List<ItemSortBy>,
            order: SortOrder,
            limit: Int? = null,
            searchTerm: String? = null,
        ) = GetItemsRequest(
            parentId = libraryId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            sortBy = sortBy,
            sortOrder = listOf(order),
            limit = limit,
            searchTerm = searchTerm,
            fields = listOf(ItemFields.CHILD_COUNT),
            enableImageTypes = listOf(ImageType.PRIMARY),
            imageTypeLimit = 1,
        )

        fun load(library: UUID?) {
            viewModelScope.launchIO {
                libraryId = library ?: findMusicLibrary()
                loadHome()
            }
        }

        private suspend fun findMusicLibrary(): UUID? =
            try {
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .firstOrNull { it.collectionType == CollectionType.MUSIC }
                    ?.id
            } catch (ex: Exception) {
                Timber.w(ex, "No music library")
                null
            }

        private suspend fun loadArtists(searchTerm: String? = null): List<BaseItem> =
            try {
                val r =
                    if (searchTerm == null) {
                        api.artistsApi.getAlbumArtists(
                            parentId = libraryId,
                            userId = userId,
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            imageTypeLimit = 1,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.BACKDROP),
                        )
                    } else {
                        api.artistsApi.getArtists(
                            parentId = libraryId,
                            userId = userId,
                            searchTerm = searchTerm,
                            limit = 20,
                            imageTypeLimit = 1,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.BACKDROP),
                        )
                    }
                r.content.items.map { BaseItem.from(it, api, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Loading artists failed")
                emptyList()
            }

        private suspend fun loadHome() {
            val id = libraryId ?: return
            try {
                val likedCount =
                    request(
                        GetItemsRequest(
                            parentId = id,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            recursive = true,
                            filters = listOf(ItemFilter.IS_FAVORITE),
                            limit = 1,
                            enableTotalRecordCount = true,
                        ),
                    ).content.totalRecordCount
                val played =
                    request(
                        GetItemsRequest(
                            parentId = id,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            recursive = true,
                            filters = listOf(ItemFilter.IS_PLAYED),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 300,
                            enableImages = false,
                        ),
                    ).content.items
                val added = request(albumsRequest(listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME), SortOrder.DESCENDING, 24)).toBaseItems(api, false)
                val all = request(albumsRequest(listOf(ItemSortBy.SORT_NAME), SortOrder.ASCENDING)).toBaseItems(api, false)
                val byId = all.associateBy { it.id }
                val recent = mutableListOf<BaseItem>()
                played.mapNotNull { it.albumId }.distinct().forEach { a -> if (recent.size < 8) byId[a]?.let { recent.add(it) } }
                added.forEach { a -> if (recent.size < 8 && recent.none { it.id == a.id }) recent.add(a) }
                home.value = MusicHomeData(likedCount, recent, added, all, loadArtists())
            } catch (ex: Exception) {
                Timber.w(ex, "Loading music home failed")
            }
        }

        fun loadLibrary() {
            viewModelScope.launchIO {
                library.value =
                    if (libraryArtists.value) {
                        home.value?.artists ?: loadArtists()
                    } else {
                        val s = MusicSorts[sort.value]
                        try {
                            request(albumsRequest(s.by, s.order)).toBaseItems(api, false)
                        } catch (ex: Exception) {
                            Timber.w(ex, "Loading albums failed")
                            emptyList()
                        }
                    }
            }
        }

        fun setLibraryArtists(on: Boolean) {
            libraryArtists.value = on
            prefs.edit().putBoolean("music_lib_artists", on).apply()
            loadLibrary()
        }

        fun setSort(i: Int) {
            if (i !in MusicSorts.indices || i == sort.value) return
            sort.value = i
            prefs.edit().putInt("music_sort", sort.value).apply()
            loadLibrary()
        }

        private var searchJob: Job? = null

        fun search(term: String) {
            searchJob?.cancel()
            if (term.isBlank()) {
                search.value = MusicSearchResults()
                return
            }
            searchJob =
                viewModelScope.launchIO {
                    delay(350)
                    try {
                        val songs =
                            request(
                                GetItemsRequest(
                                    parentId = libraryId,
                                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                                    recursive = true,
                                    searchTerm = term,
                                    limit = 30,
                                ),
                            ).toBaseItems(api, false)
                        val albums = request(albumsRequest(listOf(ItemSortBy.SORT_NAME), SortOrder.ASCENDING, 30, term)).toBaseItems(api, false)
                        search.value = MusicSearchResults(songs, albums, loadArtists(term))
                    } catch (ex: Exception) {
                        Timber.w(ex, "Music search failed")
                    }
                }
        }

        fun loadLiked() {
            viewModelScope.launchIO {
                liked.value =
                    try {
                        request(
                            GetItemsRequest(
                                parentId = libraryId,
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                recursive = true,
                                filters = listOf(ItemFilter.IS_FAVORITE),
                                sortBy = listOf(ItemSortBy.ALBUM_ARTIST, ItemSortBy.ALBUM, ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
                            ),
                        ).toBaseItems(api, false)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Loading liked songs failed")
                        emptyList()
                    }
            }
        }

        fun loadArtist(id: UUID) {
            artist.value = null
            viewModelScope.launchIO {
                try {
                    val a = BaseItem.from(api.userLibraryApi.getItem(itemId = id).content, api, false)
                    val albums =
                        request(
                            GetItemsRequest(
                                albumArtistIds = listOf(id),
                                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                fields = listOf(ItemFields.CHILD_COUNT, ItemFields.PROVIDER_IDS),
                            ),
                        ).toBaseItems(api, false)
                    val songCount =
                        request(
                            GetItemsRequest(
                                albumArtistIds = listOf(id),
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                recursive = true,
                                limit = 0,
                                enableTotalRecordCount = true,
                            ),
                        ).content.totalRecordCount
                    val popularIds =
                        a.data.providerIds
                            ?.get("IvfPopular")
                            ?.split(',')
                            ?.mapNotNull { it.trim().toUUIDOrNull() }
                            .orEmpty()
                    val popular =
                        if (popularIds.isEmpty()) {
                            emptyList()
                        } else {
                            val found = request(GetItemsRequest(ids = popularIds, enableImages = false)).toBaseItems(api, false).associateBy { it.id }
                            popularIds.mapNotNull { found[it] } // keep Deezer's order
                        }
                    artist.value = MusicArtistData(a, albums, popular, songCount)
                } catch (ex: Exception) {
                    Timber.w(ex, "Loading artist failed")
                }
            }
        }

        /** Play a list from [start] (or shuffled) */
        fun playList(
            items: List<BaseItem>,
            start: Int,
            shuffled: Boolean,
        ) {
            if (items.isEmpty()) return
            viewModelScope.launchIO {
                val list = if (shuffled) items.shuffled() else items
                musicService.setQueue(list, false)
                if (!shuffled && start > 0) withContext(Dispatchers.Main) { musicService.player.seekTo(start, 0L) }
            }
        }

        /** An artist's songs, newest album first (a queue of at most 200) */
        fun playArtist(
            id: UUID,
            shuffled: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    val songs =
                        request(
                            GetItemsRequest(
                                albumArtistIds = listOf(id),
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                recursive = true,
                                limit = 200,
                                sortBy =
                                    if (shuffled) {
                                        listOf(ItemSortBy.RANDOM)
                                    } else {
                                        listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.ALBUM, ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER)
                                    },
                                sortOrder =
                                    if (shuffled) {
                                        listOf(SortOrder.ASCENDING)
                                    } else {
                                        listOf(SortOrder.DESCENDING, SortOrder.ASCENDING, SortOrder.ASCENDING, SortOrder.ASCENDING)
                                    },
                            ),
                        ).toBaseItems(api, false)
                    musicService.setQueue(songs, false)
                } catch (ex: Exception) {
                    Timber.w(ex, "Playing artist failed")
                }
            }
        }

        private var nowJob: Job? = null

        fun loadNow(item: AudioItem?) {
            nowJob?.cancel()
            if (item == null) return
            nowJob =
                viewModelScope.launchIO {
                    try {
                        val dto = api.userLibraryApi.getItem(itemId = item.id).content
                        nowFav.value = dto.userData?.isFavorite == true
                        val artistId = dto.albumArtists?.firstOrNull()?.id ?: item.artistId
                        if (artistId != null && nowArtist.value?.id != artistId) {
                            nowArtist.value = BaseItem.from(api.userLibraryApi.getItem(itemId = artistId).content, api, false)
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Loading now playing details failed")
                    }
                }
        }

        fun setFavorite(
            id: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    favoriteWatchManager.setFavorite(id, favorite)
                    nowFav.value = favorite
                    home.value = home.value?.let { it.copy(liked = max(0, it.liked + if (favorite) 1 else -1)) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Favourite failed")
                }
            }
        }

        fun imageUrl(
            id: UUID,
            type: ImageType = ImageType.PRIMARY,
            maxWidth: Int = 240,
        ) = api.imageApi.getItemImageUrl(itemId = id, imageType = type, imageIndex = if (type == ImageType.BACKDROP) 0 else null, maxWidth = maxWidth)

        /** An artist's wide picture: backdrop if it has one, else its photo */
        fun artistArt(
            a: BaseItem,
            maxWidth: Int = 1280,
        ) = if (!a.data.backdropImageTags.isNullOrEmpty()) imageUrl(a.id, ImageType.BACKDROP, maxWidth) else imageUrl(a.id, ImageType.PRIMARY, maxWidth)

        fun userImageUrl() = userId?.let { api.imageApi.getUserImageUrl(it) }

        /** Leaving Music: the music stops (it only plays while you're in Music). */
        fun stopMusic() {
            appScope.launch { musicService.stop() }
        }
    }

/** What the Home hero shows for the selected card */
private data class Hero(
    val title: String,
    val meta: String,
    val art: String?,
    val liked: Boolean = false,
    val avatar: String? = null,
    // album covers are square: shown whole, not cropped to a wide strip
    val square: Boolean = false,
)

@Composable
fun IvanaFinMusicLibrary(
    preferences: UserPreferences,
    libraryId: UUID?,
    initialAlbumId: UUID?,
    modifier: Modifier = Modifier,
    viewModel: IvanaFinMusicViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val stack =
        remember {
            mutableStateListOf<MusicView>(MusicView.Home).apply { if (initialAlbumId != null) add(MusicView.Album(initialAlbumId)) }
        }
    val current = stack.last()
    var npOpen by remember { mutableStateOf(false) }
    var scrolled by remember { mutableFloatStateOf(0f) }
    var toTop by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { viewModel.load(libraryId) }
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
    LaunchedEffect(nowItem?.id) { viewModel.loadNow(nowItem) }
    LaunchedEffect(nowItem == null) { if (nowItem == null) npOpen = false }
    // Starting a song (a new queue, or a jump to a song in a list) opens Now Playing; Back returns
    // to the page it was started from. A song ending and the next one starting does not.
    var npAuto by remember { mutableStateOf(false) }
    val lastClicked = remember { mutableStateOf<FocusRequester?>(null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    if (mediaItem == null) return
                    // show the new song now, not at the next 500 ms poll (a queue + jump to song N
                    // is two transitions: the second must replace the first straight away)
                    (mediaItem.localConfiguration?.tag as? AudioItem)?.let { nowItem = it }
                    posMs = 0L
                    durMs = player.duration.takeIf { it > 0 } ?: 0L
                    if (!npOpen &&
                        (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
                    ) {
                        playing = true
                        npAuto = true
                        npOpen = true
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Home keeps its place (scroll, rows, selected card) while you're in a page opened from it
    val homeList = rememberLazyListState()
    val homeRows = remember { HashMap<String, LazyListState>() }
    var homeFocus by remember { mutableStateOf<String?>(null) }
    var heroKey by remember { mutableStateOf("liked") }

    val miniFocus = remember { FocusRequester() }
    val navFocus = remember { FocusRequester() }
    val pageFocus = remember { FocusRequester() }
    // Your Library keeps its place (scroll + the card you opened) while you're in an album/artist
    val libGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var libFocus by remember { mutableStateOf<UUID?>(null) }
    fun go(
        v: MusicView,
        root: Boolean = false,
    ) {
        if (root) {
            stack.clear()
            stack.add(MusicView.Home)
            if (v != MusicView.Home) stack.add(v)
        } else {
            stack.add(v)
        }
        scrolled = 0f
    }
    fun open(k: String) {
        when {
            k == "liked" -> go(MusicView.Liked)
            k.startsWith("album:") -> go(MusicView.Album(UUID.fromString(k.removePrefix("album:"))))
            k.startsWith("artist:") -> go(MusicView.Artist(UUID.fromString(k.removePrefix("artist:"))))
        }
    }
    BackHandler(enabled = npOpen || stack.size > 1) {
        if (npOpen) {
            npOpen = false
            // opened from the top-left pill: back to the pill; opened by starting a song: back to
            // the song row (or Play/Shuffle button) that started it
            val auto = npAuto
            scope.launch {
                delay(50)
                if (!auto || lastClicked.value?.tryRequestFocus() != true) miniFocus.tryRequestFocus()
            }
            npAuto = false
        } else {
            stack.removeAt(stack.lastIndex)
            scrolled = 0f
        }
    }

    // Full screen width: the navigation container pads pages left/right
    val screenW = LocalWindowInfo.current.containerSize.width
    var leftPx by remember { mutableStateOf(0f) }
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { leftPx = it.positionInWindow().x }) {
        Box(
            modifier =
                Modifier
                    .layout { m, c ->
                        val w = if (screenW > 0) screenW else c.maxWidth
                        val p = m.measure(Constraints.fixed(w, c.maxHeight))
                        layout(c.maxWidth, c.maxHeight) { p.place(-leftPx.roundToInt(), 0) }
                    }.background(MuBg),
        ) {
            // pages scroll only when the selected item would leave the screen, and keep it clear of the top bar
            MuScroll(start = 100.dp) {
            CompositionLocalProvider(LocalMuToTop provides toTop) {
            val onScrolled: (Float) -> Unit = { scrolled = it }
            CompositionLocalProvider(LocalMuLastClicked provides lastClicked) {
            // the page as one focus group: Down from the top bar goes into it (see MusicTopBar onDown)
            Box(Modifier.fillMaxSize().focusRequester(pageFocus).focusRestorer().focusGroup()) {
            when (current) {
                MusicView.Home ->
                    MusicHomeView(
                        viewModel = viewModel,
                        listState = homeList,
                        rowState = { homeRows.getOrPut(it) { LazyListState() } },
                        restoreKey = homeFocus,
                        heroKey = heroKey,
                        nowAlbumId = nowItem?.albumId,
                        onFocusKey = {
                            homeFocus = it
                            heroKey = it
                        },
                        onOpen = ::open,
                        onScrolled = onScrolled,
                    )

                MusicView.Library ->
                    MusicLibraryView(
                        viewModel,
                        nowItem?.albumId,
                        onOpen = ::open,
                        onScrolled = onScrolled,
                        gridState = libGrid,
                        lastOpened = libFocus,
                        onOpened = { libFocus = it },
                    )

                MusicView.Search ->
                    MusicSearchView(viewModel, nowItem?.id, playing, onOpen = ::open, onScrolled = onScrolled, onUp = { navFocus.tryRequestFocus() })

                MusicView.Liked ->
                    MusicLikedView(viewModel, nowItem?.id, playing, onScrolled = onScrolled)

                is MusicView.Artist ->
                    key(current.id) {
                        MusicArtistView(viewModel, current.id, nowItem?.albumId, nowItem?.id, playing, onOpen = ::open, onScrolled = onScrolled)
                    }

                is MusicView.Album ->
                    key(current.id) {
                        IvanaFinAlbumPage(
                            itemId = current.id,
                            preferences = preferences,
                            onOpenAlbum = { go(MusicView.Album(it)) },
                            onOpenArtist = { go(MusicView.Artist(it)) },
                            onScrolled = onScrolled,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }
            }
            }
            MusicTopBar(
                viewModel = viewModel,
                current = current,
                rootView = if (current == MusicView.Library || current == MusicView.Search) current else stack.getOrNull(1)?.takeIf { it == MusicView.Library || it == MusicView.Search } ?: MusicView.Home,
                scrolled = scrolled,
                onTopFocus = { if (scrolled > 0f) toTop++ },
                nowItem = nowItem,
                playing = playing,
                miniFocus = miniFocus,
                navFocus = navFocus,
                onMini = {
                    npAuto = false
                    npOpen = true
                },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onNav = { go(it, root = true) },
                // Down from the bar into the page (the page is a focus group: it returns to what was
                // selected there, else its first item) - plain focus search found nothing below the bar
                onDown = { pageFocus.tryRequestFocus() },
            )
            if (npOpen && nowItem != null) {
                NowPlayingView(
                    viewModel = viewModel,
                    item = nowItem!!,
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
                    onSeek = { player.seekTo((player.currentPosition + it).coerceIn(0L, max(0L, player.duration - 500))) },
                    // About the artist / the artist's name: close Now Playing, open the artist's page
                    // (Back from there returns to the page underneath, like on the web)
                    onArtist = {
                        npOpen = false
                        npAuto = false
                        go(MusicView.Artist(it))
                    },
                    modifier = Modifier.zIndex(10f),
                )
            }
            }
            }
        }
    }
}

// ---- top bar ---------------------------------------------------------------------------------------

@Composable
private fun MusicTopBar(
    viewModel: IvanaFinMusicViewModel,
    current: MusicView,
    rootView: MusicView,
    scrolled: Float,
    onTopFocus: () -> Unit,
    nowItem: AudioItem?,
    playing: Boolean,
    miniFocus: FocusRequester,
    navFocus: FocusRequester,
    onMini: () -> Unit,
    onPlayPause: () -> Unit,
    onNav: (MusicView) -> Unit,
    onDown: () -> Boolean,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            // the tabs stay at their place on the page and scroll away with it; selecting one scrolls back up
            .offset { IntOffset(0, -min(scrolled, 4000f).roundToInt()) }
            .onFocusChanged { if (it.hasFocus) onTopFocus() }
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) onDown() else false },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 34.dp),
        ) {
            // now playing pill (tinted from the cover)
            if (nowItem != null) {
                var tint by remember { mutableStateOf(MuCard) }
                LaunchedEffect(nowItem.imageUrl) {
                    val url = nowItem.imageUrl ?: return@LaunchedEffect
                    coverBitmap(context, url)?.let { tint = coverTint(it) }
                }
                Surface(
                    onClick = onMini,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = tint, focusedContainerColor = tint),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.width(200.dp).height(36.dp).focusRequester(miniFocus),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = nowItem.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).background(Color(0xFF333333)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(nowItem.title ?: "", fontSize = 11.sp, lineHeight = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nowItem.artistNames ?: "", fontSize = 8.5.sp, lineHeight = 11.sp, color = MuDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (playing) Fa.PAUSE else Fa.PLAY, fontFamily = FontAwesome, fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            } else {
                Spacer(Modifier.width(200.dp))
            }
            // Home / Search / Your Library
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.weight(1f),
            ) {
                NavPill(Fa.HOME, "Home", rootView == MusicView.Home, { onNav(MusicView.Home) }, if (rootView == MusicView.Home) Modifier.focusRequester(navFocus) else Modifier)
                NavPill(Fa.SEARCH, "Search", rootView == MusicView.Search, { onNav(MusicView.Search) }, if (rootView == MusicView.Search) Modifier.focusRequester(navFocus) else Modifier)
                NavPill(Fa.MUSIC, "Your Library", rootView == MusicView.Library, { onNav(MusicView.Library) }, if (rootView == MusicView.Library) Modifier.focusRequester(navFocus) else Modifier)
            }
            // as wide as the now-playing pill, so Home / Search / Your Library stay centred
            Spacer(Modifier.width(200.dp))
        }
    }
}

@Composable
private fun NavPill(
    glyph: String,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val c = if (focused) Color.Black else if (on) Color.White else Color.White.copy(alpha = .6f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp)) {
            Text(glyph, fontFamily = FontAwesome, fontSize = 14.sp, color = c)
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c)
        }
    }
}

// ---- Home ------------------------------------------------------------------------------------------

@Composable
private fun MusicHomeView(
    viewModel: IvanaFinMusicViewModel,
    listState: LazyListState,
    rowState: (String) -> LazyListState,
    restoreKey: String?,
    heroKey: String,
    nowAlbumId: UUID?,
    onFocusKey: (String) -> Unit,
    onOpen: (String) -> Unit,
    onScrolled: (Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val home by viewModel.home.collectAsState()
    val data = home ?: return
    val focusFirst = remember { FocusRequester() }
    val focusRestore = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        if (restoreKey == null || !focusRestore.tryRequestFocus()) focusFirst.tryRequestFocus()
    }
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f }.collect { onScrolled(it) }
    }
    val toTop = LocalMuToTop.current
    val toTopStart = remember { toTop }
    LaunchedEffect(toTop) { if (toTop != toTopStart) listState.scrollToItem(0) }
    // the hero follows the selected card (a moment later, so quick scrolling doesn't redraw it every step)
    var shownKey by remember { mutableStateOf(heroKey) }
    LaunchedEffect(heroKey) {
        delay(140)
        shownKey = heroKey
    }
    val hero = remember(shownKey, data) { heroFor(viewModel, data, shownKey) }
    val firstKey = data.recent.firstOrNull()?.let { "album:${it.id}" }
    fun keyMod(k: String, shortcut: Boolean = false): Modifier =
        when {
            k == restoreKey -> Modifier.focusRequester(focusRestore)
            // (only on the shortcut: the same album also sits in the rows below, and selecting that
            // one opened Music Home scrolled down)
            shortcut && k == firstKey -> Modifier.focusRequester(focusFirst)
            else -> Modifier
        }

    Box(Modifier.fillMaxSize()) {
        HeroArt(
            url = hero.art,
            liked = hero.liked,
            square = hero.square,
            scrollPx = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
            item("hero") {
                HeroText(title = hero.title, meta = hero.meta, avatarUrl = hero.avatar, likedAvatar = hero.liked)
            }
            item("shortcuts") {
                val cards: List<BaseItem?> = data.recent
                // on the shortcuts the whole hero stays in view
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier =
                        Modifier
                            .padding(start = 40.dp, end = 40.dp, top = 2.dp)
                            .onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) } },
                ) {
                    cards.chunked(4).forEach { rowCards ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            rowCards.forEach { a ->
                                val k = if (a == null) "liked" else "album:${a.id}"
                                ShortcutCard(
                                    name = a?.name ?: "Liked Songs",
                                    imageUrl = a?.let { viewModel.imageUrl(it.id, maxWidth = 120) },
                                    liked = a == null,
                                    playing = a != null && a.id == nowAlbumId,
                                    onClick = { onOpen(k) },
                                    onFocus = {
                                        onFocusKey(k)
                                    },
                                    modifier = Modifier.weight(1f).then(keyMod(k, shortcut = true)),
                                )
                            }
                            repeat(4 - rowCards.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            fun albumRow(
                id: String,
                title: String,
                albums: List<BaseItem>,
            ) {
                if (albums.isEmpty()) return
                item(id) {
                    Column {
                        MuSectionTitle(title)
                        MuLazyRow(state = rowState(id)) {
                            itemsIndexed(albums, key = { _, a -> a.id }) { _, a ->
                                val k = "album:${a.id}"
                                CoverCard(
                                    name = a.name ?: "",
                                    imageUrl = viewModel.imageUrl(a.id),
                                    playing = a.id == nowAlbumId,
                                    onClick = { onOpen(k) },
                                    onFocus = { onFocusKey(k) },
                                    modifier = keyMod(k),
                                )
                            }
                        }
                    }
                }
            }
            albumRow("added", "Recently added", data.added)
            albumRow("albums", "Albums", data.all)
            if (data.artists.isNotEmpty()) {
                item("artists") {
                    Column {
                        MuSectionTitle("Artists")
                        MuLazyRow(state = rowState("artists")) {
                            itemsIndexed(data.artists, key = { _, a -> a.id }) { _, a ->
                                val k = "artist:${a.id}"
                                CoverCard(
                                    name = a.name ?: "",
                                    imageUrl = viewModel.imageUrl(a.id),
                                    round = true,
                                    onClick = { onOpen(k) },
                                    onFocus = { onFocusKey(k) },
                                    modifier = keyMod(k),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun heroFor(
    vm: IvanaFinMusicViewModel,
    data: MusicHomeData,
    key: String,
): Hero {
    if (key.startsWith("artist:")) {
        data.artists.firstOrNull { "artist:${it.id}" == key }?.let {
            return Hero(it.name ?: "", "Artist", vm.artistArt(it), avatar = vm.imageUrl(it.id, maxWidth = 48))
        }
    }
    // an album (the first shortcut when nothing is selected yet)
    run {
        ((data.recent + data.added + data.all).firstOrNull { "album:${it.id}" == key } ?: data.recent.firstOrNull())?.let { a ->
            val artist = a.data.albumArtists?.firstOrNull()
            val bits = listOfNotNull(a.data.albumArtist ?: artist?.name, "Album", a.data.childCount?.takeIf { it > 0 }?.let { songsLabel(it) })
            return Hero(a.name ?: "", bits.joinToString(" • "), vm.imageUrl(a.id, maxWidth = 2000), avatar = artist?.let { vm.imageUrl(it.id, maxWidth = 48) }, square = true)
        }
    }
    return Hero("Music", "", null)
}

// ---- Your Library ----------------------------------------------------------------------------------

@Composable
private fun MusicLibraryView(
    viewModel: IvanaFinMusicViewModel,
    nowAlbumId: UUID?,
    onOpen: (String) -> Unit,
    onScrolled: (Float) -> Unit,
    // kept by the caller so Back from an album/artist returns to that card, scrolled as it was
    gridState: LazyGridState,
    lastOpened: UUID?,
    onOpened: (UUID) -> Unit,
) {
    val items by viewModel.library.collectAsState()
    val artists by viewModel.libraryArtists.collectAsState()
    val sortIdx by viewModel.sort.collectAsState()
    val first = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
        delay(80)
        // the card you opened, else the selected chip (Albums or Artists)
        if (lastOpened == null || !back.tryRequestFocus()) first.tryRequestFocus()
    }
    LaunchedEffect(gridState) {
        snapshotFlow { if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset.toFloat() else 100000f }.collect { onScrolled(it) }
    }
    val toTop = LocalMuToTop.current
    val toTopStart = remember { toTop }
    LaunchedEffect(toTop) { if (toTop != toTopStart) gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 110.dp, bottom = 0.dp),
            ) {
                Text("Your Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(end = 12.dp))
                MuChip("Albums", on = !artists, onClick = { viewModel.setLibraryArtists(false) }, modifier = if (!artists) Modifier.focusRequester(first) else Modifier)
                MuChip("Artists", on = artists, onClick = { viewModel.setLibraryArtists(true) }, modifier = if (artists) Modifier.focusRequester(first) else Modifier)
                Spacer(Modifier.weight(1f))
                if (!artists) SortChip(sortIdx, onPick = { viewModel.setSort(it) })
            }
        }
        itemsIndexed(items, key = { _, a -> a.id }) { _, a ->
            val mod = if (a.id == lastOpened) Modifier.focusRequester(back) else Modifier
            if (artists) {
                CoverCard(
                    name = a.name ?: "",
                    imageUrl = viewModel.imageUrl(a.id),
                    round = true,
                    sub = "Artist",
                    width = null,
                    onClick = {
                        onOpened(a.id)
                        onOpen("artist:${a.id}")
                    },
                    modifier = mod,
                )
            } else {
                CoverCard(
                    name = a.name ?: "",
                    imageUrl = viewModel.imageUrl(a.id),
                    sub = listOfNotNull(a.data.productionYear?.toString(), a.data.albumArtist ?: "Album").joinToString(" • "),
                    playing = a.id == nowAlbumId,
                    width = null,
                    onClick = {
                        onOpened(a.id)
                        onOpen("album:${a.id}")
                    },
                    modifier = mod,
                )
            }
        }
    }
}

/** The sort chip: opens a "Sort by" list under it (current order ticked, Back closes it). */
@Composable
private fun SortChip(
    sortIdx: Int,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val chipFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    fun close() {
        open = false
        scope.launch {
            delay(50)
            chipFocus.tryRequestFocus()
        }
    }
    Box {
        MuChip(MusicSorts[sortIdx].label, glyph = Fa.SORT, on = false, onClick = { open = true }, modifier = Modifier.focusRequester(chipFocus))
        if (open) {
            val dy = with(LocalDensity.current) { 34.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, dy),
                onDismissRequest = { close() },
                properties = PopupProperties(focusable = true),
            ) {
                val curFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(30)
                    curFocus.tryRequestFocus()
                }
                Column(
                    Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF282828))
                        .padding(6.dp),
                ) {
                    Text(
                        "SORT BY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .45.sp,
                        color = Color.White.copy(alpha = .6f),
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
                    )
                    MusicSorts.forEachIndexed { i, s ->
                        val on = i == sortIdx
                        Surface(
                            onClick = {
                                onPick(i)
                                close()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = .12f),
                                ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            modifier = Modifier.fillMaxWidth().then(if (on) Modifier.focusRequester(curFocus) else Modifier),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                val c = if (on) MuGreen else Color.White
                                Text(s.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c, modifier = Modifier.weight(1f))
                                if (on) Text(Fa.CHECK, fontFamily = FontAwesome, fontSize = 11.sp, color = c)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (on) Color.White else Color(0xFF2A2A2A),
                focusedContainerColor = if (on) Color.White else Color(0xFF3A3A3A),
            ),
        // the selected chip is white: a ring right on it vanished, so it gets one drawn further out
        // (below) with a dark gap between them
        border = ClickableSurfaceDefaults.border(focusedBorder = if (on) Border.None else Border(BorderStroke(2.dp, Color.White), inset = (-3).dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .height(26.dp)
                .onFocusChanged { focused = it.hasFocus || it.isFocused }
                .drawWithContent {
                    drawContent()
                    if (on && focused) {
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
            val c = if (on) Color.Black else Color.White
            if (glyph != null) {
                Text(glyph, fontFamily = FontAwesome, fontSize = 11.sp, color = c)
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c)
        }
    }
}

// ---- Search ----------------------------------------------------------------------------------------

@Composable
private fun MusicSearchView(
    viewModel: IvanaFinMusicViewModel,
    nowId: UUID?,
    playing: Boolean,
    onOpen: (String) -> Unit,
    onScrolled: (Float) -> Unit,
    onUp: () -> Boolean,
) {
    val results by viewModel.search.collectAsState()
    var text by remember { mutableStateOf("") }
    val boxFocus = remember { FocusRequester() }
    // Down from the search box goes to the first result (the text field kept Down to itself)
    val firstResult = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        delay(80)
        boxFocus.tryRequestFocus()
    }
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f }.collect { onScrolled(it) }
    }
    val toTop = LocalMuToTop.current
    val toTopStart = remember { toTop }
    LaunchedEffect(toTop) { if (toTop != toTopStart) listState.scrollToItem(0) }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
        item("box") {
            EditTextBox(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.search(it)
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(text) }),
                leadingIcon = { Text(Fa.SEARCH, fontFamily = FontAwesome, fontSize = 16.sp, color = MuDim) },
                modifier =
                    Modifier
                        .padding(start = 40.dp, top = 110.dp, bottom = 6.dp)
                        .width(480.dp)
                        .focusRequester(boxFocus)
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) {
                                false
                            } else if (it.key == Key.DirectionDown) {
                                firstResult.tryRequestFocus()
                            } else if (it.key == Key.DirectionUp) {
                                onUp() // Up from the box goes to the top bar (the text field kept it too)
                            } else {
                                false
                            }
                        },
            )
        }
        if (results.songs.isNotEmpty()) {
            item("songsTitle") { MuSectionTitle("Songs") }
            itemsIndexed(results.songs, key = { _, s -> "s" + s.id }) { i, s ->
                MuTrackRow(
                    modifier = if (i == 0) Modifier.focusRequester(firstResult) else Modifier,
                    number = (i + 1).toString(),
                    title = s.name ?: "",
                    sub = listOfNotNull(s.data.artists?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: s.data.albumArtist, s.data.album).joinToString(" • "),
                    duration = s.data.runTimeTicks?.let { minSecTicks(it) } ?: "",
                    playing = s.id == nowId,
                    paused = !playing,
                    onClick = {
                        if (s.id == nowId) {
                            val p = viewModel.musicService.player
                            if (p.isPlaying) p.pause() else p.play()
                        } else {
                            viewModel.playList(listOf(s), 0, false)
                        }
                    },
                )
            }
        }
        if (results.albums.isNotEmpty()) {
            item("albums") {
                Column {
                    MuSectionTitle("Albums")
                    MuLazyRow() {
                        itemsIndexed(results.albums, key = { _, a -> a.id }) { i, a ->
                            CoverCard(
                                name = a.name ?: "",
                                imageUrl = viewModel.imageUrl(a.id),
                                onClick = { onOpen("album:${a.id}") },
                                modifier = if (i == 0 && results.songs.isEmpty()) Modifier.focusRequester(firstResult) else Modifier,
                            )
                        }
                    }
                }
            }
        }
        if (results.artists.isNotEmpty()) {
            item("artists") {
                Column {
                    MuSectionTitle("Artists")
                    MuLazyRow() {
                        itemsIndexed(results.artists, key = { _, a -> a.id }) { _, a ->
                            CoverCard(name = a.name ?: "", imageUrl = viewModel.imageUrl(a.id), round = true, onClick = { onOpen("artist:${a.id}") })
                        }
                    }
                }
            }
        }
    }
}

// ---- Liked Songs -----------------------------------------------------------------------------------

@Composable
private fun MusicLikedView(
    viewModel: IvanaFinMusicViewModel,
    nowId: UUID?,
    playing: Boolean,
    onScrolled: (Float) -> Unit,
) {
    val songs by viewModel.liked.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadLiked() }
    val list = songs ?: return
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        playFocus.tryRequestFocus()
    }
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f }.collect { onScrolled(it) }
    }
    val toTop = LocalMuToTop.current
    val toTopStart = remember { toTop }
    LaunchedEffect(toTop) { if (toTop != toTopStart) listState.scrollToItem(0) }
    val listPlaying = list.any { it.id == nowId }
    Box(Modifier.fillMaxSize()) {
        HeroArt(
            url = null,
            liked = true,
            scrollPx = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
            item("hero") { HeroText(kind = "Playlist", title = "Liked Songs", meta = "Playlist • " + songsLabel(list.size), likedAvatar = true) }
            item("acts") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 40.dp, top = 16.dp, bottom = 10.dp)) {
                    val pause = listPlaying && playing
                    MuPill(
                        glyph = if (pause) Fa.PAUSE else Fa.PLAY,
                        text = if (pause) "Pause" else "Play",
                        primary = true,
                        onClick = {
                            if (listPlaying) {
                                val p = viewModel.musicService.player
                                if (p.isPlaying) p.pause() else p.play()
                            } else {
                                viewModel.playList(list, 0, false)
                            }
                        },
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    MuPill(glyph = Fa.SHUFFLE, text = "Shuffle", onClick = { viewModel.playList(list, 0, true) })
                }
            }
            if (list.isEmpty()) {
                item("empty") {
                    Text(
                        "Songs you like will appear here. Select the heart on Now Playing.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = .6f),
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                    )
                }
            }
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                MuTrackRow(
                    number = (i + 1).toString(),
                    title = s.name ?: "",
                    sub = listOfNotNull(s.data.artists?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: s.data.albumArtist, s.data.album).joinToString(" • "),
                    duration = s.data.runTimeTicks?.let { minSecTicks(it) } ?: "",
                    playing = s.id == nowId,
                    paused = !playing,
                    onClick = {
                        if (s.id == nowId) {
                            val p = viewModel.musicService.player
                            if (p.isPlaying) p.pause() else p.play()
                        } else {
                            viewModel.playList(list, i, false)
                        }
                    },
                )
            }
        }
    }
}

// ---- Artist ----------------------------------------------------------------------------------------

@Composable
private fun MusicArtistView(
    viewModel: IvanaFinMusicViewModel,
    id: UUID,
    nowAlbumId: UUID?,
    nowId: UUID?,
    playing: Boolean,
    onOpen: (String) -> Unit,
    onScrolled: (Float) -> Unit,
) {
    val state by viewModel.artist.collectAsState()
    LaunchedEffect(id) { viewModel.loadArtist(id) }
    var popularAll by remember(id) { mutableStateOf(false) }
    var aboutOpen by remember(id) { mutableStateOf(false) }
    val data = state?.takeIf { it.artist.id == id } ?: return
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        playFocus.tryRequestFocus()
    }
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f }.collect { onScrolled(it) }
    }
    val toTop = LocalMuToTop.current
    val toTopStart = remember { toTop }
    LaunchedEffect(toTop) { if (toTop != toTopStart) listState.scrollToItem(0) }
    val a = data.artist
    Box(Modifier.fillMaxSize()) {
        HeroArt(
            url = viewModel.artistArt(a),
            liked = false,
            scrollPx = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 100000f },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
            item("hero") {
                val n = data.albums.size
                val meta =
                    listOfNotNull(
                        // ("Artist" is already the small label above the name)
                        "$n ${if (n == 1) "album" else "albums"}",
                        data.songCount.takeIf { it > 0 }?.let { songsLabel(it) },
                    ).joinToString(" • ")
                HeroText(kind = "Artist", title = a.name ?: "", meta = meta, avatarUrl = viewModel.imageUrl(a.id, maxWidth = 48))
            }
            item("acts") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 40.dp, top = 16.dp, bottom = 10.dp)) {
                    MuPill(glyph = Fa.PLAY, text = "Play", primary = true, onClick = { viewModel.playArtist(id, false) }, modifier = Modifier.focusRequester(playFocus))
                    MuPill(glyph = Fa.SHUFFLE, text = "Shuffle", onClick = { viewModel.playArtist(id, true) })
                }
            }
            // Popular: Deezer's top songs that are in the library; 5 shown, "See more" for the rest
            if (data.popular.isNotEmpty()) {
                item("popularTitle") { MuSectionTitle("Popular") }
                val shown = if (popularAll) data.popular else data.popular.take(5)
                itemsIndexed(shown, key = { _, s -> "p" + s.id }) { i, s ->
                    MuTrackRow(
                        number = (i + 1).toString(),
                        title = s.name ?: "",
                        sub = s.data.album ?: "",
                        duration = s.data.runTimeTicks?.let { minSecTicks(it) } ?: "",
                        playing = s.id == nowId,
                        paused = !playing,
                        onClick = {
                            if (s.id == nowId) {
                                val p = viewModel.musicService.player
                                if (p.isPlaying) p.pause() else p.play()
                            } else {
                                viewModel.playList(data.popular, data.popular.indexOf(s), false)
                            }
                        },
                    )
                }
                if (data.popular.size > 5) {
                    item("popularMore") {
                        MuPill(
                            glyph = if (popularAll) Fa.CHEVRON_UP else Fa.CHEVRON_DOWN,
                            text = if (popularAll) "Show less" else "See more",
                            onClick = { popularAll = !popularAll },
                            modifier = Modifier.padding(start = 40.dp, top = 6.dp),
                        )
                    }
                }
            }
            // Discography: Albums / EPs & Singles / Mixtapes & More (server ProviderIds.IvfType)
            val rows = data.albums.groupBy { discographyRow(it) }
            DiscographyTitles.forEachIndexed { ri, title ->
                val rowAlbums = rows[ri].orEmpty()
                if (rowAlbums.isEmpty()) return@forEachIndexed
                item("disc$ri") {
                    Column {
                        MuSectionTitle(title)
                        MuLazyRow {
                            itemsIndexed(rowAlbums, key = { _, al -> al.id }) { _, al ->
                                CoverCard(
                                    name = al.name ?: "",
                                    imageUrl = viewModel.imageUrl(al.id),
                                    sub = al.data.productionYear?.toString() ?: "",
                                    playing = al.id == nowAlbumId,
                                    onClick = { onOpen("album:${al.id}") },
                                )
                            }
                        }
                    }
                }
            }
            // About: the artist's photo + bio; select it to read the whole text
            a.data.overview?.takeIf { it.isNotBlank() }?.let { text ->
                item("about") {
                    Column(Modifier.padding(start = 40.dp, end = 40.dp, top = 26.dp)) {
                        Text("About", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
                        Surface(
                            onClick = { aboutOpen = !aboutOpen },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = MuCard, focusedContainerColor = MuCard),
                            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            modifier = Modifier.width(760.dp),
                        ) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                AsyncImage(
                                    model = viewModel.imageUrl(a.id, maxWidth = 400),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(170.dp).clip(RoundedCornerShape(6.dp)),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(a.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
                                    Text(
                                        text,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = Color.White.copy(alpha = .8f),
                                        maxLines = if (aboutOpen) Int.MAX_VALUE else 6,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!aboutOpen) Text("Read more", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Now Playing -----------------------------------------------------------------------------------

@Composable
private fun NowPlayingView(
    viewModel: IvanaFinMusicViewModel,
    item: AudioItem,
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
    onSeek: (Long) -> Unit,
    onArtist: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val fav by viewModel.nowFav.collectAsState()
    val artist by viewModel.nowArtist.collectAsState()
    val scroll = rememberScrollState()
    val ppFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        ppFocus.tryRequestFocus()
    }
    val bigCover = item.albumId?.let { viewModel.imageUrl(it, maxWidth = 400) } ?: item.imageUrl
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = artist?.let { viewModel.artistArt(it, 1920) } ?: bigCover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, -.5f),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xFF464646).copy(alpha = .55f),
                    .6f to Color(0xFF323232).copy(alpha = .6f),
                    1f to MuBg.copy(alpha = .92f),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 60.dp, end = 60.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 40.dp).height(32.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = .85f)), contentAlignment = Alignment.Center) {
                    Text(Fa.DISC, fontFamily = FontAwesome, fontSize = 14.sp, color = Color(0xFF555555))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("PLAYING FROM ALBUM", fontSize = 8.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = .75f))
                    Text(item.albumTitle ?: item.title ?: "", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(189.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(
                    model = bigCover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(87.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF333333)),
                )
                Column {
                    Text(item.title ?: "", fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // the artist's name opens the artist's page (like the About the artist card)
                    Surface(
                        onClick = { artist?.id?.let(onArtist) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.padding(top = 4.dp).offset(x = (-4).dp),
                    ) {
                        Text(item.artistNames ?: "", fontSize = 12.sp, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            // progress: Left/Right on it skip 10 seconds
            val barInteraction = remember { MutableInteractionSource() }
            val barFocused by barInteraction.collectIsFocusedAsState()
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier =
                    Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .height(14.dp)
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (it.key) {
                                Key.DirectionLeft -> {
                                    onSeek(-10_000)
                                    true
                                }

                                Key.DirectionRight -> {
                                    onSeek(10_000)
                                    true
                                }

                                else -> false
                            }
                        }.focusable(interactionSource = barInteraction),
            ) {
                val f = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(if (barFocused) 5.dp else 2.5.dp).clip(CircleShape).background(Color.White.copy(alpha = .3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(if (barFocused) MuGreen else Color.White))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(clock(posMs), fontSize = 10.sp, color = Color.White.copy(alpha = .85f))
                Spacer(Modifier.weight(1f))
                Text(if (durMs > 0) clock(durMs) else "-:--", fontSize = 10.sp, color = Color.White.copy(alpha = .85f))
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(53.dp).onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0) } }) {
                NpButton(Fa.HEART, active = fav, onClick = { viewModel.setFavorite(item.id, !fav) }, modifier = Modifier.align(Alignment.CenterStart))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282828).copy(alpha = .72f))
                            .padding(horizontal = 26.dp),
                ) {
                    NpButton(Fa.SHUFFLE, active = shuffle, dot = true, onClick = onShuffle)
                    NpButton(Fa.PREV, onClick = onPrev)
                    NpButton(if (playing) Fa.PAUSE else Fa.PLAY, big = true, onClick = onPlayPause, modifier = Modifier.focusRequester(ppFocus))
                    NpButton(Fa.NEXT, onClick = onNext)
                    NpButton(Fa.REPEAT, active = repeat != Player.REPEAT_MODE_OFF, dot = true, badge = if (repeat == Player.REPEAT_MODE_ONE) "1" else null, onClick = onRepeat)
                }
            }
            // About the artist
            Text("About the artist", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 34.dp, bottom = 10.dp))
            val a = artist
            Surface(
                onClick = { a?.id?.let(onArtist) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF333333), focusedContainerColor = Color(0xFF333333)),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier =
                    Modifier
                        .width(410.dp)
                        .height(250.dp)
                        .onFocusChanged { if (it.isFocused) scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (a != null) {
                        AsyncImage(
                            model = viewModel.imageUrl(a.id, maxWidth = 900),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = androidx.compose.ui.BiasAlignment(0f, -.6f),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.35f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                        Text(a?.name ?: item.artistNames ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        a?.data?.overview?.let {
                            Text(it, fontSize = 10.5.sp, lineHeight = 15.sp, color = Color.White.copy(alpha = .85f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NpButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    active: Boolean = false,
    dot: Boolean = false,
    badge: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (big) Color.White else Color.Transparent,
                focusedContainerColor = if (big) Color.White else Color.White.copy(alpha = .2f),
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White.copy(alpha = if (big) .45f else 0f)))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.size(34.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                glyph,
                fontFamily = FontAwesome,
                fontSize = if (big) 14.sp else 15.sp,
                color =
                    when {
                        big -> Color.Black
                        active -> MuGreen
                        focused -> Color.White
                        else -> Color.White.copy(alpha = .85f)
                    },
                modifier = if (glyph == Fa.PLAY) Modifier.padding(start = 2.dp) else Modifier,
            )
            if (dot && active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp).clip(CircleShape).background(MuGreen))
            if (badge != null) Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MuGreen, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000.0).roundToInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private suspend fun coverBitmap(
    context: Context,
    url: String,
): Bitmap? =
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

/** The now-playing pill's tint: the cover's colour, dark and calm (same recipe as the web page). */
private fun coverTint(bmp: Bitmap): Color {
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
    return Color.hsl(h.roundToInt().toFloat() % 360, min(s, .4f).coerceIn(0f, 1f), max(.14f, min(l, .22f)))
}
