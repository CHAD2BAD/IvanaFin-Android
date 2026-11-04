package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
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
