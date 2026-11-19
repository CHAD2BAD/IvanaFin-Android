package com.github.damontecres.wholphin.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URI

/**
 * IvanaFin: "Cancel" on a Discover title goes to the IvanaFin Seerr helper (jellyfin-web-custom
 * server/seerr-helper, port 5056 on the Jellyfin server's host) when that server has one. Seerr itself
 * only deletes the request record, so an approved title kept downloading; the helper also stops it in
 * Radarr/Sonarr (user 2026-11-19, same on web/Samsung). It checks the Jellyfin sign-in itself.
 */
object IvfSeerrHelper {
    /**
     * @return true = cancelled, false = the helper said no (error shown by the caller),
     * null = no helper on this server (use Seerr's own request delete)
     */
    suspend fun cancel(
        api: ApiClient,
        type: String,
        tmdbId: Int,
    ): Boolean? =
        withContext(Dispatchers.IO) {
            val token = api.accessToken ?: return@withContext null
            val host = runCatching { URI(api.baseUrl ?: "").host }.getOrNull() ?: return@withContext null
            try {
                val conn = URI("http://$host:5056/cancel").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 60000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "MediaBrowser Token=\"$token\"")
                conn.outputStream.use { it.write("""{"type":"$type","tmdbId":$tmdbId}""".toByteArray()) }
                val code = conn.responseCode
                val body = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
                Timber.i("IvanaFin Seerr helper cancel %s %s -> %s %s", type, tmdbId, code, body)
                when {
                    code in 200..299 -> true
                    code == 404 && body?.contains("\"ok\"") != true -> null // something else on that port
                    else -> false
                }
            } catch (ex: Exception) {
                Timber.i("IvanaFin Seerr helper not reachable: %s", ex.message)
                null
            }
        }
}
