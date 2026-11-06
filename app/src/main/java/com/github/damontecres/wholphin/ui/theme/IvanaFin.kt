package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.enableMarquee
import androidx.compose.runtime.ReadOnlyComposable
import com.github.damontecres.wholphin.preferences.AppThemeColors

/*
 * IvanaFin: small switches that make the ElegantFin theme behave like Lewis's
 * Jellyfin web custom CSS. Other themes keep upstream Wholphin behaviour.
 */

private val isElegantFin: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalTheme.current == AppThemeColors.ELEGANTFIN

/** Web CSS hides `.countIndicator` and episode-number badges on cards. */
@Composable
@ReadOnlyComposable
fun showCardBadges(): Boolean = !isElegantFin

/**
 * Library/grid posters: no grow when highlighted, just a thin white outline drawn OUTSIDE the
 * picture (same as the Samsung app; an inside border squeezed the picture).
 */
@Composable
@ReadOnlyComposable
fun posterOutlineOnly(): Boolean = isElegantFin

/** Web CSS lets `.cardText` wrap instead of truncating; allow 2 lines for titles. */
@Composable
@ReadOnlyComposable
fun cardTitleMaxLines(): Int = if (isElegantFin) 2 else 1

/** Web keeps a pure black page behind the backdrop; skip the colour tint taken from the image. */
@Composable
@ReadOnlyComposable
fun useDynamicBackdropColors(): Boolean = !isElegantFin

/**
 * Marquee forces a single line, which defeats [cardTitleMaxLines]; only scroll titles when
 * they are limited to one line.
 */
@Composable
fun androidx.compose.ui.Modifier.cardTitleMarquee(focused: Boolean): androidx.compose.ui.Modifier =
    if (cardTitleMaxLines() > 1) this else enableMarquee(focused)

/** ElegantFin pop-ups: black glass panel with a faint outline and rounder corners. */
@Composable
@ReadOnlyComposable
fun useGlassDialogs(): Boolean = isElegantFin

/** Library grids: no random / play all / shuffle buttons (sort, filter and view options only). */
@Composable
@ReadOnlyComposable
fun showLibraryActionButtons(): Boolean = !isElegantFin

/**
 * Gap between a focused (enlarged) grid poster and its title. Negative = the title slides further
 * down than the default, so the white focus outline doesn't cut into it. Unfocused gap is unchanged.
 */
@Composable
@ReadOnlyComposable
fun gridCardFocusedTextGap(): androidx.compose.ui.unit.Dp =
    if (isElegantFin) (-6).dp else 4.dp

/** Home = only the libraries as big tiles (no Continue watching / Next up / Recently added rows). */
@Composable
@ReadOnlyComposable
fun useLibraryGridHome(): Boolean = isElegantFin

/**
 * IvanaFin: libraries already loaded by Home, so opening one from the home tiles doesn't have to
 * fetch the library item again before it can start loading the posters.
 */
object IvanaFinLibraryCache {
    private val items = java.util.concurrent.ConcurrentHashMap<java.util.UUID, com.github.damontecres.wholphin.data.model.BaseItem>()

    fun put(libraries: List<com.github.damontecres.wholphin.data.model.BaseItem>) {
        libraries.forEach { items[it.id] = it }
    }

    fun get(id: java.util.UUID): com.github.damontecres.wholphin.data.model.BaseItem? = items[id]
}

/** Movie page: quieter header like web - no stream chips, no Director/Writer/Studio/Genre rows. */
@Composable
@ReadOnlyComposable
fun compactDetailHeader(): Boolean = isElegantFin
