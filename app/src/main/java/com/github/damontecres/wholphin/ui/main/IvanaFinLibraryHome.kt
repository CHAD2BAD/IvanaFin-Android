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
import coil3.request.transformations
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType

/** IvanaFin: the extra "Discover" (Seerr) tile shown after the libraries on Home. */
class DiscoverTile(
    val name: String,
    val backdropUrl: String?,
    val onClick: () -> Unit,
)

/**
 * IvanaFin: the "Games" home tile opens Moonlight straight into the "IvanaFin Games" app
 * (ES-DE) on the Sunshine host PC, through Moonlight's own shortcut entry point.
 * The app id is Sunshine's CRC32 of the app name - renaming the app (or giving it an
 * image) in Sunshine changes it.
 */
private const val MOONLIGHT_PACKAGE = "com.limelight"
private const val GAMES_HOST_UUID = "F5D46348-9F3D-5C20-7110-4C2F65FCC0A5"
private const val GAMES_HOST_NAME = "DesktopVerde"
private const val GAMES_APP_ID = "302243709"
private const val GAMES_APP_NAME = "IvanaFin Games"

private fun launchGames(context: android.content.Context) {
    val intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setClassName(MOONLIGHT_PACKAGE, "$MOONLIGHT_PACKAGE.ShortcutTrampoline")
            .putExtra("UUID", GAMES_HOST_UUID)
            .putExtra("Name", GAMES_HOST_NAME)
            .putExtra("AppId", GAMES_APP_ID)
            .putExtra("AppName", GAMES_APP_NAME)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast
            .makeText(context, "Games needs the Moonlight app", android.widget.Toast.LENGTH_LONG)
            .show()
    }
}

/**
 * IvanaFin home: just the top buttons and the libraries as big tiles filling the screen,
 * four per row in the server's library order (Movies, Shows, Star Wars, Movies (Special) /
 * Archive, Music) followed by a Discover tile and a Games tile.
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
    // Extra tile after the libraries (null = Seerr not connected)
    discover: DiscoverTile? = null,
) {
    // Tiles after the libraries: Discover (when Seerr is connected), then Games
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Games picture: the current one shows now, the next is preloaded and swapped in after leaving Home
    var gamesArt by remember { mutableStateOf(GamesArt.current) }
    LifecycleResumeEffect(Unit) {
        gamesArt = GamesArt.current
        val next = GamesArt.next
        scope.launch {
            coil3.SingletonImageLoader.get(context).execute(tileBackdropRequest(context, next))
        }
        onPauseOrDispose { GamesArt.swap() }
    }
    val gamesTile =
        remember(context, gamesArt) {
            DiscoverTile(name = "Games", backdropUrl = gamesArt, onClick = { launchGames(context) })
        }
    val extraTiles = listOfNotNull(discover, gamesTile)
    val tileCount = libraries.size + extraTiles.size
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
        remember(tileCount, firstTileFocus) {
            List(tileCount) { if (it == 0) firstTileFocus else FocusRequester() }
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
                    .padding(top = 8.dp, bottom = 32.dp, end = 16.dp), // bottom gap = side margins
        ) {
            val columns = 4
            val rows = ((tileCount + columns - 1) / columns).coerceAtLeast(1)
            val gap = 16.dp
            val tileWidth = (maxWidth - gap * (columns - 1)) / columns
            val tileHeight = (maxHeight - gap * (rows - 1)) / rows
            // libraries (BaseItem), then Discover / Games (DiscoverTile)
            val tiles: List<Any> = libraries + extraTiles
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxSize(),
            ) {
                tiles.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowItems.forEachIndexed { columnIndex, tile ->
                            val index = rowIndex * columns + columnIndex
                            if (tile is BaseItem) {
                                val library = tile
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
                                            .focusRequester(focusRequesters[index]),
                                )
                            } else if (tile is DiscoverTile) {
                                PictureTile(
                                    name = tile.name,
                                    backdropUrl = tile.backdropUrl,
                                    width = tileWidth,
                                    height = tileHeight,
                                    onClick = tile.onClick,
                                    modifier = Modifier.focusRequester(focusRequesters[index]),
                                )
                            }
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
                TileTitle(text = displayName, shadow = true, modifier = Modifier.align(Alignment.Center))
            } else if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = library.name,
                    contentScale = ContentScale.Crop,
                    onError = { imageError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // No picture at all (e.g. an empty library): the same bold white name as the others
                TileTitle(text = displayName, shadow = false, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** A home tile that is not a library (Discover): picture + darkening + big name, same look as LibraryTile. */
@Composable
private fun PictureTile(
    name: String,
    backdropUrl: String?,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var backdropError by remember(backdropUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1f),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = shape),
            ),
        colors = CardDefaults.colors(containerColor = Color.Black),
        modifier = modifier.size(width, height),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (backdropUrl != null && !backdropError) {
                AsyncImage(
                    model = remember(backdropUrl) { tileBackdropRequest(context, backdropUrl) },
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    onError = { backdropError = true },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .35f)),
                )
            }
            TileTitle(text = name, shadow = true, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * IvanaFin: the big bold name on a home tile. Starts at the usual size (smaller for long names)
 * and shrinks until every word fits on one line, so a word is never split ("Discove / r").
 */
@Composable
private fun TileTitle(
    text: String,
    shadow: Boolean,
    modifier: Modifier = Modifier,
) {
    val base =
        (if (text.length > 14) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium)
            .copy(
                fontWeight = FontWeight.Bold,
                shadow = if (shadow) Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 2f), blurRadius = 16f) else null,
            )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = 24.dp),
    ) {
        val maxPx = constraints.maxWidth
        val style =
            remember(text, base, maxPx) {
                val words = text.split(' ').filter { it.isNotEmpty() }
                var scale = 1f
                fun widest(s: Float) =
                    words.maxOfOrNull { word ->
                        measurer
                            .measure(
                                text = word,
                                style = base.copy(fontSize = base.fontSize * s),
                                softWrap = false,
                                maxLines = 1,
                            ).size.width
                    } ?: 0
                while (scale > .4f && widest(scale) > maxPx) scale *= .92f
                if (scale == 1f) base else base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale)
            }
        Text(
            text = text,
            style = style,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * IvanaFin: Games tile pictures - Steam "library hero" art (1920x620, art centred, no logo) for
 * the Steam games in the PC's ES-DE menu, from Steam's public image server. A different one
 * each time Home is shown, like the library tiles. Same list in jellyfin-web-custom home-grid.js.
 */
private object GamesArt {
    private val steamAppIds =
        listOf(
            "1808500", "2379780", "1086940", "2807960", "1478500", "311210", "730", "435150",
            "2483190", "553850", "976730", "686810", "274170", "219150", "594650", "1538550",
            "3405340", "1127500", "1118520", "1144200", "252950", "2075800", "2001120", "393380",
            "413150", "489830",
        )

    private fun url(id: String) = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/library_hero.jpg"

    private fun pick(not: String?) = steamAppIds.map(::url).filter { it != not }.random()

    var current: String = pick(null)
        private set
    var next: String = pick(current)
        private set

    /** Home was left: show the (already preloaded) next picture on the next visit. */
    fun swap() {
        current = next
        next = pick(current)
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
