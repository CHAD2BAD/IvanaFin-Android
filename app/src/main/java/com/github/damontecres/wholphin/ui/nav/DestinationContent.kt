package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.filter.DefaultForGenresFilterOptions
import com.github.damontecres.wholphin.data.filter.DefaultForStudiosFilterOptions
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ItemGrid
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.rememberSortOptions
import com.github.damontecres.wholphin.ui.detail.CollectionFolderMixed
import com.github.damontecres.wholphin.ui.detail.CollectionFolderGeneric
import com.github.damontecres.wholphin.ui.detail.CollectionFolderMovie
import com.github.damontecres.wholphin.ui.detail.CollectionFolderPhotoAlbum
import com.github.damontecres.wholphin.ui.detail.CollectionFolderPlaylist
import com.github.damontecres.wholphin.ui.detail.CollectionFolderTv
import com.github.damontecres.wholphin.ui.detail.DebugPage
import com.github.damontecres.wholphin.ui.detail.FavoritesPage
import com.github.damontecres.wholphin.ui.detail.HomeRowGrid
import com.github.damontecres.wholphin.ui.detail.PersonPage
import com.github.damontecres.wholphin.ui.detail.PlaylistDetails
import com.github.damontecres.wholphin.ui.detail.collection.CollectionDetails
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverMovieDetails
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverPersonPage
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverSeriesDetails
import com.github.damontecres.wholphin.ui.detail.episode.EpisodeDetails
import com.github.damontecres.wholphin.ui.detail.movie.MovieDetails
import com.github.damontecres.wholphin.ui.detail.music.IvanaFinMusicLibrary
import com.github.damontecres.wholphin.ui.detail.music.ArtistDetailsPage
import com.github.damontecres.wholphin.ui.detail.music.NowPlayingPage
import com.github.damontecres.wholphin.ui.detail.music.SongDetailsPage
import com.github.damontecres.wholphin.ui.detail.series.SeriesDetails
import com.github.damontecres.wholphin.ui.detail.series.SeriesOverview
import com.github.damontecres.wholphin.ui.discover.DiscoverPage
import com.github.damontecres.wholphin.ui.discover.DiscoverRequestGrid
import com.github.damontecres.wholphin.ui.main.HomePage
import com.github.damontecres.wholphin.ui.main.settings.HomeSettingsPage
import com.github.damontecres.wholphin.ui.playback.PlayExternalPage
import com.github.damontecres.wholphin.ui.playback.PlaybackPage
import com.github.damontecres.wholphin.ui.preferences.PreferencesPage
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleStylePage
import com.github.damontecres.wholphin.ui.preferences.user.UserProfilePreferencesPage
import com.github.damontecres.wholphin.ui.search.SearchPage
import com.github.damontecres.wholphin.ui.setup.InstallUpdatePage
import com.github.damontecres.wholphin.ui.slideshow.SlideshowPage
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination.fullScreen) {
        LaunchedEffect(Unit) { onClearBackdrop.invoke() }
    }
    when (destination) {
        is Destination.Home -> {
            HomePage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.HomeSettings -> {
            HomeSettingsPage(preferences, modifier)
        }

        is Destination.Playback -> {
            val backend =
                destination.backend ?: preferences.appPreferences.playbackPreferences.playerBackend
            if (backend == PlayerBackend.EXTERNAL_PLAYER) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.PlaybackList -> {
            if (preferences.appPreferences.playbackPreferences.playerBackend == PlayerBackend.EXTERNAL_PLAYER) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.Settings -> {
            PreferencesPage(
                preferences.appPreferences,
                destination.screen,
                modifier,
            )
        }

        is Destination.SubtitleSettings -> {
            SubtitleStylePage(
                preferences.appPreferences,
                destination.hdr,
                modifier,
            )
        }

        Destination.UserAppPreferences -> {
            UserProfilePreferencesPage(modifier)
        }

        is Destination.SeriesOverview -> {
            SeriesOverview(
                preferences = preferences,
                destination = destination,
                initialSeasonEpisode = destination.seasonEpisode,
                modifier = modifier,
            )
        }

        is Destination.MediaItem -> DiscoverItemGuard(destination) {
            when (destination.type) {
                BaseItemKind.SERIES -> {
                    // IvanaFin: Prime-style - opening a show goes straight to its season page
                    // (next-up season, else season 1) instead of the series landing page
                    SeriesOverview(
                        preferences = preferences,
                        destination = Destination.SeriesOverview(destination.itemId, BaseItemKind.SERIES),
                        initialSeasonEpisode = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MOVIE -> {
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.VIDEO,
                BaseItemKind.MUSIC_VIDEO,
                -> {
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.EPISODE -> {
                    EpisodeDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.BOX_SET -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionDetails(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PLAYLIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PlaylistDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                BaseItemKind.COLLECTION_FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = true,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.USER_VIEW -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.PHOTO_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolderPhotoAlbum(
                        preferences = preferences,
                        itemId = destination.itemId,
                        recursive = false,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    // IvanaFin: albums open inside the Spotify-style music library
                    IvanaFinMusicLibrary(
                        preferences = preferences,
                        libraryId = null,
                        initialAlbumId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    ArtistDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.AUDIO -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    SongDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}", modifier)
                }
            }
        }

        is Destination.FilteredCollection -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                playEnabled = true, // TODO only genres use this currently, so might need to change in future
                filterOptions =
                    when (destination.parentType) {
                        BaseItemKind.GENRE -> DefaultForGenresFilterOptions
                        BaseItemKind.STUDIO -> DefaultForStudiosFilterOptions
                        else -> throw IllegalArgumentException("Unsupported parentType ${destination.parentType}")
                    },
                sortOptions = rememberSortOptions(destination.collectionType),
                modifier = modifier,
            )
        }

        is Destination.MoreHomeRow -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            HomeRowGrid(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.ItemGrid<*> -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            ItemGrid(
                preferences,
                destination,
                modifier,
            )
        }

        is Destination.Slideshow -> {
            SlideshowPage(
                slideshow = destination,
            )
        }

        Destination.Favorites -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            FavoritesPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        Destination.NowPlaying -> {
            NowPlayingPage(modifier)
        }

        Destination.UpdateApp -> {
            InstallUpdatePage(preferences, modifier)
        }

        Destination.License -> {
            LicenseInfo(modifier)
        }

        is Destination.Search -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            SearchPage(
                initialQuery = destination.query,
                userPreferences = preferences,
                modifier = modifier,
            )
        }

        Destination.Debug -> {
            DebugPage(preferences, modifier)
        }

        Destination.Discover -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            DiscoverPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.DiscoveredItem -> {
            when (destination.item.type) {
                SeerrItemType.MOVIE -> {
                    DiscoverMovieDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.TV -> {
                    DiscoverSeriesDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    DiscoverPersonPage(
                        person = destination.item,
                        startIndex = destination.startIndex,
                        modifier = modifier,
                    )
                }

                SeerrItemType.UNKNOWN -> {
                    Text(
                        text = "Unknown discover type",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        is Destination.DiscoverMoreResult -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            DiscoverRequestGrid(
                destination = destination,
                showTitle = true,
                positionCallback = { _, _ -> },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    usePostersOverride: Boolean?,
    recursiveOverride: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
        CollectionType.TVSHOWS -> {
            CollectionFolderTv(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.MOVIES -> {
            CollectionFolderMovie(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.BOXSETS -> {
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                usePosters = true,
                recursive = false,
                playEnabled = false,
                modifier = modifier,
                sortOptions = MovieSortOptions,
            )
        }

        CollectionType.PLAYLISTS -> {
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                true,
                modifier,
            )
        }

        CollectionType.MUSIC -> {
            // IvanaFin: Spotify-style music library (album list + album + player bar)
            IvanaFinMusicLibrary(
                preferences = preferences,
                libraryId = destination.itemId,
                initialAlbumId = null,
                modifier = modifier,
            )
        }

        CollectionType.HOMEVIDEOS,
        CollectionType.PHOTOS,
        -> {
            CollectionFolderPhotoAlbum(
                preferences = preferences,
                itemId = destination.itemId,
                recursive = recursiveOverride ?: false,
                modifier = modifier,
            )
        }

        CollectionType.MUSICVIDEOS,
        CollectionType.BOOKS,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = true,
                modifier = modifier,
            )
        }

        // IvanaFin: mixed movies + TV libraries (no collection type) look like the movie/TV
        // libraries - 2:3 posters, 6 columns, spacing 16, no library name at the top
        // Library roots (COLLECTION_FOLDER / USER_VIEW, i.e. no posters override) get tabs.
        null -> {
            if (usePostersOverride == null) {
                CollectionFolderMixed(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                CollectionFolderGeneric(
                    preferences,
                    destination.itemId,
                    usePosters = usePostersOverride,
                    recursive = recursiveOverride ?: false,
                    playEnabled = false,
                    showTitle = false,
                    modifier = modifier,
                )
            }
        }

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        CollectionType.LIVETV, // IvanaFin: Live TV removed - never listed, plain folder if ever opened
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = false,
                modifier = modifier,
            )
        }
    }
}
