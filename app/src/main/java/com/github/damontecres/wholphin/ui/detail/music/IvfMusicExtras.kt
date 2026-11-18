package com.github.damontecres.wholphin.ui.detail.music

import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * IvanaFin: music extras on ANY Jellyfin server (GitHub plan, 2026-11-18).
 *
 * The original library has them as hidden server ids written by jellyfin-web-custom/backup/music-artist-data.ps1
 * (album IvfType, artist IvfPopular). Other servers don't, so the artist page asks Deezer (free, no account -
 * user's choice) for the artist's top tracks and release types, and matches them to the songs and albums on
 * this server by title. Albums Deezer doesn't know are sorted by length (Spotify's rule: 1-3 songs under
 * 30 min = single, 4-6 under 30 min = EP). Server ids, where present, always win.
 */
internal object IvfMusicExtras {
    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

    /** Deezer's view of an artist: top track titles (most popular first) and (title, record_type) of releases */
    data class DeezerArtist(
        val top: List<String>,
        val releases: List<Pair<String, String>>,
    )

    // per app run; null = looked up, not found
    private val artists = ConcurrentHashMap<String, Optional>()

    private data class Optional(val value: DeezerArtist?)

    /** Album types found on Deezer this run (album id -> album / ep / single / compile) */
    val albumTypes = ConcurrentHashMap<UUID, String>()

    /** Lower case, no bracketed parts ("(feat. X)", "[Deluxe]"), letters and digits only */
    fun norm(s: String?): String =
        (s ?: "")
            .lowercase()
            .replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun get(url: String): JSONObject? =
        runCatching {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                if (!r.isSuccessful) null else JSONObject(r.body.string())
            }
        }.onFailure { Timber.w(it, "Deezer request failed") }.getOrNull()

    /** The artist on Deezer (exact name match only - never a different artist), or null */
    suspend fun artist(name: String?): DeezerArtist? {
        val key = norm(name)
        if (key.isEmpty()) return null
        artists[key]?.let { return it.value }
        val found =
            withContext(Dispatchers.IO) {
                val q = URLEncoder.encode(name, "UTF-8")
                val hits = get("https://api.deezer.com/search/artist?q=$q&limit=10")?.optJSONArray("data") ?: return@withContext null
                val id =
                    (0 until hits.length())
                        .map { hits.getJSONObject(it) }
                        .firstOrNull { norm(it.optString("name")) == key }
                        ?.optLong("id") ?: return@withContext null
                val top = get("https://api.deezer.com/artist/$id/top?limit=50")?.optJSONArray("data")
                val rel = get("https://api.deezer.com/artist/$id/albums?limit=300")?.optJSONArray("data")
                DeezerArtist(
                    top = if (top == null) emptyList() else (0 until top.length()).map { top.getJSONObject(it).optString("title") },
                    releases =
                        if (rel == null) {
                            emptyList()
                        } else {
                            (0 until rel.length()).map { rel.getJSONObject(it).let { r -> r.optString("title") to r.optString("record_type") } }
                        },
                )
            }
        artists[key] = Optional(found)
        return found
    }

    private val EP_SUFFIX = Regex("""(?i)\s*-?\s*\b(EP|Single)$""")

    /** Title for matching releases: "Dear Donda - EP" and "Dear Donda" are the same release */
    private fun relKey(t: String?) = norm((t ?: "").replace(EP_SUFFIX, ""))

    /**
     * Fill [albumTypes] for the albums with no server type, from Deezer's release list. Like the original
     * library (music-artist-data.ps1): a release Deezer doesn't have is a mixtape (leaks, bootlegs, tapes),
     * unless its title says EP. Checked against the original library (2026-11-18, 10 artists): 104 of 116 rows the same.
     */
    fun learnTypes(
        albums: List<BaseItem>,
        dz: DeezerArtist,
    ) {
        if (dz.releases.isEmpty()) return
        val byTitle = dz.releases.associate { (t, type) -> relKey(t) to type }
        for (a in albums) {
            if (a.data.providerIds?.get("IvfType") != null) continue
            albumTypes[a.id] = byTitle[relKey(a.name)]?.takeIf { it.isNotBlank() }
                ?: if (EP_SUFFIX.containsMatchIn(a.name ?: "")) "ep" else "mixtape"
        }
    }

    /** Up to 10 of [songs] in Deezer's popularity order (each title once) */
    fun popular(
        songs: List<BaseItem>,
        dz: DeezerArtist,
    ): List<BaseItem> {
        val left = songs.toMutableList()
        val out = mutableListOf<BaseItem>()
        for (t in dz.top) {
            val k = norm(t)
            if (k.isEmpty()) continue
            val i = left.indexOfFirst { norm(it.name) == k }
            if (i >= 0) out += left.removeAt(i)
            if (out.size == 10) break
        }
        return out
    }

    /** Spotify's rule when nothing else knows: 1-3 songs under 30 min = single, 4-6 under 30 min = EP */
    fun guessType(album: BaseItem): String? {
        val songs = album.data.childCount ?: return null
        val minutes = (album.data.runTimeTicks ?: 0L) / 600_000_000L
        val short = minutes < 30
        return when {
            songs in 1..3 && short -> "single"
            songs in 4..6 && short -> "ep"
            else -> null
        }
    }
}
