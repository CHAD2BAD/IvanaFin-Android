package com.github.damontecres.wholphin.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PersonKind

/**
 * IvanaFin: Jellyfin web's item "details group" (jellyfin-web itemDetails `.itemDetailsGroup`):
 * labelled rows in web order Director(s), Writer(s), Studio(s), Genre(s).
 */
@Composable
fun IvanaFinDetailsGroup(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    val rows =
        remember(item.people, item.studios, item.genres) {
            fun names(kind: PersonKind) =
                item.people
                    .orEmpty()
                    .filter { it.type == kind }
                    .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                    .distinct()
            listOf(
                Triple(R.string.detail_director, R.string.detail_directors, names(PersonKind.DIRECTOR)),
                Triple(R.string.detail_writer, R.string.detail_writers, names(PersonKind.WRITER)),
                Triple(
                    R.string.detail_studio,
                    R.string.detail_studios,
                    item.studios.orEmpty().mapNotNull { it.name?.takeIf(String::isNotBlank) },
                ),
                Triple(R.string.detail_genre, R.string.detail_genres, item.genres.orEmpty()),
            ).filter { it.third.isNotEmpty() }
        }
    if (rows.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        rows.forEach { (single, plural, values) ->
            DetailsGroupRow(if (values.size == 1) single else plural, values)
        }
    }
}

@Composable
private fun DetailsGroupRow(
    @StringRes label: Int,
    values: List<String>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = values.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
