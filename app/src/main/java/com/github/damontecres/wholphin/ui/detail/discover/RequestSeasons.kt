package com.github.damontecres.wholphin.ui.detail.discover

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.github.damontecres.wholphin.ui.ifElse
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.LocalContentColor
import com.github.damontecres.wholphin.ui.FontAwesome
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.ui.cards.AvailableIndicator
import com.github.damontecres.wholphin.ui.cards.PartiallyAvailableIndicator
import com.github.damontecres.wholphin.ui.cards.PendingIndicator
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

data class RequestSeason(
    val season: Season,
    val status: RequestStatus,
    val availability: SeerrAvailability,
    val editable: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestSeasons(
    id: Int,
    title: String,
    seasons: List<RequestSeason>,
    seasons4k: List<RequestSeason>,
    data: SeerrRequestData,
    requestEnabled: Boolean,
    request4kEnabled: Boolean,
    onSubmit: (TvRequest) -> Unit,
    initialFocus: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    var is4k by remember { mutableStateOf(!requestEnabled) }
    val seasons = remember(is4k, seasons, seasons4k) { if (is4k) seasons4k else seasons }

    val allSeasonNumbers =
        remember(seasons) {
            seasons
                .filter { it.editable }
                .mapNotNull { it.season.seasonNumber }
                .toSet()
        }
    val availableSeasons =
        remember(seasons) {
            mutableStateSetOf(
                *seasons
                    .filter { season ->
                        !season.editable && (
                            season.status == RequestStatus.PENDING ||
                                season.status == RequestStatus.APPROVED ||
                                season.status == RequestStatus.COMPLETED ||
                                season.availability == SeerrAvailability.PARTIALLY_AVAILABLE ||
                                season.availability == SeerrAvailability.AVAILABLE
                        )
                    }.mapNotNull { season -> season.season.seasonNumber }
                    .toTypedArray(),
            )
        }
    val selectedSeasons =
        remember(seasons) {
            mutableStateSetOf<Int>(
                *seasons
                    .filter { season -> season.status == RequestStatus.PENDING }
                    .mapNotNull { season -> season.season.seasonNumber }
                    .toTypedArray(),
            )
        }

    var profile by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.profiles4k.firstOrNull { it.default } ?: data.profiles4k.firstOrNull()
            } else {
                data.profiles.firstOrNull { it.default } ?: data.profiles.firstOrNull()
            },
        )
    }
    var folder by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.rootFolders4k.firstOrNull { it.default } ?: data.rootFolders4k.firstOrNull()
            } else {
                data.rootFolders.firstOrNull { it.default } ?: data.rootFolders.firstOrNull()
            },
        )
    }
    val profiles = remember(is4k, data) { if (is4k) data.profiles4k else data.profiles }
    val folders = remember(is4k, data) { if (is4k) data.rootFolders4k else data.rootFolders }

    fun submit() {
        onSubmit.invoke(
            TvRequest(
                data = data,
                tvId = id,
                seasons = selectedSeasons.toList(),
                is4k = is4k,
                profileId = profile?.id,
                folder = folder?.path,
            ),
        )
    }

    // IvanaFin: everything on one screen - title + Request at the top, quality profile,
    // then all seasons as a grid of tiles. Root folder is always the default (not shown).
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = ::submit,
                enabled = selectedSeasons.isNotEmpty(),
            ) {
                Text(text = stringResource(R.string.request))
            }
        }
        if (requestEnabled && request4kEnabled) {
            RequestSwitchRow(
                label = stringResource(R.string.request_4k),
                checked = is4k,
                onClick = { is4k = !is4k },
            )
        }
        if (profiles.isNotEmpty()) {
            profile?.let {
                ChooseProfile(
                    selectedProfile = it,
                    profiles = profiles,
                    onClickProfile = { profile = it },
                    asTile = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(initialFocus),
                )
            }
        }
        HorizontalDivider()
        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
        val tileWidth = (maxWidth - 16.dp) / 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.tv_seasons),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val isSelected = allSeasonNumbers.isNotEmpty() && selectedSeasons.containsAll(allSeasonNumbers)
            SeasonTile(
                name = stringResource(R.string.select_all),
                subtitle = null,
                checked = isSelected,
                enabled = allSeasonNumbers.isNotEmpty(),
                status = null,
                onClick = {
                    if (isSelected) {
                        selectedSeasons.removeAll(allSeasonNumbers)
                    } else {
                        selectedSeasons.addAll(allSeasonNumbers)
                    }
                },
                modifier = Modifier.width(tileWidth),
            )
        }
        // Centred rows so a short last row stays symmetrical
        FlowRow(
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
        ) {
            seasons.forEach { season ->
                val seasonNumber = season.season.seasonNumber
                val checked = seasonNumber in selectedSeasons || seasonNumber in availableSeasons
                val specials = stringResource(R.string.ivanafin_specials)
                SeasonTile(
                    name =
                        when (seasonNumber) {
                            null -> season.season.name ?: ""
                            0 -> specials
                            else -> formatSeasonNumber(seasonNumber)
                        },
                    subtitle =
                        season.season.episodeCount?.let {
                            "$it " + stringResource(R.string.episodes)
                        },
                    checked = checked,
                    enabled = season.editable,
                    status = season.availability,
                    onClick = {
                        if (seasonNumber in selectedSeasons) {
                            selectedSeasons.remove(seasonNumber)
                        } else {
                            seasonNumber?.let { selectedSeasons.add(it) }
                        }
                    },
                    modifier =
                        Modifier
                            .width(tileWidth)
                            .ifElse(profiles.isEmpty() && season == seasons.firstOrNull { it.editable }, Modifier.focusRequester(initialFocus)),
                )
            }
        }
        }
        }
    }
}

@Composable
fun SeasonListItem(
    season: RequestSeason,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        enabled = season.editable,
        selected = false,
        headlineContent = {
            // IvanaFin: Seerr sends bare numbers as names; show "Specials" / "Season N"
            val specials = stringResource(R.string.ivanafin_specials)
            Text(
                text =
                    when (val n = season.season.seasonNumber) {
                        null -> season.season.name ?: ""
                        0 -> specials
                        else -> formatSeasonNumber(n)
                    },
            )
        },
        supportingContent = {
            season.season.episodeCount?.let {
                Text(
                    // TODO should use plurals string
                    text = "${season.season.episodeCount} " + stringResource(R.string.episodes),
                )
            }
        },
        // IvanaFin: no leading slot (it indented the names); status icon sits beside the switch
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeasonStatusIcon(season)
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        onClick.invoke()
                    },
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun SeasonStatusIcon(season: RequestSeason) {
            when (season.availability) {
                SeerrAvailability.UNKNOWN,
                SeerrAvailability.DELETED,
                -> {
                }

                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                -> {
                    PendingIndicator()
                }

                SeerrAvailability.PARTIALLY_AVAILABLE -> {
                    PartiallyAvailableIndicator()
                }

                SeerrAvailability.AVAILABLE -> {
                    AvailableIndicator()
                }

                SeerrAvailability.BLOCKLISTED -> {
                    // TODO handle block listed
                    //                    BlocklistedIndicator()
                }
            }
}

/** IvanaFin: label left, switch right - lines up with [SeasonListItem]. */
@Composable
fun RequestSwitchRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        headlineContent = { Text(text = label) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { onClick.invoke() },
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ClickSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
                pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                pressedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
            ),
        onClick = onClick,
        content = content,
        modifier = modifier,
    )
}

@Composable
fun ClickSwitch(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    ClickSurface(
        onClick = onClick,
        modifier = Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .height(54.dp),
        ) {
            Switch(
                checked = checked,
                onCheckedChange = {},
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = label,
            )
        }
    }
}

@Composable
fun RequestSeasonsDialog(
    id: Int,
    title: String,
    loading: LoadingState,
    data: SeerrRequestData,
    seasons: List<RequestSeason>,
    seasons4k: List<RequestSeason>,
    requestEnabled: Boolean,
    request4kEnabled: Boolean,
    onSubmit: (TvRequest) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        when (loading) {
            is LoadingState.Error -> {
                ErrorMessage(loading, Modifier)
            }

            LoadingState.Loading,
            LoadingState.Pending,
            -> {
                LoadingPage(Modifier)
            }

            LoadingState.Success -> {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                RequestSeasons(
                    id = id,
                    title = title,
                    data = data,
                    seasons = seasons,
                    seasons4k = seasons4k,
                    requestEnabled = requestEnabled,
                    request4kEnabled = request4kEnabled,
                    onSubmit = onSubmit,
                    modifier =
                        Modifier
                            .padding(16.dp),
                    initialFocus = focusRequester, // IvanaFin: open with Quality profile selected
                )
            }
        }
    }
}

@Preview(
    device = "spec:parent=tv_1080p",
    backgroundColor = 0xFF383535,
    uiMode = UI_MODE_TYPE_TELEVISION,
    heightDp = 800,
)
@Composable
fun RequestSeasonsPreview() {
    val seasons =
        List(10) {
            RequestSeason(
                season =
                    Season(
                        seasonNumber = it + 1,
                        episodeCount = 10 + it,
                    ),
                status = RequestStatus.UNKNOWN,
                availability =
                    if (it < 3) {
                        SeerrAvailability.AVAILABLE
                    } else {
                        SeerrAvailability.UNKNOWN
                    },
                editable = it >= 3,
            )
        }

    WholphinTheme {
        RequestSeasons(
            id = 1,
            title = "Series title",
            seasons = seasons,
            seasons4k = emptyList(),
            data =
                SeerrRequestData(
                    profiles4k =
                        listOf(
                            SeerrProfile(1, "HD", true),
                            SeerrProfile(2, "Ultra HD", false),
                        ),
                    rootFolders4k =
                        listOf(
                            SeerrRootFolder(1, "/tv", "400GB", true),
                        ),
                ),
            requestEnabled = true,
            request4kEnabled = true,
            onSubmit = { },
            modifier = Modifier.width(400.dp),
        )
    }
}

/**
 * IvanaFin: compact season toggle for the request grid. Name + episode count on the left,
 * a tick circle (or the availability icon for seasons that can't be requested) on the right.
 */
@Composable
fun SeasonTile(
    name: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    status: SeerrAvailability?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .18f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (checked) Color.White.copy(alpha = .16f) else Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
                pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                pressedContentColor = contentColorFor(MaterialTheme.colorScheme.inverseSurface),
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
            ),
        modifier = modifier.height(56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (!enabled && status != null) {
                SeasonStatusIconFor(status)
            } else {
                val content = LocalContentColor.current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .then(
                                if (checked) {
                                    Modifier.background(content, CircleShape)
                                } else {
                                    Modifier.border(1.5.dp, content.copy(alpha = .6f), CircleShape)
                                },
                            ),
                ) {
                    if (checked) {
                        Text(
                            text = stringResource(R.string.fa_check),
                            fontFamily = FontAwesome,
                            fontSize = 11.sp,
                            color =
                                if (content.luminance() > .5f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonStatusIconFor(status: SeerrAvailability) {
    when (status) {
        SeerrAvailability.PENDING,
        SeerrAvailability.PROCESSING,
        -> PendingIndicator()

        SeerrAvailability.PARTIALLY_AVAILABLE -> PartiallyAvailableIndicator()

        SeerrAvailability.AVAILABLE -> AvailableIndicator()

        else -> {}
    }
}
