package com.github.damontecres.wholphin.ui.detail.movie

import com.github.damontecres.wholphin.ui.theme.compactDetailHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.detail.IvanaFinDetailsGroup
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch

/**
 * IvanaFin (2026-11-12, ElegantFin layout): the film page's first screen, at the bottom over the full-screen
 * backdrop: logo (or title) -> info line -> buttons centred, then tagline + description left-aligned, full width.
 * (Cast & Crew and the other rows start below this screen.)
 */
@Composable
fun MovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit = {},
) {
    val dto = movie.data
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Title / logo, centred
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            TitleOrLogo(
                item = movie,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            // smaller info line (user request)
            // IvanaFin: Star Wars films start with the in-universe year ("19 BBY")
            val swContext = androidx.compose.ui.platform.LocalContext.current
            // (other servers: the year comes from the data, by TMDB id)
            val swYear =
                androidx.compose.runtime.remember(movie.id) {
                    if (SwPlanets.film(swContext, movie) == null) {
                        null
                    } else {
                        movie.data.providerIds?.get("SwYear") ?: SwPlanets.timeline(movie.data.providerIds?.get("Tmdb"))?.second
                    }
                }
            QuickDetails(movie.ui.quickDetails, movie.timeRemainingOrRuntime, textStyle = MaterialTheme.typography.labelMedium, prefix = swYear)
        }

        // Buttons, centred under the info line
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // smaller, see-through buttons over the backdrop (user request)
            androidx.compose.runtime.CompositionLocalProvider(com.github.damontecres.wholphin.ui.components.LocalIvfGlassButtons provides true) {
                buttons()
            }
        }

        // ElegantFin layout: tagline and description left-aligned across the full width
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp), // was 12: logo/info/buttons sit lower (user)
        ) {
            // IvanaFin: compact (web-like) header = fewer words on screen
            val compact = compactDetailHeader()
            if (!compact) {
                VideoStreamDetails(
                    chosenStreams = chosenStreams,
                    numberOfVersions = movie.data.mediaSourceCount ?: 0,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(shadow = com.github.damontecres.wholphin.ui.theme.IvfTextShadow),
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Start,
                    maxLines = if (compact) 1 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    // (start 8dp = the description's own inner padding, so the two lines up)
                    modifier = Modifier.padding(start = 8.dp, top = if (compact) 8.dp else 0.dp),
                )
            }

            // Description (whole text, centred)
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = if (compact) Int.MAX_VALUE else 3,
                    textStyle =
                        (
                            if (compact) {
                                MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            }
                        ).copy(textAlign = TextAlign.Start, shadow = com.github.damontecres.wholphin.ui.theme.IvfTextShadow),
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }

            // Web: .itemDetailsGroup - Director(s), Writer(s), Studio(s), Genre(s)
            if (!compact) {
                IvanaFinDetailsGroup(item = dto, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
