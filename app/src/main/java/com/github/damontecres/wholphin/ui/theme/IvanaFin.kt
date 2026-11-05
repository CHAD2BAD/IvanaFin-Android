package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
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
