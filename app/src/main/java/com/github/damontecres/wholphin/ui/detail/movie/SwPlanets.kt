package com.github.damontecres.wholphin.ui.detail.movie

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.cards.PersonCard
import com.github.damontecres.wholphin.ui.cards.personRowCardWidth
import com.github.damontecres.wholphin.ui.ifElse
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

/**
 * IvanaFin (2026-11-15): Star Wars extras on film and show pages, same as the web / Samsung (movie-page.js,
 * series-page.js, sw-card.js): Featured Planets, Characters and Starships & Vehicles rows with a card for
 * each (facts, bio, "Also in" links), the era line, In Story Order (before / after on the timeline) and an
 * opening crawl (original 'story so far' text) for the saga episodes.
 * Data: assets/sw_planets.json, generated from jellyfin-web-custom/data/sw-planets-src (build.ts); the
 * pictures are stored on the Jellyfin server.
 */
data class SwLink(val name: String, val id: UUID?, val series: Boolean)

data class SwEntry(
    val kind: String, // planet | char | ship
    val slug: String,
    val name: String,
    /** "Items/<id>/Images/Backdrop/<n>" on the Jellyfin server */
    val image: String,
    val sub: String,
    val facts: List<Pair<String, String>>,
    val bio: String,
    val seenIn: List<SwLink>,
)

data class SwCrawl(val episode: String, val title: String, val text: List<String>)

data class SwFilm(val planets: List<SwEntry>, val chars: List<SwEntry>, val ships: List<SwEntry>, val crawl: SwCrawl?)

/** One season of a Star Wars show (k = its SwTimeline place, year = "22 BBY"). */
data class SwSeason(val k: Double, val year: String, val planets: List<SwEntry>, val chars: List<SwEntry>, val ships: List<SwEntry>)

fun hexToUuid(hex: String): UUID? =
    runCatching {
        val h = hex.replace("-", "")
        UUID.fromString(h.replace(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
    }.getOrNull()

private fun UUID.hex() = toString().replace("-", "")

object SwPlanets {
    @Volatile private var root: JSONObject? = null

    private fun data(context: Context): JSONObject? {
        root?.let { return it }
        val r = runCatching { JSONObject(context.assets.open("sw_planets.json").bufferedReader().use { it.readText() }) }.getOrNull()
        root = r
        return r
    }

    private val FACTS =
        mapOf(
            "planet" to listOf("Region" to "region", "Climate" to "climate", "Terrain" to "terrain"),
            "char" to listOf("Species" to "species", "Homeworld" to "homeworld", "Allegiance" to "side"),
            "ship" to listOf("Type" to "type", "Maker" to "maker", "Length" to "length"),
        )
    private val SUB = mapOf("planet" to "region", "char" to "species", "ship" to "type")
    private val SET = mapOf("planet" to "planets", "char" to "chars", "ship" to "ships")

    private fun entry(
        d: JSONObject,
        kind: String,
        ref: Any,
    ): SwEntry? {
        val slug = if (ref is JSONObject) ref.optString("id") else ref.toString()
        val o = d.getJSONObject(SET.getValue(kind)).optJSONObject(slug) ?: return null
        val seen = o.optJSONArray("seenIn") ?: JSONArray()
        return SwEntry(
            kind = kind,
            slug = slug,
            name = (ref as? JSONObject)?.optString("as")?.ifBlank { null } ?: o.optString("name"),
            image = (ref as? JSONObject)?.optString("image")?.ifBlank { null } ?: o.optString("image"),
            sub = o.optString(SUB.getValue(kind)),
            facts = FACTS.getValue(kind).map { it.first to o.optString(it.second) }.filter { it.second.isNotBlank() },
            bio = o.optString("bio"),
            seenIn =
                List(seen.length()) {
                    val s = seen.getJSONObject(it)
                    SwLink(s.optString("name"), hexToUuid(s.optString("id")), s.optBoolean("series"))
                },
        )
    }

    private fun list(
        d: JSONObject,
        kind: String,
        arr: JSONArray?,
    ): List<SwEntry> = if (arr == null) emptyList() else List(arr.length()) { arr.get(it) }.mapNotNull { entry(d, kind, it) }

    /** The film's extras, or null for anything that isn't a Star Wars film. */
    fun film(
        context: Context,
        id: UUID,
    ): SwFilm? {
        val d = data(context) ?: return null
        val f = d.optJSONObject("films")?.optJSONObject(id.hex()) ?: return null
        val c = f.optJSONObject("crawl")
        return SwFilm(
            planets = list(d, "planet", f.optJSONArray("planets")),
            chars = list(d, "char", f.optJSONArray("chars")),
            ships = list(d, "ship", f.optJSONArray("ships")),
            crawl =
                c?.let {
                    val t = it.getJSONArray("text")
                    SwCrawl(it.optString("episode"), it.optString("title"), List(t.length()) { i -> t.getString(i) })
                },
        )
    }

    /** A Star Wars show season's extras (planets, characters, starships, its place in the story), or null. */
    fun season(
        context: Context,
        id: UUID,
    ): SwSeason? {
        val d = data(context) ?: return null
        val s = d.optJSONObject("seasons")?.optJSONObject(id.hex()) ?: return null
        return SwSeason(
            k = s.optDouble("k"),
            year = s.optString("year"),
            planets = list(d, "planet", s.optJSONArray("planets")),
            chars = list(d, "char", s.optJSONArray("chars")),
            ships = list(d, "ship", s.optJSONArray("ships")),
        )
    }

    // Kept for older callers
    fun forFilm(
        context: Context,
        id: UUID,
    ): List<SwEntry> = film(context, id)?.planets.orEmpty()
}

/** Star Wars era for a SwTimeline value (years, BBY negative). */
fun swEra(k: Double): String =
    when {
        k <= -100 -> "The High Republic"
        k < -18.5 -> "Fall of the Jedi"
        k < -1 -> "Reign of the Empire"
        k < 5 -> "Age of Rebellion"
        k < 28 -> "The New Republic"
        else -> "Rise of the First Order"
    }

// ---- before / after on the timeline -------------------------------------------------------------------
data class SwTimelineItem(val id: UUID, val name: String, val key: Double, val year: String, val series: Boolean)

data class SwNeighbours(val before: SwTimelineItem?, val after: SwTimelineItem?)

private val SW_LIBRARY = hexToUuid("ddb2bb02ef627bc7aba27c2133fa15a0")

suspend fun swNeighbours(
    api: ApiClient,
    itemId: UUID,
): SwNeighbours? =
    runCatching {
        val groups = swTimelineGroups(api)
        val at = groups.indexOfFirst { g -> g.any { it.id == itemId } }
        if (at < 0) null else SwNeighbours(groups.getOrNull(at - 1)?.first(), groups.getOrNull(at + 1)?.first())
    }.getOrNull()

/** Before / after a show season at timeline place [k] (the show's own entry left out). */
suspend fun swNeighboursAt(
    api: ApiClient,
    seriesId: UUID,
    k: Double,
): SwNeighbours? =
    runCatching {
        val groups = swTimelineGroups(api, except = seriesId)
        SwNeighbours(groups.lastOrNull { it[0].key <= k }?.first(), groups.firstOrNull { it[0].key > k }?.first())
    }.getOrNull()

private suspend fun swTimelineGroups(
    api: ApiClient,
    except: UUID? = null,
): List<List<SwTimelineItem>> {
        swLoadPlaces(api)
        val items =
            api.libraryApi
                .getItems(
                    GetItemsRequest(
                        parentId = SW_LIBRARY,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        fields = listOf(ItemFields.PROVIDER_IDS),
                        enableImageTypes = listOf(ImageType.PRIMARY),
                        imageTypeLimit = 1,
                    ),
                ).content.items
        val tl =
            items
                .mapNotNull { x ->
                    val k = x.providerIds?.get("SwTimeline")?.toDoubleOrNull() ?: return@mapNotNull null
                    SwTimelineItem(x.id, x.name ?: "", k, x.providerIds?.get("SwYear") ?: "", x.type == BaseItemKind.SERIES)
                }.filter { it.id != except }
                .sortedBy { it.key }
        // copies of one film (35mm, extended, novel-named edits) sit within 0.05 of each other: one entry each
        val groups = mutableListOf<MutableList<SwTimelineItem>>()
        for (x in tl) {
            val g = groups.lastOrNull()
            if (g != null && x.key - g[0].key <= 0.05) g.add(x) else groups.add(mutableListOf(x))
        }
        return groups
}

// ---- pictures -------------------------------------------------------------------------------------------
// Jellyfin sometimes reshuffles the root folder's backdrop LIST (2026-11-16 and again by 2026-11-17), and
// pictures are asked for by their place in it, so the place in the data can go stale. A picture's tag never
// changes, so its current place is looked up by tag: the list is fetched once per app run (from the film /
// season page loading, see swTimelineGroups). Until it arrives, pictures stay empty instead of wrong.
private val SW_PICTURES = hexToUuid("e9d5075a555c1cbc394eec4cef295274")
private var swPlaces by mutableStateOf<Map<String, Int>?>(null)

private suspend fun swLoadPlaces(api: ApiClient) {
    if (swPlaces != null || SW_PICTURES == null) return
    runCatching {
        api.imageApi
            .getItemImageInfos(SW_PICTURES)
            .content
            .filter { it.imageType == ImageType.BACKDROP && it.imageTag != null && it.imageIndex != null }
            .associate { it.imageTag!! to it.imageIndex!! }
    }.onSuccess { swPlaces = it }
        .onFailure { swPlaces = emptyMap() }
}

@Composable
private fun swImageUrl(
    path: String,
    width: Int,
): String? {
    val service = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    val places = swPlaces
    return remember(path, width, places) {
        if (places == null) return@remember null
        // "?tag=": the picture's own tag, so a picture saved earlier for the same place is never shown instead
        val m = Regex("""Items/([0-9a-fA-F]{32})/Images/Backdrop/(\d+)(?:\?tag=(\w+))?""").find(path) ?: return@remember null
        val id = hexToUuid(m.groupValues[1]) ?: return@remember null
        val tag = m.groupValues[3].ifBlank { null }
        val index = tag?.let { places[it] } ?: m.groupValues[2].toInt()
        service.getItemImageUrl(itemId = id, imageType = ImageType.BACKDROP, maxWidth = width, imageIndex = index, tag = tag)
    }
}

// ---- rows ------------------------------------------------------------------------------------------------
@Composable
fun SwRow(
    title: String,
    entries: List<SwEntry>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    startCardFocusRequester: FocusRequester? = null,
) {
    val firstFocus = remember { FocusRequester() }
    val wide = entries.firstOrNull()?.kind == "ship"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
        LazyRow(
            state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, startIndex - 2)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(firstFocus),
        ) {
            itemsIndexed(entries) { index, e ->
                val m =
                    Modifier
                        .ifElse(index == startIndex, Modifier.focusRequester(firstFocus))
                        .ifElse(index == startIndex && startCardFocusRequester != null, Modifier.focusRequester(startCardFocusRequester ?: firstFocus))
                        .ifElse(index == entries.lastIndex, Modifier.focusProperties { right = FocusRequester.Cancel })
                if (wide) {
                    SwWideCard(e, onClick = { onClick(index) }, modifier = m)
                } else {
                    PersonCard(
                        name = e.name,
                        // characters: name only (it may wrap), species only in the card
                        role = if (e.kind == "char") null else e.sub,
                        nameLines = if (e.kind == "char") 3 else 1,
                        imageUrl = swImageUrl(e.image, 320),
                        favorite = false,
                        onClick = { onClick(index) },
                        onLongClick = {},
                        modifier = m.width(personRowCardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwWideCard(
    e: SwEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(213.dp)) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(containerColor = Color(0xFF050505)),
            modifier = Modifier.width(213.dp).height(120.dp),
        ) {
            AsyncImage(model = swImageUrl(e.image, 480), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
        }
        Text(e.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(e.sub, fontSize = 12.sp, color = Color(0xFF9CA3AF), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Before / after on the Star Wars timeline: two poster cards. */
@Composable
fun SwStoryRow(
    nb: SwNeighbours,
    onOpen: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text("In Story Order", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(8.dp)) {
            listOfNotNull(nb.before?.let { it to "Before" }, nb.after?.let { it to "After" }).forEach { (x, label) ->
                Column(Modifier.width(115.dp)) {
                    Card(onClick = { onOpen(x.id, x.series) }, modifier = Modifier.width(115.dp).height(172.dp)) {
                        AsyncImage(
                            model = remember(x.id) { service.getItemImageUrl(itemId = x.id, imageType = ImageType.PRIMARY, maxHeight = 600) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(x.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(label + if (x.year.isNotBlank()) " · " + x.year else "", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ---- the card --------------------------------------------------------------------------------------------
/**
 * A planet / character / starship card. Left/Right step through [entries]; Down moves to the "Also in" links
 * (OK opens that film or show, Up goes back); Back closes ([onDismiss] gets the last index shown).
 */
@Composable
fun SwCardDialog(
    entries: List<SwEntry>,
    startIndex: Int,
    currentId: UUID,
    onDismiss: (Int) -> Unit,
    onOpen: (SwLink) -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, entries.lastIndex)) }
    val e = entries[index]
    val links = remember(e) { e.seenIn.filter { it.id != currentId } }
    val focus = remember { FocusRequester() }
    val firstLink = remember { FocusRequester() }
    val more = mapOf("planet" to "planets", "char" to "characters", "ship" to "starships")[e.kind]
    Dialog(onDismissRequest = { onDismiss(index) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0B0B0B))
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 20.dp),
        ) {
            Box(
                Modifier
                    .focusRequester(focus)
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (it.key) {
                            Key.DirectionLeft -> { if (index > 0) index--; true }
                            Key.DirectionRight -> { if (index < entries.lastIndex) index++; true }
                            Key.DirectionDown -> { if (links.any { l -> l.id != null }) runCatching { firstLink.requestFocus() }; true }
                            Key.DirectionUp -> true
                            else -> false
                        }
                    }.focusable(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    val ship = e.kind == "ship"
                    AsyncImage(
                        model = swImageUrl(e.image, 600),
                        contentDescription = null,
                        contentScale = if (ship) ContentScale.Fit else ContentScale.Crop,
                        alignment = if (e.kind == "char") Alignment.TopCenter else Alignment.Center,
                        modifier =
                            if (ship) {
                                Modifier.size(220.dp, 150.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF050505))
                            } else {
                                Modifier.size(170.dp).clip(CircleShape).background(Color.Black)
                            },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        e.facts.forEach { (k, v) ->
                            Row(Modifier.padding(vertical = 1.dp)) {
                                Text(k, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF9CA3AF), modifier = Modifier.width(72.dp))
                                Text(v, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFE5E7EB))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(e.bio, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFFD1D5DB))
                    }
                }
            }
            if (links.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Also in:", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 3.dp, end = 4.dp))
                    var first = true
                    links.forEach { l ->
                        if (l.id == null) {
                            Text(l.name, fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 3.dp))
                        } else {
                            val isFirst = first
                            first = false
                            Surface(
                                onClick = { onOpen(l) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                                colors =
                                    ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = .1f),
                                        contentColor = Color(0xFFD1D5DB),
                                        focusedContainerColor = Color(0xFFD1D5DB),
                                        focusedContentColor = Color(0xFF131414),
                                    ),
                                modifier =
                                    Modifier
                                        .ifElse(isFirst, Modifier.focusRequester(firstLink))
                                        .onPreviewKeyEvent {
                                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                                runCatching { focus.requestFocus() }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                            ) {
                                Text(l.name, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val hint =
                    listOfNotNull(
                        if (entries.size > 1) "◀ ▶ more $more" else null,
                        if (links.any { it.id != null }) "▼ also in" else null,
                    ).joinToString(" · ")
                Text(hint, fontSize = 10.sp, color = Color(0xFF6B7280))
                Text("Picture: Wookieepedia", fontSize = 10.sp, color = Color(0xFF6B7280))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

// ---- opening crawl -----------------------------------------------------------------------------------
@Composable
fun SwCrawlDialog(
    crawl: SwCrawl,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var started by remember { mutableStateOf(false) }
    val intro by animateFloatAsState(if (started) 1f else 0f, tween(7000, easing = LinearEasing), label = "intro")
    val scroll by animateFloatAsState(if (started) 1f else 0f, tween(60000, delayMillis = 7000, easing = LinearEasing), label = "crawl")
    val stars = remember { List(160) { Offset(Random.nextFloat(), Random.nextFloat()) to (0.5f + Random.nextFloat()) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()) {
            val h = constraints.maxHeight.toFloat()
            Canvas(Modifier.fillMaxSize()) { stars.forEach { (o, r) -> drawCircle(Color.White.copy(alpha = .8f), r * 1.5f, Offset(o.x * size.width, o.y * size.height)) } }
            // "A long time ago..." fades in and out over the first seven seconds
            val introAlpha = when { intro < .15f -> intro / .15f; intro < .75f -> 1f; else -> ((1f - intro) / .25f).coerceAtLeast(0f) }
            Text(
                "A long time ago in a galaxy far,\nfar away....",
                color = Color(0xFF4BD5EE),
                fontSize = 22.sp,
                lineHeight = 31.sp,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = if (started) introAlpha else 0f },
            )
            val density = LocalDensity.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(560.dp)
                        .graphicsLayer {
                            alpha = if (scroll > 0f && scroll < .97f) 1f else if (scroll >= .97f) ((1f - scroll) / .03f) else 0f
                            rotationX = 28f
                            cameraDistance = 6f * density.density
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            translationY = h - scroll * h * 2.7f
                        },
            ) {
                val gold = Color(0xFFFEDA4A)
                Text(crawl.episode, color = gold, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(crawl.title, color = gold, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                crawl.text.forEach {
                    Text(it, color = gold, fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Justify, modifier = Modifier.padding(bottom = 20.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        started = true
    }
}
