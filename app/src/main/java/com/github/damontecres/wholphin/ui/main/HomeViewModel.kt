package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomeDataService
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.combinePair
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val homeDataService: HomeDataService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val seerrService: com.github.damontecres.wholphin.services.SeerrService, // IvanaFin
        private val api: org.jellyfin.sdk.api.client.ApiClient, // IvanaFin: random library backdrops
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        // IvanaFin: show the Discover (Seerr) button on the home page only when Seerr is connected
        val seerrActive = seerrService.active

        /**
         * IvanaFin: backdrops shown on the home library tiles. The NEXT set is fetched (and its
         * images preloaded) while Home is on screen, and only swapped in when Home is left, so a
         * return to Home shows new pictures straight away with no visible change-over.
         */
        val tileBackdrops = MutableStateFlow<Map<java.util.UUID, BaseItem?>>(emptyMap())

        /** IvanaFin: home tile last opened, so coming back selects it again (-1 = none yet). */
        val homeLastOpenedIndex = androidx.compose.runtime.mutableIntStateOf(-1)

        /** IvanaFin: true until the selection has been restored once for this visit to Home. */
        private var restoreHomeSelection = true

        fun consumeHomeSelectionRestore(): Boolean = restoreHomeSelection.also { restoreHomeSelection = false }

        /** IvanaFin: last library list, so the tiles stay on screen while Home reloads its rows. */
        var lastHomeLibraries: List<BaseItem>? = null
        private var nextTileBackdrops: Map<java.util.UUID, BaseItem?>? = null
        private var tileBackdropJob: Job? = null

        /** IvanaFin: picture on the Discover home tile (a random Seerr trending backdrop). */
        val discoverTileBackdrop = MutableStateFlow<String?>(null)
        private var nextDiscoverTileBackdrop: String? = null
        private var discoverBackdropPool: List<String> = emptyList()

        /** Home is on screen: make sure a next set is being prepared (first visit: show one now). */
        fun prepareTileBackdrops(
            libraries: List<BaseItem>,
            preload: suspend (BaseItem) -> Unit,
            preloadUrl: suspend (String) -> Unit,
        ) {
            if (libraries.isEmpty() || tileBackdropJob?.isActive == true || nextTileBackdrops != null) return
            tileBackdropJob =
                viewModelScope.launchIO {
                    suspend fun pick() =
                        libraries
                            .map { lib -> async { lib.id to randomLibraryBackdrop(lib) } }
                            .awaitAll()
                            .toMap()
                    if (tileBackdrops.value.isEmpty()) {
                        tileBackdrops.value = pick()
                    }
                    if (discoverTileBackdrop.value == null) {
                        discoverTileBackdrop.value = randomDiscoverBackdrop()
                    }
                    val next = pick()
                    next.values.filterNotNull().forEach { preload(it) }
                    nextDiscoverTileBackdrop = randomDiscoverBackdrop()?.also { preloadUrl(it) }
                    nextTileBackdrops = next
                }
        }

        /** IvanaFin: a random trending backdrop from Seerr (list fetched once), not the one shown now. */
        private suspend fun randomDiscoverBackdrop(): String? =
            try {
                if (discoverBackdropPool.isEmpty() && seerrService.active.first()) {
                    discoverBackdropPool = seerrService.trending().mapNotNull { it.backDropUrl }
                }
                discoverBackdropPool.filter { it != discoverTileBackdrop.value }.randomOrNull()
                    ?: discoverBackdropPool.firstOrNull()
            } catch (ex: Exception) {
                Timber.w(ex, "Could not get a Discover tile backdrop")
                null
            }

        /** Home is being left: swap in the prepared set once the screen change has finished. */
        fun swapTileBackdropsAfterLeaving() {
            restoreHomeSelection = true // leaving Home: restore the selection once on return
            viewModelScope.launch {
                kotlinx.coroutines.delay(800)
                swapTileBackdrops()
            }
        }

        private fun swapTileBackdrops() {
            val next = nextTileBackdrops ?: return
            nextTileBackdrops = null
            // keep the current picture for any library whose lookup failed
            tileBackdrops.update { current -> current + next.filterValues { it != null } }
            nextDiscoverTileBackdrop?.let { discoverTileBackdrop.value = it }
            nextDiscoverTileBackdrop = null
        }

        /**
         * IvanaFin: picture for a home tile - a random movie or series backdrop from the library, or for
         * Live TV a programme airing now (sports preferred).
         */
        suspend fun randomLibraryBackdrop(library: BaseItem): BaseItem? =
            if (library.data.collectionType == org.jellyfin.sdk.model.api.CollectionType.LIVETV) {
                randomAiringProgram()
            } else {
                randomLibraryBackdrop(library.id)
            }

        private suspend fun randomAiringProgram(): BaseItem? =
            try {
                val programs =
                    api.liveTvApi
                        .getPrograms(
                            org.jellyfin.sdk.model.api.GetProgramsDto(
                                userId = serverRepository.currentUser?.id,
                                isAiring = true,
                                limit = 300,
                                enableImages = true,
                                enableImageTypes = listOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY),
                                imageTypeLimit = 1,
                                enableUserData = false,
                            ),
                        ).content.items
                        .filter { it.imageTags?.containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY) == true }
                val sports = programs.filter { it.isSports == true }
                (sports.ifEmpty { programs }).randomOrNull()?.let { BaseItem(it, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not get an airing programme for the Live TV tile")
                null
            }

        /** IvanaFin: a random movie or series with a backdrop from [libraryId], for the home tiles. */
        suspend fun randomLibraryBackdrop(libraryId: java.util.UUID): BaseItem? =
            try {
                    api.itemsApi
                        .getItems(
                            org.jellyfin.sdk.model.api.request.GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                parentId = libraryId,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                imageTypes = listOf(org.jellyfin.sdk.model.api.ImageType.BACKDROP),
                                sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.RANDOM),
                                limit = 1,
                            ),
                        ).content.items
                        .firstOrNull()
                        ?.let { BaseItem(it, false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not get a random backdrop for %s", libraryId)
                null
            }

        private var dataLoadingJob: Job? = null

        init {
            datePlayedService.invalidateAll()
            serverRepository.currentUserDtoFlow
                .combinePair(homeSettingsService.currentSettings)
                .collectLatestIn(viewModelScope) { (userDto, settings) ->
                    Timber.v(
                        "Got new userDto & settings, userId=%s, settings?=%s",
                        userDto?.id,
                        settings != HomePageResolvedSettings.EMPTY,
                    )
//                    Timber.v("userDto=%s", userDto)
                    dataLoadingJob?.cancel()
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (state.value.settings.userId != settings.userId) {
                        Timber.d("User changed")
                        _state.update { HomeState.EMPTY }
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                serverRepository.currentUserDto?.let { userDto ->
                    if (dataLoadingJob?.isActive == false) {
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        if (userDto.id == settings.userId) {
                            dataLoadingJob =
                                viewModelScope.launchIO {
                                    loadHomeRows(userDto, settings)
                                }
                        } else {
                            Timber.d(
                                "Init user IDs don't match: %s vs %s",
                                userDto.id,
                                settings.userId,
                            )
                        }
                    } else {
                        Timber.v("Data loading job is active")
                    }
                }
            }
        }

        suspend fun loadHomeRows(
            userDto: ServerUserConfig,
            settings: HomePageResolvedSettings,
        ) {
            Timber.i("Starting loadHomeRows")
            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences

                val libraries =
                    navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)

                val state = state.value

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    settings.rows.size,
                )
                _state.update {
                    it.copy(
                        loadingState = if (refresh) LoadingState.Success else LoadingState.Loading,
                        refreshState = LoadingState.Loading,
                        settings = settings,
                        homeRows =
                            if (refresh) {
                                it.homeRows
                            } else {
                                List(settings.rows.size) {
                                    HomeRowLoadingState.Pending(EmptyStringProvider)
                                }
                            },
                    )
                }

                val semaphore = Semaphore(4)

                val deferred =
                    settings.rows
                        .map { row ->
                            viewModelScope.async(WholphinDispatchers.IO) {
                                semaphore.withPermit {
                                    Timber.v("Fetching row: %s", row)
                                    try {
                                        homeDataService.fetchDataForRow(
                                            row = row.config,
                                            scope = viewModelScope,
                                            prefs = prefs,
                                            userDto = userDto,
                                            libraries = libraries,
                                            limit = prefs.maxItemsPerRow,
                                            isRefresh = refresh,
                                        )
                                    } catch (ex: InvalidStatusException) {
                                        if (ex.status == 404) {
                                            Timber.w(ex, "404 on row %s", row)
                                            HomeRowLoadingState.Success(
                                                row.title,
                                                emptyList(),
                                            )
                                        } else {
                                            Timber.e(
                                                ex,
                                                "Error %s on row %s",
                                                ex.status,
                                                row,
                                            )
                                            HomeRowLoadingState.Error(
                                                row.title,
                                                exception = ex,
                                            )
                                        }
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Error on row %s", row)
                                        HomeRowLoadingState.Error(
                                            row.title,
                                            exception = ex,
                                        )
                                    }
                                }
                            }
                        }

                if (refresh) {
                    // Replace rows as they complete
                    val remaining = deferred.withIndex().toMutableList()
                    while (remaining.isNotEmpty()) {
                        val (rowIndex, rowData) =
                            select {
                                // "Return" the first remaining that is completed
                                remaining
                                    .forEach { (rowIndex, deferred) ->
                                        deferred.onAwait { rowIndex to it }
                                    }
                            }
                        Timber.v("Got row data index=%s", rowIndex)
                        remaining.removeIf { it.index == rowIndex }
                        _state.update { state ->
                            val newRows =
                                state.homeRows.toMutableList().apply {
                                    set(rowIndex, rowData)
                                }
                            state.copy(
                                homeRows = newRows,
                            )
                        }
                    }
                    _state.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Success,
                        )
                    }
                } else {
                    val rows = deferred.awaitAll()
                    Timber.v("Got all rows")
                    _state.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Success,
                            homeRows = rows,
                        )
                    }
                }
                Timber.d("Home page load complete")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during home page loading")
                if (state.value.loadingState == LoadingState.Success) {
                    showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                    _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    _state.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
