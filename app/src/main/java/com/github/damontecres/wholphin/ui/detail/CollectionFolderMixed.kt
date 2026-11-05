package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import com.github.damontecres.wholphin.ui.data.SortAndDirection

/**
 * IvanaFin: tabbed page for mixed movies + TV libraries (no collection type on the server),
 * laid out like [CollectionFolderMovie]: Library (movies and shows together), Collections, Genres.
 */
@Composable
fun CollectionFolderMixed(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.library),
                TabDetails(R.string.collections),
                TabDetails(R.string.genres),
            )
        }
    val itemTypes = remember { listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    TabbedPage(
        itemId = destination.itemId.toString(),
        tabs = tabs,
        alwaysStartOn = 0, // always open the full Library tab
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        when (tabIndex) {
            // Library
            0 -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_library",
                    // Top level only, as organised on disk: a recursive movie+series search
                    // lists every episode-as-movie file inside the sub-folders.
                    // Always opens in release order (saved sort is ignored for this tab)
                    initialFilter = CollectionFolderFilter(useSavedLibraryDisplayInfo = false),
                    initialSortAndDirection =
                        SortAndDirection(ItemSortBy.PREMIERE_DATE, SortOrder.ASCENDING),
                    showTitle = false,
                    recursive = false,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Collections
            1 -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_collection",
                    initialFilter =
                        CollectionFolderFilter(
                            filter = GetItemsFilter(includeItemTypes = listOf(BaseItemKind.BOX_SET)),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            2 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = itemTypes,
                    collectionType = CollectionType.UNKNOWN,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
