package com.github.damontecres.wholphin.ui.detail.episode

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ChapterRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ChooseVersionParams
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun EpisodeDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: EpisodeViewModel =
        hiltViewModel<EpisodeViewModel, EpisodeViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    val canDelete by viewModel.canDelete.collectAsState()

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
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
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
                onClearChosenStreams = { viewModel.clearChosenStreams(it) },
            )
        }

    when (val st = state.episode) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            val ep = st.data
            LifecycleResumeEffect(ep) {
                ep.data.seriesId?.let { seriesId ->
                    viewModel.maybePlayThemeSong(seriesId)
                }
                onPauseOrDispose {
                    viewModel.release()
                }
            }
            EpisodeDetailsContent(
                preferences = preferences,
                ep = ep,
                chosenStreams = state.chosenStreams,
                playOnClick = {
                    viewModel.navigateTo(
                        Destination.Playback(
                            ep.id,
                            it.inWholeMilliseconds,
                        ),
                    )
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(ep)
                },
                moreOnClick = {
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = false,
                            item = ep,
                            chosenStreams = state.chosenStreams,
                            showGoTo = false,
                            showStreamChoices = true,
                            canDelete = canDelete,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                watchOnClick = {
                    viewModel.setWatched(ep.id, !ep.played)
                },
                favoriteOnClick = {
                    viewModel.setFavorite(ep.id, !ep.favorite)
                },
                canDelete = canDelete,
                onConfirmDelete = { viewModel.deleteItem(ep) },
                onChooseVersion = { contextActions.onChooseVersion.invoke(ep, it) },
                modifier = modifier,
                // IvanaFin: web episode rows
                people = state.people,
                guestCast = state.guestCast,
                chapters = state.chapters,
                moreFromSeason = state.moreFromSeason,
                onClickPerson = {
                    viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.PERSON))
                },
                onClickItem = { viewModel.navigateTo(Destination.MediaItem(it)) }, // IvanaFin: episode page
            )
        }
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
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
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

// IvanaFin: web episode rows
private const val SEASON_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASON_ROW + 1
private const val GUEST_ROW = PEOPLE_ROW + 1
private const val CHAPTER_ROW = GUEST_ROW + 1

@Composable
fun EpisodeDetailsContent(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    playOnClick: (Duration) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    canDelete: Boolean,
    onConfirmDelete: () -> Unit,
    onChooseVersion: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
    people: List<Person> = emptyList(),
    guestCast: List<Person> = emptyList(),
    chapters: List<Chapter> = emptyList(),
    moreFromSeason: List<BaseItem> = emptyList(),
    onClickPerson: (Person) -> Unit = {},
    onClickItem: (BaseItem) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(CHAPTER_ROW + 1) { FocusRequester() } }
    val dto = ep.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    RequestOrRestoreFocus(focusRequesters.getOrNull(position))
    Box(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    EpisodeDetailsHeader(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                        // IvanaFin: buttons sit under the info line, like Jellyfin web
                        buttons = {
                    ExpandablePlayButtons(
                        title = ep.title ?: "",
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
                        trailers = null,
                        trailerOnClick = {},
                        canDelete = canDelete,
                        onConfirmDelete = onConfirmDelete,
                        chooseVersionParams =
                            remember(chosenStreams, ep, onChooseVersion) {
                                ChooseVersionParams(
                                    chosenStreams = chosenStreams,
                                    mediaSources = ep.data.mediaSources.orEmpty(),
                                    onChooseVersion = onChooseVersion,
                                )
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[HEADER_ROW]),
                    )
                        },
                    )
                }
            }
            // IvanaFin: Jellyfin web order - More from <season>, Cast & Crew, Guest Stars, Scenes
            if (moreFromSeason.isNotEmpty()) {
                item {
                    ItemRow(
                        title = stringResource(R.string.more_from_value, dto.seasonName ?: ""),
                        items = moreFromSeason,
                        onClickItem = { _, item ->
                            position = SEASON_ROW
                            onClickItem.invoke(item)
                        },
                        onLongClickItem = { _, _ -> },
                        cardContent = { _, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = mod,
                                showImageOverlay = true,
                                imageHeight = Cards.heightEpisode,
                                imageWidth = Dp.Unspecified,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SEASON_ROW]),
                    )
                }
            }
            if (people.isNotEmpty()) {
                item {
                    PersonRow(
                        title = R.string.cast_and_crew,
                        people = people,
                        onClick = {
                            position = PEOPLE_ROW
                            onClickPerson.invoke(it)
                        },
                        onLongClick = { _, _ -> },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[PEOPLE_ROW]),
                    )
                }
            }
            if (guestCast.isNotEmpty()) {
                item {
                    PersonRow(
                        title = R.string.guest_stars_title,
                        people = guestCast,
                        onClick = {
                            position = GUEST_ROW
                            onClickPerson.invoke(it)
                        },
                        onLongClick = { _, _ -> },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[GUEST_ROW]),
                    )
                }
            }
            if (chapters.isNotEmpty()) {
                item {
                    ChapterRow(
                        title = R.string.scenes_title,
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
        }
    }
}
