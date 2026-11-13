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
) {
    val season = seasons.getOrNull(selectedSeasonIndex)
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
        // (the same gradient as the film page: it keeps the logo and text readable)
        Box(
            Modifier
                .fillMaxSize()
                .bleedToScreenEdges()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent,
                        .2f to Color.Transparent,
                        .4f to Color.Black.copy(alpha = .18f),
                        .7f to Color.Black.copy(alpha = .72f),
                        1f to Color.Black.copy(alpha = .94f),
                    ),
                ),
        )
        // scrolled down to the episodes: the picture darkens a little so their text reads easily
        // (user 2026-11-13; the film page keeps no extra darkening - user's earlier choice)
        val dim by androidx.compose.animation.core.animateFloatAsState(
            if (scroll.value > 0) .3f else 0f,
            // (a slow, eased fade - 0.3 s felt harsh, user 2026-11-13)
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "showDim",
        )
        if (dim > 0f) Box(Modifier.fillMaxSize().bleedToScreenEdges().background(Color.Black.copy(alpha = dim)))
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 24.dp, bottom = 48.dp),
    ) {
        // The first screen: everything at the bottom of a block as tall as the screen
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
                    // (the film page's margins: 40dp from the left, 56dp from the right)
                    .padding(start = 24.dp, end = 32.dp, bottom = 28.dp),
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
                )
            }
            // the season dropdown and "Play next episode": the film page's small see-through buttons
            androidx.compose.runtime.CompositionLocalProvider(com.github.damontecres.wholphin.ui.components.LocalIvfGlassButtons provides true) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 11.dp, bottom = 2.dp)
                            // back on the buttons: the first screen again
                            .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0) } }
                            .focusProperties {
                                val firstLoaded = (episodes as? EpisodeList.Success)?.episodes?.firstOrNull() != null
                                if (firstLoaded) down = firstEpisodeFocusRequester
                            },
                ) {
                    // Keys keep the Play button the same node when the season button appears once
                    // seasons load, so it keeps the initial focus
                    androidx.compose.runtime.key("season") {
                        if (showSeasonButton && season != null) {
                            GlassButton(
                                onClick = { showSeasonPicker = true },
                                modifier = Modifier.focusRequester(seasonButtonFocusRequester),
                            ) {
                                Text(text = seasonLabel(season), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    androidx.compose.runtime.key("playNext") {
                        GlassButton(
                            onClick = onPlayNextEpisode,
                            modifier = Modifier.focusRequester(playNextFocusRequester),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text(
                                text = stringResource(R.string.play_next_episode),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
            // the show's description, left-aligned across the full width (like the film page)
            series.data.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall.copy(shadow = com.github.damontecres.wholphin.ui.theme.IvfTextShadow),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                )
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

/** The film page's small see-through button (white 14%, the selected one light), for the show page's first screen */
@Composable
private fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    com.github.damontecres.wholphin.ui.components.Button(
        onClick = onClick,
        colors = com.github.damontecres.wholphin.ui.components.ivfButtonColors(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = modifier.height(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
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
