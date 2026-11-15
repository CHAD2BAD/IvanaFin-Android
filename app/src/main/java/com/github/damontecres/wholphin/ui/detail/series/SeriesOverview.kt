@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.util.DataLoadingState
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Serializable
data class SeasonEpisode(
    val season: Int,
    val episode: Int,
)

@Serializable
data class SeasonEpisodeIds(
    val seasonId: UUID,
    val seasonNumber: Int?,
    val episodeId: UUID?,
    val episodeNumber: Int?,
)

@Serializable
data class SeriesOverviewPosition(
    val seasonTabIndex: Int,
    val episodeRowIndex: Int,
)

@Composable
fun SeriesOverview(
    preferences: UserPreferences,
    destination: Destination.SeriesOverview,
    initialSeasonEpisode: SeasonEpisodeIds?,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, initialSeasonEpisode, SeriesPageType.OVERVIEW)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val episodeRowFocusRequester = remember { FocusRequester() }
    val castCrewRowFocusRequester = remember { FocusRequester() }
    val guestStarRowFocusRequester = remember { FocusRequester() }
    val extrasRowFocusRequester = remember { FocusRequester() }

    val state by viewModel.state.collectAsState()
    val episodeList =
        remember(state.episodes) { (state.episodes as? EpisodeList.Success)?.episodes }

    val position by viewModel.position.collectAsState(SeriesOverviewPosition(0, 0))
    val currentPosition by rememberUpdatedState(position)
    LaunchedEffect(Unit) {
        if (state.seasons.isNotEmpty()) {
            state.seasons.getOrNull(position.seasonTabIndex)?.let {
                viewModel.loadEpisodes(it.id, it.indexNumber)
            }
        }
    }

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
    val playlistState by playlistViewModel.playlistState.collectAsState()

    // IvanaFin: a show opens with "Play next episode" focused
    var rowFocused by rememberInt(PLAY_NEXT_ROW)
    // IvanaFin: the episode last played from this page (-1 = none) - Back from the player selects it
    var playedIndex by rememberInt(-1)
    // IvanaFin: the Cast & Crew card last opened (Back from the person page selects it)
    var castIndex by rememberInt(0)
    val castCardFocusRequester = remember { FocusRequester() }
    val playNextFocusRequester = remember { FocusRequester() }

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    viewModel.setWatched(itemId, watched, currentPosition.episodeRowIndex)
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(itemId, favorite, currentPosition.episodeRowIndex)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog = itemId
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
                onClearChosenStreams = {
                    val focusedEpisode =
                        (state.episodes as? EpisodeList.Success)
                            ?.episodes
                            ?.getOrNull(currentPosition.episodeRowIndex)
                    if (focusedEpisode != null) {
                        viewModel.clearChosenStreams(focusedEpisode, it)
                    }
                },
            )
        }

    LaunchedEffect(position, state.episodes) {
        val focusedEpisode =
            (state.episodes as? EpisodeList.Success)
                ?.episodes
                ?.getOrNull(position.episodeRowIndex)

        focusedEpisode?.let {
            viewModel.lookUpChosenTracks(it.id, it)
            viewModel.lookupPeopleInEpisode(it)
        }
    }
    val chosenStreams = state.chosenStreams

    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val preferredSubtitleLanguage = userDto?.configuration?.subtitleLanguagePreference

    when (val st = state.series) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            RequestOrRestoreFocus(
                when (rowFocused) {
                    PLAY_NEXT_ROW -> playNextFocusRequester
                    EPISODE_ROW -> episodeRowFocusRequester
                    CAST_AND_CREW_ROW -> castCrewRowFocusRequester
                    GUEST_STAR_ROW -> guestStarRowFocusRequester
                    EXTRAS_ROW -> extrasRowFocusRequester
                    else -> episodeRowFocusRequester
                },
                "series_overview",
            )
            // IvanaFin: the request above runs while the episodes are still loading (nothing to focus
            // yet - the page opened with nothing selected, and Back from an episode fell on episode 1).
            // Ask again once they are there: Play Next Episode, or the episode you were on.
            val episodesReady = state.episodes is EpisodeList.Success
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            LaunchedEffect(episodesReady) {
                if (!episodesReady) return@LaunchedEffect
                // back from playing an episode: that episode, even if the list was reset while reloading
                val target = playedIndex
                if (target >= 0) rowFocused = EPISODE_ROW
                // The header is rebuilt just after the first request (the button that got the focus is
                // replaced, so the page kept the focus with nothing drawn as selected). Ask again a few
                // times in the first 1.5 s; it follows rowFocused, so if you've moved on to an episode
                // it just re-selects that one.
                for (wait in listOf(60L, 250L, 500L, 900L, 1500L)) {
                    delay(wait)
                    // (the button could hold the focus without being drawn as selected: clear it first
                    // so it gets a fresh focus event)
                    if (wait == 250L) focusManager.clearFocus(force = true)
                    // (the reload can select episode 1 and overwrite the position: put it back - only in
                    // the first half second, so it never fights you moving to another episode)
                    if (target >= 0 && wait <= 500L && viewModel.position.value.episodeRowIndex != target) {
                        viewModel.position.update { it.copy(episodeRowIndex = target) }
                        delay(40) // (the episode's focus requester moves with the position)
                    }
                    when (rowFocused) {
                        PLAY_NEXT_ROW -> playNextFocusRequester
                        CAST_AND_CREW_ROW -> castCardFocusRequester
                        GUEST_STAR_ROW -> guestStarRowFocusRequester
                        EXTRAS_ROW -> extrasRowFocusRequester
                        else -> episodeRowFocusRequester
                    }.tryRequestFocus()
                }
                playedIndex = -1
            }
            LifecycleResumeEffect(destination.itemId) {
                viewModel.onResumePage()

                onPauseOrDispose {
                    viewModel.release()
                }
            }

            // IvanaFin: Jellyfin-web-style season page with a Prime-style season dropdown
            IvanaFinSeasonPage(
                preferences = preferences,
                series = st.data,
                seasons = state.seasons,
                episodes = state.episodes,
                people = state.people,
                selectedSeasonIndex = position.seasonTabIndex,
                focusedEpisodeIndex = position.episodeRowIndex,
                episodeFocusRequester = episodeRowFocusRequester,
                playNextFocusRequester = playNextFocusRequester,
                castFocusRequester = castCrewRowFocusRequester,
                castStartIndex = castIndex,
                castCardFocusRequester = castCardFocusRequester,
                onOpenItem = { id, isSeries -> viewModel.navigateTo(Destination.MediaItem(id, if (isSeries) org.jellyfin.sdk.model.api.BaseItemKind.SERIES else org.jellyfin.sdk.model.api.BaseItemKind.MOVIE)) },
                onChangeSeason = { index ->
                    if (index != position.seasonTabIndex) {
                        state.seasons.getOrNull(index)?.let { season ->
                            viewModel.loadEpisodes(season.id, season.indexNumber)
                            viewModel.position.update { SeriesOverviewPosition(index, 0) }
                        }
                    }
                },
                onFocusEpisode = { episodeIndex ->
                    rowFocused = EPISODE_ROW
                    viewModel.position.update { it.copy(episodeRowIndex = episodeIndex) }
                },
                onPlayEpisode = { ep ->
                    rowFocused = EPISODE_ROW
                    playedIndex = episodeList?.indexOfFirst { it?.id == ep.id } ?: -1
                    viewModel.navigateTo(Destination.Playback(ep.id, ep.resumeMs))
                },
                onEpisodeMenu = { ep ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = ep,
                            chosenStreams = chosenStreams,
                            showGoTo = false,
                            showStreamChoices = true,
                            canDelete = viewModel.canDelete(ep, preferences.appPreferences),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                onPlayNextEpisode = { viewModel.playNextUp() },
                onClickPerson = {
                    rowFocused = CAST_AND_CREW_ROW
                    castIndex = maxOf(0, state.people.indexOf(it))
                    viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.PERSON))
                },
                modifier = modifier,
            )
        }
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
    showPlaylistDialog?.let { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog = null },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog = null
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog = null
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

private const val PLAY_NEXT_ROW = -1 // IvanaFin
private const val EPISODE_ROW = 0
private const val CAST_AND_CREW_ROW = EPISODE_ROW + 1
private const val GUEST_STAR_ROW = CAST_AND_CREW_ROW + 1
private const val EXTRAS_ROW = GUEST_STAR_ROW + 1
