package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * IvanaFin Music colour (2026-11-12, Apple Music style - same recipe as the web/Samsung music-page.js
 * palette()): every page takes its colours from its picture.
 *   edge: the page colour - the cover's own background, the commonest colour round its edge (the
 *         outer 2 of 24 pixels); black when most of the edge is black; light colours taken down only
 *         as far as white text needs
 *   acc:  the accent (Play, the playing song, favourites) - the cover's most vivid colour
 *   ink:  text on the accent
 *   wash: a see-through tint for cards (shortcut cards, library tiles)
 */

@Stable
data class MuPal(
    val edge: Color,
    val acc: Color,
    val ink: Color,
    val wash: Color? = null,
    /** Browse tiles only: the tile's own colour */
    val tile: Color? = null,
)

internal val MuBgBase = Color(0xFF121212)
internal val MuPink = Color(0xFFFA586A)
internal val DefaultPal = MuPal(MuBgBase, MuPink, Color.White)
internal val LikedPal = MuPal(Color.hsl(252f, .48f, .27f), Color.hsl(252f, .9f, .74f), Color(0xFF111111), Color.hsl(252f, .48f, .34f, .62f))

/** A page colour from a hue (Browse tiles and their pages) */
internal fun huePal(h: Float) = MuPal(Color.hsl(h, .45f, .22f), Color.hsl(h, .85f, .66f), Color(0xFF111111), Color.hsl(h, .45f, .32f, .62f), Color.hsl(h, .62f, .40f))

private val palettes = ConcurrentHashMap<String, MuPal>()

private suspend fun smallBitmap(
    context: Context,
    url: String,
): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(context).data(url).size(96).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

/** The colours of a picture (cached). "liked" = the Liked Songs purple. */
internal suspend fun muPalette(
    context: Context,
    url: String?,
): MuPal {
    if (url == null) return DefaultPal
    if (url == "liked") return LikedPal
    palettes[url]?.let { return it }
    val bmp = smallBitmap(context, url) ?: return DefaultPal.also { palettes[url] = it }
    val pal = withContext(Dispatchers.Default) { paletteOf(bmp) }
    palettes[url] = pal
    return pal
}

private fun paletteOf(src: Bitmap): MuPal {
    val bmp = Bitmap.createScaledBitmap(src, 24, 24, true)
    val n = 24 * 24
    val rs = IntArray(n)
    val gs = IntArray(n)
    val bs = IntArray(n)
    // the accent: the average hue of the most vivid pixels
    val hs = FloatArray(n)
    val ss = FloatArray(n)
    val score = FloatArray(n)
    for (k in 0 until n) {
        val p = bmp.getPixel(k % 24, k / 24)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        rs[k] = r
        gs[k] = g
        bs[k] = b
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val dd = mx - mn
        var h = 0f
        if (dd > 0f) {
            h = (if (mx == rf) ((gf - bf) / dd) % 6 else if (mx == gf) (bf - rf) / dd + 2 else (rf - gf) / dd + 4) * 60
            if (h < 0) h += 360
        }
        val s = if (mx > 0f) dd / mx else 0f
        hs[k] = h
        ss[k] = s
        score[k] = s * (if (mx > .18f) min(1f, mx * 1.3f) else 0f) * (if (mx > .97f && s < .08f) 0f else 1f)
    }
    val order = (0 until n).sortedByDescending { score[it] }
    val top = order.take(max(8, (n * .12f).roundToInt()))
    var sx = 0.0
    var sy = 0.0
    var sSum = 0f
    top.forEach {
        sx += cos(Math.toRadians(hs[it].toDouble())) * score[it]
        sy += sin(Math.toRadians(hs[it].toDouble())) * score[it]
        sSum += ss[it]
    }
    val hue = ((Math.toDegrees(atan2(sy, sx)) + 360) % 360).roundToInt().toFloat()
    val sat = sSum / top.size
    val vivid = sat >= .14f
    val yg = hue > 40 && hue < 170
    val acc = if (vivid) Color.hsl(hue, max(.55f, min(.9f, sat * 1.1f)), if (yg) .52f else .62f) else Color.White
    val ink = if (vivid && yg) Color.White else Color(0xFF111111)

    // the page: the commonest non-black colour round the edge; black when most of the edge is black
    data class Bucket(var n: Int = 0, var r: Int = 0, var g: Int = 0, var b: Int = 0)
    val buckets = HashMap<Int, Bucket>()
    var best: Bucket? = null
    var edgeN = 0
    var edgeDark = 0
    for (k in 0 until n) {
        val ex = k % 24
        val ey = k / 24
        if (ex in 2..21 && ey in 2..21) continue
        edgeN++
        val r = rs[k]
        val g = gs[k]
        val b = bs[k]
        if (r < 39 && g < 39 && b < 39) {
            edgeDark++
            continue
        }
        val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
        val bk = buckets.getOrPut(key) { Bucket() }
        bk.n++
        bk.r += r
        bk.g += g
        bk.b += b
        if (best == null || bk.n > best!!.n) best = bk
    }
    if (edgeDark >= edgeN * .6f || best == null) return MuPal(Color.Black, acc, ink, Color.Black.copy(alpha = .55f))
    val bb = best!!
    val er = bb.r / bb.n / 255f
    val eg = bb.g / bb.n / 255f
    val eb = bb.b / bb.n / 255f
    val emx = max(er, max(eg, eb))
    val emn = min(er, min(eg, eb))
    val el = (emx + emn) / 2
    val ed = emx - emn
    var eh = 0f
    if (ed > 0f) {
        eh = (if (emx == er) ((eg - eb) / ed) % 6 else if (emx == eg) (eb - er) / ed + 2 else (er - eg) / ed + 4) * 60
        if (eh < 0) eh += 360
    }
    eh = eh.roundToInt().toFloat() % 360
    val eyg = eh > 40 && eh < 170 && ed > .12f
    // yellows / greens look brighter: a bit darker
    val nl = max(.14f, min(el, if (eyg) .24f else .3f))
    // keep the colour's strength (chroma), not its HSL saturation: a near-white edge with a faint
    // tint becomes grey when darkened, not a strong colour
    val es = min(.75f, ed / (1 - abs(2 * nl - 1)))
    return MuPal(Color.hsl(eh, es, nl), acc, ink, Color.hsl(eh, es, min(nl + .08f, .36f), .7f))
}

/** The Music page colour: pages set it (from a picture, or a preset); the colours fade to it. */
@Stable
class MuTheme(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var target by mutableStateOf(DefaultPal)
        private set
    private var req = 0

    fun setUrl(url: String?) {
        val id = ++req
        scope.launch {
            val p = muPalette(context, url)
            if (id == req) target = p
        }
    }

    fun setPal(p: MuPal) {
        ++req
        target = p
    }
}

/** The faded (animated) colours everything draws with */
@Stable
class MuColors(
    val edge: State<Color>,
    val acc: State<Color>,
    val ink: State<Color>,
)

private val fixedColors = MuColors(mutableStateOf(MuBgBase), mutableStateOf(MuPink), mutableStateOf(Color.White))
internal val LocalMuTheme = staticCompositionLocalOf<MuTheme?> { null }
internal val LocalMuColors = staticCompositionLocalOf { fixedColors }

/** The accent (was Spotify green) */
internal val MuGreen: Color
    @Composable get() = LocalMuColors.current.acc.value

/** The page colour */
internal val MuBg: Color
    @Composable get() = LocalMuColors.current.edge.value

internal val MuInk: Color
    @Composable get() = LocalMuColors.current.ink.value

/** A picture's colours, for a card's own tint (null until known) */
@Composable
internal fun rememberMuPal(url: String?): MuPal? {
    val context = LocalContext.current
    return produceState<MuPal?>(null, url) { value = muPalette(context, url) }.value
}

/** Sets the page colour from [url] while this is shown */
@Composable
internal fun MuTint(url: String?) {
    val theme = LocalMuTheme.current ?: return
    LaunchedEffect(url) { theme.setUrl(url) }
}

@Composable
internal fun MuTintPal(pal: MuPal) {
    val theme = LocalMuTheme.current ?: return
    LaunchedEffect(pal) { theme.setPal(pal) }
}

/** Library / Search: the page colour moves through [urls] every 7 s (shuffled, the first one first) */
@Composable
internal fun MuCycle(urls: List<String>) {
    val theme = LocalMuTheme.current ?: return
    LaunchedEffect(urls) {
        if (urls.isEmpty()) {
            theme.setUrl(null)
            return@LaunchedEffect
        }
        val order = listOf(urls.first()) + urls.drop(1).shuffled()
        var i = 0
        theme.setUrl(order[0])
        while (order.size > 1) {
            kotlinx.coroutines.delay(7000)
            i = (i + 1) % order.size
            theme.setUrl(order[i])
        }
    }
}
