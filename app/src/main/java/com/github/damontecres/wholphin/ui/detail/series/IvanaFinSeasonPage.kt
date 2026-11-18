package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.util.LocalClock
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 4
private val GAP = 16.dp
private val HEADER_BUTTON_WIDTH = 220.dp // IvanaFin: season + play-next buttons the same size

/**
 * IvanaFin: the season page, modelled on Jellyfin web's season page (centred logo, season name,
 * buttons, overview, a 4-column episode grid with full descriptions, Cast & Crew) with an
 * Amazon-Prime-style season dropdown. OK on an episode plays it, holding OK opens its menu.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun IvanaFinSeasonPage(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    people: List<Person>,
    selectedSeasonIndex: Int,
    focusedEpisodeIndex: Int,
    episodeFocusRequester: FocusRequester,
    playNextFocusRequester: FocusRequester,
    castFocusRequester: FocusRequester,
    castStartIndex: Int,
    castCardFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onPlayEpisode: (BaseItem) -> Unit,
    onEpisodeMenu: (BaseItem) -> Unit,
    onPlayNextEpisode: () -> Unit,
    onClickPerson: (Person) -> Unit,
    modifier: Modifier = Modifier,
    onOpenItem: (java.util.UUID, Boolean) -> Unit = { _, _ -> },
    swStory: suspend (Double) -> com.github.damontecres.wholphin.ui.detail.movie.SwNeighbours? = { null },
) {
    val season = seasons.getOrNull(selectedSeasonIndex)
    // IvanaFin: Star Wars shows - per season (they change with the Season button): era line, Featured
    // Planets, Characters, Starships & Vehicles and In Story Order above Cast & Crew (SwPlanets.kt)
    val swContext = LocalContext.current
    // (other servers: the whole show's extras on every season, found by the show's TMDB id)
    val sw = remember(season?.id) { season?.let { com.github.damontecres.wholphin.ui.detail.movie.SwPlanets.season(swContext, it.id, series.data.providerIds?.get("Tmdb")) } }
    val swRows =
        remember(sw) {
            if (sw == null) emptyList() else listOf("Featured Planets" to sw.planets, "Characters" to sw.chars, "Starships & Vehicles" to sw.ships).filter { it.second.isNotEmpty() }
        }
    var swOpen by remember { mutableStateOf<Pair<Int, Int>?>(null) } // row, card
    val swIndex = remember(season?.id) { androidx.compose.runtime.mutableStateListOf(0, 0, 0) }
    val swRowFocus = remember { List(3) { FocusRequester() } }
    val swScope = androidx.compose.runtime.rememberCoroutineScope()
    val swNb = androidx.compose.runtime.produceState<com.github.damontecres.wholphin.ui.detail.movie.SwNeighbours?>(null, sw) { value = sw?.let { swStory(it.k) } }.value
    // IvanaFin: a show with only one season has no Season button (nothing to choose)
    val showSeasonButton = season != null && seasons.size > 1
    var showSeasonPicker by remember { mutableStateOf(false) }
    var focusAfterSeasonChange by remember { mutableStateOf(false) }
    // IvanaFin: Down from the Season / Play Next Episode buttons always goes to episode 1
    val firstEpisodeFocusRequester = remember { FocusRequester() }
    // IvanaFin: Up from any episode in the top row goes to the Season button (Play Next Episode if none)
    val seasonButtonFocusRequester = remember { FocusRequester() }

    // IvanaFin (2026-11-13, user: "the film page treatment"): like the film page, the show's backdrop
    // fills the whole screen, darkened towards the bottom; the first screen is logo -> info line ->
    // buttons centred at the bottom, then the show's description left-aligned. Episodes and Cast &
    // Crew start below the first screen.
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val imageUrlService = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    val backdropUrl =
        remember(series) {
            imageUrlService.getItemImageUrl(series, org.jellyfin.sdk.model.api.ImageType.BACKDROP, fillWidth = 1920)
        }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().bleedToScreenEdges(),
            )
        }
        // the picture shows clearly behind the logo at the top and darkens where the episodes start (about a
        // third of the way down), so their text reads on any picture (2026-11-14 layout)
        // scrolled down to the episodes: the picture darkens a little so their text reads easily
        // (user 2026-11-13; .55 since beta round 5: episode text was still hard to read on bright pictures)
        // (derivedStateOf: only crossing the top re-checks, not every scrolled pixel - Fire TV smoothness 2026-11-18)
        val scrolled by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 0 } }
        val dim by androidx.compose.animation.core.animateFloatAsState(
            if (scrolled) .55f else 0f,
            // (a slow, eased fade - 0.3 s felt harsh, user 2026-11-13)
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "showDim",
        )
        // gradient and scroll darkening drawn as ONE layer (dim read while drawing only)
        Box(
            Modifier
                .fillMaxSize()
                .bleedToScreenEdges()
                .drawBehind {
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = ivfDarken(.1f, dim)),
                            .25f to Color.Black.copy(alpha = ivfDarken(.3f, dim)),
                            .45f to Color.Black.copy(alpha = ivfDarken(.72f, dim)),
                            1f to Color.Black.copy(alpha = ivfDarken(.9f, dim)),
                        ),
                    )
                },
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 24.dp, bottom = 48.dp),
    ) {
        // The header (user 2026-11-14: back to the old layout - logo at the top, the buttons under it and the
        // episodes straight below on the first screen; the full-screen picture stays)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TitleOrLogo(
                    item = series,
                    showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                com.github.damontecres.wholphin.ui.components.QuickDetails(
                    series.ui.quickDetails,
                    null,
                    textStyle = MaterialTheme.typography.labelMedium,
                    // Star Wars: the season's in-universe year first ("22 BBY")
                    prefix = sw?.year,
                )
            }
            // User choice: just the season dropdown and "Play next episode", centred under the logo
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                        // back on the buttons: the top of the page again
                        .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0) } }
                        .focusProperties {
                            val firstLoaded = (episodes as? EpisodeList.Success)?.episodes?.firstOrNull() != null
                            if (firstLoaded) down = firstEpisodeFocusRequester
                        },
            ) {
                // Keys keep the Play button the same node when the season button appears once
                // seasons load, so it keeps the initial focus
                androidx.compose.runtime.key("season") {
                    if (showSeasonButton) {
                        Button(
                            onClick = { showSeasonPicker = true },
                            modifier = Modifier.width(HEADER_BUTTON_WIDTH).focusRequester(seasonButtonFocusRequester),
                        ) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            Text(text = seasonLabel(season), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
                androidx.compose.runtime.key("playNext") {
                    Button(
                        onClick = onPlayNextEpisode,
                        modifier = Modifier.width(HEADER_BUTTON_WIDTH).focusRequester(playNextFocusRequester),
                    ) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text(
                            text = stringResource(R.string.play_next_episode),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // the season's own description (when it has one), above its episodes
        season?.data?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when (episodes) {
            EpisodeList.Loading -> LoadingPage()
            is EpisodeList.Error -> ErrorMessage(episodes.message, episodes.exception)
            is EpisodeList.Success -> {
                if (focusAfterSeasonChange) {
                    // after choosing a season: stay at the top with the Season button selected, like
                    // the Samsung app (beta round 5: focus went missing, or the page jumped to the episodes)
                    LaunchedEffect(episodes) {
                        delay(50)
                        if (!seasonButtonFocusRequester.tryRequestFocus()) episodeFocusRequester.tryRequestFocus()
                        scroll.animateScrollTo(0)
                        focusAfterSeasonChange = false
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val cardWidth = (maxWidth - GAP * (COLUMNS - 1)) / COLUMNS
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(GAP),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        maxItemsInEachRow = COLUMNS,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        episodes.episodes.forEachIndexed { index, episode ->
                            if (episode != null) {
                                EpisodeGridItem(
                                    episode = episode,
                                    width = cardWidth,
                                    onClick = { onPlayEpisode(episode) },
                                    onLongClick = { onEpisodeMenu(episode) },
                                    onFocused = { onFocusEpisode(index) },
                                    cellModifier = Modifier.fillMaxRowHeight(),
                                    modifier =
                                        (
                                            if (index == focusedEpisodeIndex) {
                                                Modifier.focusRequester(episodeFocusRequester)
                                            } else {
                                                Modifier
                                            }
                                        ).then(
                                            if (index == 0) Modifier.focusRequester(firstEpisodeFocusRequester) else Modifier,
                                        ).then(
                                            if (index < COLUMNS) {
                                                // Up from the top row: Season, or Play Next Episode when there is no Season button
                                                Modifier.focusProperties {
                                                    up = if (showSeasonButton) seasonButtonFocusRequester else playNextFocusRequester
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        swRows.forEachIndexed { row, (title, entries) ->
            androidx.compose.runtime.key(season?.id, row) {
                com.github.damontecres.wholphin.ui.detail.movie.SwRow(
                    title = title,
                    entries = entries,
                    onClick = { swIndex[row] = it; swOpen = row to it },
                    startIndex = swIndex[row],
                    startCardFocusRequester = swRowFocus[row],
                    modifier = Modifier.fillMaxWidth().padding(top = if (row == 0) 24.dp else 0.dp),
                )
            }
        }
        if (swNb != null && (swNb.before != null || swNb.after != null)) {
            com.github.damontecres.wholphin.ui.detail.movie.SwStoryRow(swNb, onOpen = onOpenItem, modifier = Modifier.fillMaxWidth())
        }
        // Web: Cast & Crew
        if (people.isNotEmpty()) {
            PersonRow(
                title = R.string.cast_and_crew,
                people = people,
                onClick = onClickPerson,
                startIndex = castStartIndex,
                startCardFocusRequester = castCardFocusRequester,
                // IvanaFin: Back from a person page returns to the person you opened (the row's own
                // focusRestorer picks the remembered card)
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).focusRequester(castFocusRequester),
            )
        }
    }
    }

    swOpen?.let { (row, start) ->
        com.github.damontecres.wholphin.ui.detail.movie.SwCardDialog(
            entries = swRows.getOrNull(row)?.second ?: return@let,
            startIndex = start,
            currentId = series.id,
            onDismiss = { last ->
                swOpen = null
                swIndex[row] = last
                swScope.launch {
                    kotlinx.coroutines.delay(120)
                    repeat(10) {
                        if (runCatching { swRowFocus[row].requestFocus() }.getOrDefault(false)) return@launch
                        kotlinx.coroutines.delay(50)
                    }
                }
            },
            onOpen = { l -> swOpen = null; l.id?.let { onOpenItem(it, l.series) } },
        )
    }

    if (showSeasonPicker) {
        DialogPopup(
            showDialog = true,
            title = series.name ?: "",
            dialogItems =
                // IvanaFin: Specials (season 0) at the bottom of the list; indexes stay the real ones
                seasons
                    .withIndex()
                    .sortedBy { (_, s) -> if (s?.data?.indexNumber == 0) 1 else 0 }
                    .mapNotNull { (index, s) ->
                    s?.let {
                        DialogItem(
                            headlineContent = { Text(seasonLabel(it)) },
                            onClick = {
                                if (index != selectedSeasonIndex) {
                                    focusAfterSeasonChange = true
                                    onChangeSeason(index)
                                }
                            },
                            selected = index == selectedSeasonIndex,
                        )
                    }
                },
            onDismissRequest = { showSeasonPicker = false },
            waitToLoad = false,
        )
    }
}

/**
 * Exactly what Jellyfin web shows under an episode title: `46m  ★ 6.5  Ends at 14:31`
 */
@Composable
private fun WebEpisodeDetailsLine(episode: BaseItem) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val now by LocalClock.current.now
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurface
    val runtime =
        episode.data.runTimeTicks
            ?.ticks
            ?.roundMinutes
            ?.takeIf { it > Duration.ZERO }
    val remaining = episode.timeRemainingOrRuntime
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        runtime?.let {
            Text(
                text = resources.formatDuration(it),
                style = style,
                color = color,
                maxLines = 1,
            )
        }
        episode.data.communityRating?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF00A4DC),
                    modifier = Modifier.padding(end = 4.dp).size(14.dp),
                )
                Text(text = "%.1f".format(rating), style = style, color = color, maxLines = 1)
            }
        }
        remaining?.let {
            val endsAt = now.plusSeconds(it.inWholeSeconds)
            Text(
                text =
                    stringResource(
                        R.string.ends_at,
                        formatTime(context, endsAt),
                    ),
                style = style,
                color = color,
                maxLines = 1,
            )
        }
    }
}

private fun seasonLabel(season: BaseItem): String =
    season.name ?: season.data.indexNumber?.let { formatSeasonNumber(it) } ?: ""

/**
 * One episode in the grid: thumbnail (watched tick / progress bar), "N. Title",
 * the full description, then runtime · rating · ends at (Prime order, 2026-11-12).
 */
@Composable
private fun EpisodeGridItem(
    episode: BaseItem,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    // (the FlowRow's fillMaxRowHeight: every card in a row is as tall as the tallest)
    cellModifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = cellModifier.width(width),
    ) {
        BannerCard(
            name = null,
            item = episode,
            onClick = onClick,
            onLongClick = onLongClick,
            played = episode.played,
            favorite = episode.favorite,
            playPercent = episode.data.userData?.playedPercentage ?: 0.0,
            cardHeight = width / AspectRatios.WIDE,
            aspectRatio = AspectRatios.WIDE,
            interactionSource = interactionSource,
            useSeriesForPrimary = false,
            cornerRadius = 4.dp, // Prime-style small corners
            modifier = modifier,
        )
        Text(
            text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
            style = MaterialTheme.typography.titleSmall.copy(shadow = com.github.damontecres.wholphin.ui.theme.IvfTextShadow),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Prime order: title, the full description, then runtime / rating / ends at underneath
        episode.data.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(shadow = com.github.damontecres.wholphin.ui.theme.IvfTextShadow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // the details line sits at the bottom, so it lines up across the row (round-4 beta)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = 4.dp)) { WebEpisodeDetailsLine(episode) }
    }
}

/**
 * The app lays every page out 32dp from the left and 16dp from the right screen edge
 * (ApplicationContent); the backdrop reaches the screen edges anyway (same as the film page).
 */
private fun Modifier.bleedToScreenEdges(): Modifier =
    this.layout { measurable, constraints ->
        val left = 32.dp.roundToPx()
        val right = 16.dp.roundToPx()
        val w = constraints.maxWidth + left + right
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-left, 0) }
    }

/** IvanaFin: black at [a] with the scroll darkening [dim] on top, as one alpha (one layer instead of two). */
private fun ivfDarken(
    a: Float,
    dim: Float,
): Float = 1f - (1f - a) * (1f - dim)
