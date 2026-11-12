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
 * IvanaFin (2026-11-12, user design): the film page's first screen - centred at the bottom, over the
 * full-screen backdrop: logo (or title) -> info line -> buttons -> tagline -> description.
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
            QuickDetails(movie.ui.quickDetails, movie.timeRemainingOrRuntime)
        }

        // Buttons, centred under the info line
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
            buttons()
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(.70f),
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
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 1 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compact) 8.dp else 0.dp),
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
                        ).copy(textAlign = TextAlign.Center),
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
