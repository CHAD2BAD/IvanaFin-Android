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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 24.dp, top = 24.dp, bottom = 48.dp),
    ) {
        // Header (web: centred logo, season name, buttons)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            // IvanaFin: no fixed-width box, so the logo (drawn at the box's start) sits truly centred
            TitleOrLogo(
                item = series,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            )
        }
        // User choice: just the season dropdown and "Play next episode", centred under the logo
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusProperties {
                        val firstLoaded = (episodes as? EpisodeList.Success)?.episodes?.firstOrNull() != null
                        if (firstLoaded) down = firstEpisodeFocusRequester
                    },
        ) {
            // Keys keep the Play button the same node when the season button appears once
            // seasons load, so it keeps the initial focus
            androidx.compose.runtime.key("season") {
                if (showSeasonButton && season != null) {
                    Button(
                        onClick = { showSeasonPicker = true },
                        modifier = Modifier.width(HEADER_BUTTON_WIDTH).focusRequester(seasonButtonFocusRequester),
                    ) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Text(
                            text = seasonLabel(season),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
            androidx.compose.runtime.key("playNext") {
                Button(
                    onClick = onPlayNextEpisode,
                    modifier =
                        Modifier
                            .width(HEADER_BUTTON_WIDTH)
                            .focusRequester(playNextFocusRequester),
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
        // Web: season overview, full width
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
                    LaunchedEffect(episodes) {
                        episodeFocusRequester.tryRequestFocus()
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
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }

    if (showSeasonPicker) {
        DialogPopup(
            showDialog = true,
            title = series.name ?: "",
            dialogItems =
                seasons.mapIndexedNotNull { index, s ->
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
 * runtime · rating · ends at, and the full description.
 */
@Composable
private fun EpisodeGridItem(
    episode: BaseItem,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(width),
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
            modifier = modifier,
        )
        Text(
            text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        WebEpisodeDetailsLine(episode)
        episode.data.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
