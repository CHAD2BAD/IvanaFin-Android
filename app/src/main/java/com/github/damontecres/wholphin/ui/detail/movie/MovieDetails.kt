package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.github.damontecres.wholphin.ui.components.ChosenTrackResult
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.chooseStream
import com.github.damontecres.wholphin.ui.theme.compactDetailHeader
import org.jellyfin.sdk.model.api.MediaStreamType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.Cards
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.cards.ChapterRow
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.PersonContextActions
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ChooseVersionParams
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel =
        hiltViewModel<MovieViewModel, MovieViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    // IvanaFin: where you were on the page (row, cast card, scroll) - kept up here, outside the loading switch,
    // so it survives the page being rebuilt on the way back (beta round 6: Back from a cast member landed on Play)
    val ivfPosition = rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val ivfPersonIndex = rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val ivfListState = androidx.compose.foundation.lazy.rememberLazyListState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var trackDialog by remember { mutableStateOf<DialogParams?>(null) }
    val resources = LocalResources.current
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()

    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val preferredSubtitleLanguage = userDto?.configuration?.subtitleLanguagePreference

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = viewModel::setWatched,
                onClickFavorite = viewModel::setFavorite,
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(itemId)
                },
                onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                onDeleteItem = viewModel::deleteItem,
                onChooseVersion = { item, source ->
                    viewModel.savePlayVersion(
                        item,
                        source.id!!.toUUID(),
                    )
                },
                onChooseTracks = { result ->
                    viewModel.saveTrackSelection(
                        result.item,
                        result.itemPlayback,
                        result.trackIndex,
                        result.streamType,
                    )
                },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onClearChosenStreams = { viewModel.clearChosenStreams(it) },
            )
        }

    when (val s = state.loading) {
        is DataLoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success -> {
            val unknownStr = stringResource(R.string.unknown)
            val movie by rememberUpdatedState(s.data)
            val chosenStreams by rememberUpdatedState(state.chosenStreams)
            LifecycleResumeEffect(destination.itemId) {
                viewModel.maybePlayThemeSong(destination.itemId)
                onPauseOrDispose {
                    viewModel.release()
                }
            }
            MovieDetailsContent(
                preferences = preferences,
                positionState = ivfPosition,
                personIndexState = ivfPersonIndex,
                listState = ivfListState,
                movie = movie,
                state = state,
                onClickItem = { index, item ->
                    viewModel.navigateTo(item.destination())
                },
                onClickPerson = {
                    viewModel.navigateTo(
                        Destination.MediaItem(
                            it.id,
                            BaseItemKind.PERSON,
                        ),
                    )
                },
                playOnClick = {
                    viewModel.navigateTo(
                        Destination.Playback(
                            movie.id,
                            it.inWholeMilliseconds,
                        ),
                    )
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(movie)
                },
                // IvanaFin (2026-11-12, user request): the More button is now "Media Information"
                moreOnClick = {
                    overviewDialog = ItemDetailsDialogInfo(movie)
                },
                watchOnClick = {
                    viewModel.setWatched(movie.id, !movie.played)
                },
                favoriteOnClick = {
                    viewModel.setFavorite(movie.id, !movie.favorite)
                },
                onLongClickPerson = { index, person ->
                    showContextMenu =
                        ContextMenu.ForPerson(
                            fromLongClick = true,
                            person = person,
                            actions =
                                PersonContextActions(
                                    navigateTo = viewModel::navigateTo,
                                    onClickFavorite = viewModel::setFavorite,
                                ),
                        )
                },
                onLongClickSimilar = { _, similar ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = similar,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = false,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                onClickExtra = { index, extra ->
                    viewModel.navigateTo(extra.destination)
                },
                onClickDiscover = { index, item ->
                    viewModel.navigateTo(item.destination)
                },
                canDelete = state.canDelete,
                onConfirmDelete = { state.movie?.let { viewModel.deleteItem(it) } },
                onChooseVersion = { contextActions.onChooseVersion.invoke(movie, it) },
                modifier = modifier,
                // IvanaFin: Star Wars - before/after on the timeline, and opening films/shows from the cards
                swStory = androidx.compose.runtime.produceState<SwNeighbours?>(null, movie.id) { value = viewModel.swStory() }.value,
                onOpenItem = { id, series -> viewModel.navigateTo(com.github.damontecres.wholphin.ui.nav.Destination.MediaItem(id, if (series) org.jellyfin.sdk.model.api.BaseItemKind.SERIES else org.jellyfin.sdk.model.api.BaseItemKind.MOVIE)) },
                // IvanaFin: Audio / Subtitles buttons on the movie page
                onChooseTracks =
                    if (compactDetailHeader()) {
                        { type ->
                            viewModel.streamChoiceService
                                .chooseSource(movie.data, chosenStreams?.itemPlayback)
                                ?.let { source ->
                                    trackDialog =
                                        chooseStream(
                                            resources = resources,
                                            streams = source.mediaStreams.orEmpty(),
                                            type = type,
                                            streamChoice =
                                                if (type == MediaStreamType.AUDIO) {
                                                    chosenStreams?.audioStream
                                                } else {
                                                    chosenStreams?.subtitleStream
                                                },
                                            onClick = { trackIndex ->
                                                contextActions.onChooseTracks.invoke(
                                                    ChosenTrackResult(
                                                        item = movie,
                                                        streamType = type,
                                                        trackIndex = trackIndex,
                                                        itemPlayback = chosenStreams?.itemPlayback,
                                                    ),
                                                )
                                            },
                                            preferredSubtitleLanguage = preferredSubtitleLanguage,
                                        )
                                }
                        }
                    } else {
                        null
                    },
            )
        }
    }
    trackDialog?.let { params ->
        DialogPopup(
            params = params,
            onDismissRequest = { trackDialog = null },
            dismissOnClick = true,
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
        )
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                userDto
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

private const val HEADER_ROW = 0
private const val PEOPLE_ROW = HEADER_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val CHAPTER_ROW = TRAILER_ROW + 1
private const val EXTRAS_ROW = CHAPTER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val DISCOVER_ROW = SIMILAR_ROW + 1
private const val PLANET_ROW = DISCOVER_ROW + 1 // IvanaFin: Star Wars films (SwPlanets.kt)
private const val CHAR_ROW = PLANET_ROW + 1
private const val SHIP_ROW = CHAR_ROW + 1
private const val STORY_ROW = SHIP_ROW + 1

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    positionState: androidx.compose.runtime.MutableIntState,
    personIndexState: androidx.compose.runtime.MutableIntState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    movie: BaseItem,
    state: MovieState,
    playOnClick: (Duration) -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickPerson: (Int, Person) -> Unit,
    onLongClickSimilar: (Int, BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    canDelete: Boolean,
    onConfirmDelete: () -> Unit,
    onChooseVersion: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
    onChooseTracks: ((MediaStreamType) -> Unit)? = null,
    swStory: SwNeighbours? = null,
    onOpenItem: (java.util.UUID, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by positionState
    val focusRequesters = remember { List(STORY_ROW + 1) { FocusRequester() } }
    // IvanaFin: Star Wars extras (rows above Cast & Crew); the card open (kind, index), and the card to
    // select in each row after it
    val sw = remember(movie.id) { SwPlanets.film(context, movie.id) }
    val planets = sw?.planets.orEmpty()
    val swRows = remember(sw) { listOfNotNull(sw?.planets?.takeIf { it.isNotEmpty() }?.let { Triple("Featured Planets", it, PLANET_ROW) }, sw?.chars?.takeIf { it.isNotEmpty() }?.let { Triple("Characters", it, CHAR_ROW) }, sw?.ships?.takeIf { it.isNotEmpty() }?.let { Triple("Starships & Vehicles", it, SHIP_ROW) }) }
    var swOpen by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (row constant, index)
    val swIndex = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) { androidx.compose.runtime.mutableStateListOf(0, 0, 0) }
    var crawlOpen by remember { mutableStateOf(false) }
    val dto = movie.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // IvanaFin: the person opened from Cast & Crew (its card is focused directly on the way back)
    var personIndex by personIndexState

    // IvanaFin: back on the page, the row's cards are laid out a frame after this runs - a single request
    // failed and focus fell back to Play at the top (beta round 6: Back from a cast member, all clients)
    // (read once, as the page is built: Play gets focus by default a moment later and its focus handler
    // resets the row to the header before this ran - so Back always landed on Play)
    val restoreRow = remember { position }
    LaunchedEffect(Unit) {
        val target = focusRequesters.getOrNull(restoreRow) ?: return@LaunchedEffect
        position = restoreRow
        // the list comes back at the top, where the cast row isn't built yet (so its card can't take focus):
        // bring the cast row on screen first
        // (the Planets row, on Star Wars films, comes before it)
        if (restoreRow == PEOPLE_ROW && state.people.isNotEmpty()) runCatching { listState.scrollToItem(1 + swRows.size) }
        swRows.forEachIndexed { i, r -> if (restoreRow == r.third) runCatching { listState.scrollToItem(1 + i) } }
        repeat(20) { n ->
            // (requestFocus(direction) returns false while the card isn't laid out - tryRequestFocus() only
            // logs a warning then, so it can't tell)
            if (runCatching { target.requestFocus(androidx.compose.ui.focus.FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }

    // IvanaFin (2026-11-12, user design): the backdrop fills the whole screen, darkened towards the
    // bottom where the title and buttons sit (no extra darkening when scrolled to the rows - user choice)
    val imageUrlService = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    val backdropUrl =
        remember(movie) {
            imageUrlService.getItemImageUrl(movie, org.jellyfin.sdk.model.api.ImageType.BACKDROP, fillWidth = 1920)
        }
    Box(modifier = modifier) {
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().bleedToScreenEdges(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .bleedToScreenEdges()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        .2f to androidx.compose.ui.graphics.Color.Transparent,
                        .4f to androidx.compose.ui.graphics.Color.Black.copy(alpha = .18f),
                        .7f to androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f),
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = .94f),
                    ),
                ),
        )
        // scrolled down to the rows: the picture darkens behind them so cast names read on bright
        // pictures (beta round 5: 2001, Alien - user 2026-11-13: yes, darker; same as the show page)
        val dim by androidx.compose.animation.core.animateFloatAsState(
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40) .55f else 0f,
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "filmDim",
        )
        if (dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .bleedToScreenEdges()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dim)),
            )
        }
        swOpen?.let { (row, start) ->
            val r = swRows.firstOrNull { it.third == row }
            if (r != null) {
                SwCardDialog(entries = r.second, startIndex = start, currentId = movie.id, onDismiss = { last ->
                    swOpen = null
                    // back on the row at the card last shown (it holds the row's focus requester)
                    swIndex[row - PLANET_ROW] = last
                    scope.launch(ExceptionHandler()) {
                        kotlinx.coroutines.delay(120)
                        repeat(10) {
                            if (runCatching { focusRequesters[row].requestFocus() }.getOrDefault(false)) return@launch
                            kotlinx.coroutines.delay(50)
                        }
                    }
                }, onOpen = { l -> swOpen = null; l.id?.let { onOpenItem(it, l.series) } })
            }
        }
        if (crawlOpen) sw?.crawl?.let { SwCrawlDialog(it, onDismiss = { crawlOpen = false }) }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                // the first screen: the header at the bottom of a full-height block
                Column(
                    verticalArrangement = Arrangement.Bottom,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight()
                            .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    MovieDetailsHeader(
                        preferences = preferences,
                        movie = movie,
                        chosenStreams = state.chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                // (end 16dp more than start: the page sits 32dp from the left and 16dp from the right edge)
                                .padding(start = 40.dp, end = 56.dp, bottom = 28.dp),
                        // IvanaFin: buttons sit under the info line, like Jellyfin web
                        buttons = {
                    // IvanaFin: Star Wars - the era under the info line, Opening Crawl after the buttons
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    val swYear = movie.data.providerIds?.get("SwYear")
                    val swKey = movie.data.providerIds?.get("SwTimeline")?.toDoubleOrNull()
                    if (sw != null && !swYear.isNullOrBlank() && swKey != null) {
                        androidx.tv.material3.Text(swYear + " · " + swEra(swKey), color = androidx.compose.ui.graphics.Color(0xFFF5C518), fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ExpandablePlayButtons(
                        title = movie.title ?: "",
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        favorite = dto.userData?.isFavorite ?: false,
                        playOnClick = {
                            position = HEADER_ROW
                            playOnClick.invoke(it)
                        },
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                position = HEADER_ROW
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        trailers = state.trailers,
                        trailerOnClick = {
                            position = TRAILER_ROW
                            trailerOnClick.invoke(it)
                        },
                        canDelete = canDelete,
                        onConfirmDelete = onConfirmDelete,
                        chooseVersionParams =
                            remember(state.chosenStreams, movie, onChooseVersion) {
                                ChooseVersionParams(
                                    chosenStreams = state.chosenStreams,
                                    mediaSources = movie.data.mediaSources.orEmpty(),
                                    onChooseVersion = onChooseVersion,
                                )
                            },
                        modifier = Modifier.focusRequester(focusRequesters[HEADER_ROW]), // centred by the header
                        onChooseAudio = onChooseTracks?.let { { it(MediaStreamType.AUDIO) } },
                        onChooseSubtitles = onChooseTracks?.let { { it(MediaStreamType.SUBTITLE) } },
                        moreIsMediaInfo = true,
                    )
                    if (sw?.crawl != null) {
                        com.github.damontecres.wholphin.ui.components.ExpandableFaButton(
                            title = R.string.ivf_opening_crawl,
                            iconStringRes = R.string.fa_list_ul,
                            onClick = { crawlOpen = true },
                        )
                    }
                    }
                    }
                        },
                    )
                }
            }
            swRows.forEach { (title, entries, row) ->
                item {
                    SwRow(
                        title = title,
                        entries = entries,
                        onClick = {
                            position = row
                            swIndex[row - PLANET_ROW] = it
                            swOpen = row to it
                        },
                        startIndex = swIndex[row - PLANET_ROW],
                        startCardFocusRequester = focusRequesters[row],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.people.letNotEmpty { people ->
                item {
                    PersonRow(
                        title = R.string.cast_and_crew, // IvanaFin: web label
                        people = people,
                        onClick = {
                            position = PEOPLE_ROW
                            personIndex = people.indexOf(it).coerceAtLeast(0)
                            onClickPerson.invoke(it)
                        },
                        onLongClick = { index, person ->
                            position = PEOPLE_ROW
                            personIndex = index
                            onLongClickPerson.invoke(index, person)
                        },
                        startIndex = personIndex,
                        startCardFocusRequester = focusRequesters[PEOPLE_ROW],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // IvanaFin: web order is Cast & Crew -> Special Features -> Scenes -> More Like This
            state.extras.letNotEmpty { extras ->
                item {
                    ExtrasRow(
                        extras = extras,
                        onClickItem = { index, item ->
                            position = EXTRAS_ROW
                            onClickExtra.invoke(index, item)
                        },
                        onLongClickItem = { _, _ -> },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[EXTRAS_ROW]),
                    )
                }
            }
            state.chapters.letNotEmpty { chapters ->
                item {
                    ChapterRow(
                        title = R.string.scenes_title, // IvanaFin: web label
                        chapters = chapters,
                        onClick = {
                            position = CHAPTER_ROW
                            playOnClick.invoke(it.position)
                        },
                        onLongClick = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[CHAPTER_ROW]),
                    )
                }
            }
            if (swStory != null && (swStory.before != null || swStory.after != null)) {
                item {
                    SwStoryRow(swStory, onOpen = { id, series -> position = STORY_ROW; onOpenItem(id, series) }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequesters[STORY_ROW]))
                }
            }
            state.similar.letNotEmpty { similar ->
                item {
                    val imageHeight =
                        remember(movie.type) {
                            if (movie.type == BaseItemKind.MOVIE) {
                                Cards.height2x3
                            } else {
                                Cards.heightEpisode
                            }
                        }
                    ItemRow(
                        title = stringResource(R.string.more_like_this),
                        items = similar,
                        onClickItem = { index, item ->
                            position = SIMILAR_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, similar ->
                            position = SIMILAR_ROW
                            onLongClickSimilar.invoke(index, similar)
                        },
                        cardContent = { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = mod,
                                showImageOverlay = true,
                                imageHeight = imageHeight,
                                imageWidth = Dp.Unspecified,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SIMILAR_ROW]),
                    )
                }
            }
            state.discovered.letNotEmpty { discovered ->
                item {
                    DiscoverRow(
                        row =
                            DiscoverRowData(
                                ResStringProvider(R.string.discover),
                                DataLoadingState.Success(discovered),
                                type = DiscoverRequestType.UNKNOWN,
                            ),
                        onClickItem = { index: Int, item: DiscoverItem ->
                            position = DISCOVER_ROW
                            onClickDiscover.invoke(index, item)
                        },
                        onLongClickItem = { _, _ -> },
                        onCardFocus = {},
                        focusRequester = focusRequesters[DISCOVER_ROW],
                    )
                }
            }
        }
    }
}

/**
 * IvanaFin: the app lays every page out 32dp from the left and 16dp from the right screen edge
 * (ApplicationContent); the film page's backdrop reaches the screen edges anyway.
 */
private fun Modifier.bleedToScreenEdges(): Modifier =
    this.layout { measurable, constraints ->
        val left = 32.dp.roundToPx()
        val right = 16.dp.roundToPx()
        val w = constraints.maxWidth + left + right
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-left, 0) }
    }