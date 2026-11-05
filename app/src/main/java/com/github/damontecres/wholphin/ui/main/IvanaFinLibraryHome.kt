package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType

/**
 * IvanaFin home: just the top buttons and the libraries as big tiles filling the screen,
 * two per row in the server's library order (Movies, Shows / Star Wars, Movies Alternative).
 */
@Composable
fun IvanaFinLibraryHome(
    libraries: List<BaseItem>,
    onClickLibrary: (Int, BaseItem) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    launchFocus: FocusRequester? = null,
    firstTileFocus: FocusRequester = remember { FocusRequester() },
    // Backdrop per library (missing key = still loading; null = none, use the library image).
    backdrops: Map<java.util.UUID, BaseItem?> = emptyMap(),
    // Home shown: prepare the next set in the background (the swap is triggered on navigation).
    onShown: () -> Unit = {},
    // index of the library last opened from Home (kept by the caller across visits), -1 = none
    lastOpenedIndex: Int = -1,
    // returns true only the first time it is called per visit to Home
    consumeSelectionRestore: () -> Boolean = { true },
) {
    val libraryIds = remember(libraries) { libraries.map { it.id } }
    val currentOnShown by rememberUpdatedState(onShown)
    // Bumped every time Home comes (back) on screen, to re-run the focus choice below
    var shownCount by remember { mutableIntStateOf(0) }
    // Keyed on Unit, not the library list: Home refreshes its data in the background after a
    // return, and re-running this would yank the selection back after the user had moved.
    LifecycleResumeEffect(Unit) {
        shownCount++
        currentOnShown()
        onPauseOrDispose { }
    }
    // lastOpenedIndex < 0 = nothing opened yet (fresh launch): select launchFocus (Search).
    // After opening a library and coming back, that library's tile is selected again.
    val currentLastOpened by rememberUpdatedState(lastOpenedIndex)
    val focusRequesters =
        remember(libraries.size, firstTileFocus) {
            List(libraries.size) { if (it == 0) firstTileFocus else FocusRequester() }
        }
    LaunchedEffect(shownCount) {
        // let the returning screen lay out first, then move the selection
        kotlinx.coroutines.delay(50)
        // only once per visit: later rebuilds (data refreshes) must not move the selection
        if (!consumeSelectionRestore()) return@LaunchedEffect
        val tile = focusRequesters.getOrNull(currentLastOpened)
        if (tile != null) {
            tile.tryRequestFocus()
        } else if (launchFocus?.tryRequestFocus() != true) {
            focusRequesters.firstOrNull()?.tryRequestFocus()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        header()
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp, end = 16.dp),
        ) {
            val columns = 2
            val rows = ((libraries.size + columns - 1) / columns).coerceAtLeast(1)
            val gap = 16.dp
            val tileWidth = (maxWidth - gap * (columns - 1)) / columns
            val tileHeight = (maxHeight - gap * (rows - 1)) / rows
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxSize(),
            ) {
                libraries.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowItems.forEachIndexed { columnIndex, library ->
                            val index = rowIndex * columns + columnIndex
                            LibraryTile(
                                library = library,
                                backdropItem = backdrops[library.id],
                                backdropLoaded = backdrops.containsKey(library.id),
                                width = tileWidth,
                                height = tileHeight,
                                onClick = {
                                    onClickLibrary.invoke(index, library)
                                },
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequesters[index])
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    library: BaseItem,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdropItem: BaseItem? = null,
    backdropLoaded: Boolean = true,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val fillWidth = with(density) { width.roundToPx() }
    val fillHeight = with(density) { height.roundToPx() }
    val backdropUrl =
        remember(backdropItem) {
            backdropItem?.let { tileBackdropUrl(imageUrlService, it) }
        }
    val imageUrl =
        remember(library, fillWidth, fillHeight) {
            library.imageUrlOverride
                ?: imageUrlService.getItemImageUrl(
                    library,
                    ImageType.PRIMARY,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                )
        }
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    var backdropError by remember(backdropUrl) { mutableStateOf(false) }
    val showBackdrop = backdropUrl != null && !backdropError
    // While the random backdrop is loading, show just the name on black (no flash of the old art)
    val showLibraryImage = !showBackdrop && backdropLoaded
    val displayName = ivanaFinLibraryDisplayName(library.name)
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        // Tiles stay the same size when selected
        scale = CardDefaults.scale(focusedScale = 1f), // no grow: the white outline shows the selection
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = shape),
            ),
        colors = CardDefaults.colors(containerColor = Color.Black),
        modifier = modifier.size(width, height),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showBackdrop || !showLibraryImage) {
                if (showBackdrop) {
                    AsyncImage(
                        model = remember(backdropUrl) { tileBackdropRequest(context, backdropUrl!!) },
                        contentDescription = library.name,
                        contentScale = ContentScale.Crop,
                        onError = { backdropError = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Darken the picture a little so the name stays readable
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = .35f)),
                    )
                }
                Text(
                    text = displayName,
                    style =
                        // long names (e.g. "Movies (Alternative Format)") get a smaller size so they fit on 2 lines
                        (if (displayName.length > 14) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium).copy(
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 2f), blurRadius = 16f),
                        ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                )
            } else if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = library.name,
                    contentScale = ContentScale.Crop,
                    onError = { imageError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** IvanaFin: names shown on the home tiles (the server keeps its own names). */
private fun ivanaFinLibraryDisplayName(name: String?): String =
    when (name) {
        // (renamed on the server instead: "Movies (Alternative Format)" is now "Movies (Special)")
        else -> name ?: ""
    }

/**
 * Backdrop URL for a home tile. Fixed size so the URL the tile shows is exactly the one that was
 * preloaded into the image cache (preloaded pictures then appear instantly).
 */
fun tileBackdropUrl(
    imageUrlService: com.github.damontecres.wholphin.services.ImageUrlService,
    item: BaseItem,
): String? =
    imageUrlService.getItemImageUrl(
        item,
        ImageType.BACKDROP,
        fillWidth = 1280,
        fillHeight = 720,
    )

/**
 * The one image request used for tile backdrops, both to preload and to show - same URL, size
 * and cache key, so a preloaded picture is a memory-cache hit and appears instantly.
 */
fun tileBackdropRequest(
    context: android.content.Context,
    url: String,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url)
        .memoryCacheKey(url)
        .size(coil3.size.Size.ORIGINAL)
        .crossfade(true)
        .build()
