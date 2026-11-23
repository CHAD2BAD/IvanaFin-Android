package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.jellyfinId
import com.github.damontecres.wholphin.services.jellyfinIdAsString
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

suspend fun getDiscoverRating(
    id: Int,
    func: suspend () -> DiscoverRating,
): DiscoverRating? =
    try {
        func.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ClientException) {
        if (ex.statusCode == 404) {
            Timber.w("No rating for %s", id)
        } else {
            Timber.e(ex, "Error fetching rating for %s", id)
        }
        null
    } catch (ex: Exception) {
        Timber.e(ex, "Error fetching rating for %s", id)
        null
    }

/**
 * Navigates to the Jellyfin media item if the [mediaInfo] has a valid [jellyfinId]
 *
 * If not, a error toast is shown
 */
suspend fun goToButtonDiscover(
    mediaInfo: MediaInfo?,
    type: BaseItemKind,
    context: Context,
    navigationManager: NavigationManager,
) {
    val id = mediaInfo?.jellyfinId
    if (id != null) {
        navigationManager.navigateTo(
            Destination.MediaItem(
                itemId = id,
                type = type,
            ),
        )
    } else {
        val stringId = mediaInfo?.jellyfinIdAsString
        val msg =
            if (stringId.isNotNullOrBlank()) {
                "Unknown Jellyfin ID: $stringId"
            } else {
                "No Jellyfin ID found"
            }
        Timber.w(
            "Unknown jellyfinId for tmdb=%s: %s/%s",
            mediaInfo?.tmdbId,
            mediaInfo?.jellyfinMediaId,
            mediaInfo?.jellyfinMediaId4k,
        )
        showToast(context, msg)
    }
}

/**
 * IvanaFin: true while a Seerr item is requested/pending/processing/partially available but not
 * fully available yet, so the discover page keeps re-checking until the "Go to" button can show.
 */
fun needsSeerrStatusPolling(info: com.github.damontecres.wholphin.api.seerr.model.MediaInfo?): Boolean {
    if (info == null || info.status == 5) return false
    return info.status in 2..4 || !info.requests.isNullOrEmpty()
}

/** IvanaFin: how often the discover page re-checks Seerr while an item is on its way. */
const val SEERR_STATUS_POLL_MS = 20_000L

/**
 * IvanaFin: "Downloading 42% · 12 min left" from Seerr's downloadStatus (Radarr/Sonarr queue),
 * or null when nothing is downloading. Multiple items (e.g. episodes) are summed.
 */
fun seerrDownloadProgressText(info: com.github.damontecres.wholphin.api.seerr.model.MediaInfo?): String? {
    val items = info?.downloadStatus.orEmpty().ifEmpty { info?.downloadStatus4k.orEmpty() }
    if (items.isEmpty()) return null
    val size = items.sumOf { it.propertySize ?: 0L }
    val left = items.sumOf { it.sizeLeft ?: 0L }
    val statuses = items.mapNotNull { it.status?.lowercase() }
    val label =
        when {
            statuses.isNotEmpty() && statuses.all { it == "paused" } -> "Paused"
            statuses.isNotEmpty() && statuses.all { it == "queued" || it == "delay" } -> "Queued"
            statuses.any { it == "importpending" || it == "importing" } && left == 0L -> "Importing"
            else -> "Downloading"
        }
    val count = if (items.size > 1) " ${items.size} files" else ""
    if (size <= 0L) return "$label$count"
    val pct = (((size - left).coerceAtLeast(0L) * 100) / size).coerceIn(0L, 100L)
    val eta =
        items
            .mapNotNull { parseArrTimeLeftSeconds(it.timeLeft) }
            .maxOrNull()
            ?.takeIf { it > 0 && label == "Downloading" }
            ?.let { " · ${formatSecondsLeft(it)} left" }
            .orEmpty()
    return "$label$count $pct%$eta"
}

/** Radarr/Sonarr timeLeft is "hh:mm:ss" or "d.hh:mm:ss". */
private fun parseArrTimeLeftSeconds(timeLeft: String?): Long? {
    if (timeLeft.isNullOrBlank()) return null
    return try {
        val (days, rest) =
            if (timeLeft.contains('.') && timeLeft.indexOf('.') < timeLeft.indexOf(':')) {
                timeLeft.substringBefore('.').toLong() to timeLeft.substringAfter('.')
            } else {
                0L to timeLeft
            }
        val parts = rest.substringBefore('.').split(':').map { it.toLong() }
        if (parts.size != 3) return null
        days * 86400 + parts[0] * 3600 + parts[1] * 60 + parts[2]
    } catch (ex: NumberFormatException) {
        null
    }
}

private fun formatSecondsLeft(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "$m min"
        else -> "<1 min"
    }
}


/** IvanaFin (round-7 beta): Seerr dates ("2026-11-20") as "20 Nov 2026", like web/Samsung (was the raw date). */
fun ivfSeerrDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        java.time.LocalDate.parse(raw.take(10)).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK))
    } catch (e: Exception) {
        raw
    }
}
