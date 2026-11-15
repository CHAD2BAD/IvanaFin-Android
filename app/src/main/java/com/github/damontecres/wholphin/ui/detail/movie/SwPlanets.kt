package com.github.damontecres.wholphin.ui.detail.movie

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.cards.PersonCard
import com.github.damontecres.wholphin.ui.cards.personRowCardWidth
import com.github.damontecres.wholphin.ui.ifElse
import org.json.JSONObject
import java.util.UUID

/**
 * IvanaFin (2026-11-15): Star Wars "Planets" row on the film page (above Cast & Crew), same as the web /
 * Samsung movie-page.js. Data: assets/sw_planets.json, generated from jellyfin-web-custom/data/sw-planets.json
 * (film id -> planets in order; each planet: picture (Wookieepedia), region/climate/terrain, a short bio,
 * the films it appears in).
 */
data class SwPlanet(
    val slug: String,
    val name: String,
    /** "Items/<id>/Images/Backdrop/<n>": the pictures are stored on the Jellyfin server (see [imageUrl]) */
    val image: String,
    val region: String,
    val climate: String,
    val terrain: String,
    val bio: String,
    val seenIn: List<String>,
)

object SwPlanets {
    @Volatile private var films: Map<String, List<SwPlanet>>? = null

    private fun load(context: Context): Map<String, List<SwPlanet>> {
        films?.let { return it }
        val result =
            runCatching {
                val root = JSONObject(context.assets.open("sw_planets.json").bufferedReader().use { it.readText() })
                val p = root.getJSONObject("planets")
                val planets = HashMap<String, SwPlanet>()
                for (slug in p.keys()) {
                    val o = p.getJSONObject(slug)
                    val seen = o.optJSONArray("seenIn")
                    planets[slug] =
                        SwPlanet(
                            slug = slug,
                            name = o.optString("name"),
                            image = o.optString("image"),
                            region = o.optString("region"),
                            climate = o.optString("climate"),
                            terrain = o.optString("terrain"),
                            bio = o.optString("bio"),
                            seenIn = if (seen == null) emptyList() else List(seen.length()) { seen.getString(it) },
                        )
                }
                val f = root.getJSONObject("films")
                f.keys().asSequence().associateWith { id ->
                    val arr = f.getJSONArray(id)
                    List(arr.length()) { arr.getString(it) }.mapNotNull { planets[it] }
                }
            }.getOrDefault(emptyMap())
        films = result
        return result
    }

    /** The film's planets in order, or empty for anything that isn't a Star Wars film. */
    fun forFilm(
        context: Context,
        id: UUID,
    ): List<SwPlanet> = load(context)[id.toString().replace("-", "")].orEmpty()
}

/** The planet's picture from the Jellyfin server, [width] px wide. */
@Composable
private fun SwPlanet.imageUrl(width: Int): String? {
    val service = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    return remember(image, width) {
        val m = Regex("""Items/([0-9a-fA-F]{32})/Images/Backdrop/(\d+)""").find(image) ?: return@remember null
        val hex = m.groupValues[1]
        val id = UUID.fromString(hex.replace(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
        service.getItemImageUrl(
            itemId = id,
            imageType = org.jellyfin.sdk.model.api.ImageType.BACKDROP,
            maxWidth = width,
            imageIndex = m.groupValues[2].toInt(),
        )
    }
}

@Composable
fun PlanetRow(
    planets: List<SwPlanet>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    startCardFocusRequester: FocusRequester? = null,
) {
    val firstFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(
            text = "Featured Planets",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, startIndex - 2)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(firstFocus),
        ) {
            itemsIndexed(planets) { index, planet ->
                PersonCard(
                    name = planet.name,
                    role = planet.region,
                    imageUrl = planet.imageUrl(320),
                    favorite = false,
                    onClick = { onClick(index) },
                    onLongClick = {},
                    modifier =
                        Modifier
                            .width(personRowCardWidth)
                            .ifElse(index == startIndex, Modifier.focusRequester(firstFocus))
                            .ifElse(index == startIndex && startCardFocusRequester != null, Modifier.focusRequester(startCardFocusRequester ?: firstFocus))
                            .ifElse(index == planets.lastIndex, Modifier.focusProperties { right = FocusRequester.Cancel }),
                )
            }
        }
    }
}

/** A planet's card. Left/Right step through the film's planets, Back closes (onDismiss gets the last one shown). */
@Composable
fun PlanetDialog(
    planets: List<SwPlanet>,
    startIndex: Int,
    onDismiss: (Int) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, planets.lastIndex)) }
    val p = planets[index]
    val focus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = { onDismiss(index) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0B0B0B))
                .focusRequester(focus)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> { if (index > 0) index--; true }
                        Key.DirectionRight -> { if (index < planets.lastIndex) index++; true }
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                }.focusable()
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                AsyncImage(
                    model = p.imageUrl(500),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(170.dp).clip(CircleShape).background(Color.Black),
                )
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Fact("Region", p.region)
                    Fact("Climate", p.climate)
                    Fact("Terrain", p.terrain)
                    Spacer(Modifier.height(12.dp))
                    Text(p.bio, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFFD1D5DB))
                    if (p.seenIn.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Seen in: " + p.seenIn.joinToString(", "), fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF9CA3AF))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (planets.size > 1) "◀ ▶ more planets" else "", fontSize = 10.sp, color = Color(0xFF6B7280))
                        Text("Picture: Wookieepedia", fontSize = 10.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF9CA3AF), modifier = Modifier.width(60.dp))
        Text(value, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFE5E7EB))
    }
}
