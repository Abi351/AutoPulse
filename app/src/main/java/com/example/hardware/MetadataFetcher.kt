package com.example.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.data.db.AudioTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer

data class FetchTrackResult(
    val track: AudioTrackEntity,
    val wasUpdated: Boolean,
)

object MetadataFetcher {

    private const val COVER_MAX_SIZE_PX = 256
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 7_000

    private val AUDIO_EXT_REGEX = Regex("\\.(mp3|flac|m4a|wav|ogg|aac|wma|opus)$", RegexOption.IGNORE_CASE)
    private val LEADING_TRACK_NUM_REGEX = Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*")
    private val BRACKETS_REGEX = Regex("\\[[^\\]]*]|\\([^)]*\\)")
    private val NOISE_WORDS_REGEX = Regex(
        "\\b(official\\s+video|official\\s+audio|video\\s+song|full\\s+video|lyric\\s+video|lyrical|full\\s+song|audio|hd|4k|hq|feat\\.?|ft\\.?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SEPARATORS_REGEX = Regex("[_|/]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val YEAR_REGEX = Regex("(19|20)\\d{2}")

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    fun cleanTrackName(raw: String): String {
        var cleaned = raw.trim()
        cleaned = AUDIO_EXT_REGEX.replace(cleaned, "")
        cleaned = LEADING_TRACK_NUM_REGEX.replace(cleaned, "")
        cleaned = BRACKETS_REGEX.replace(cleaned, " ")
        cleaned = SEPARATORS_REGEX.replace(cleaned, " ")
        cleaned = NOISE_WORDS_REGEX.replace(cleaned, " ")
        cleaned = cleaned.replace(" - ", " ")
        cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim(' ', '-', '.', ',')
        return cleaned.ifBlank { raw.substringBeforeLast('.').ifBlank { "Piste audio" } }
    }

    fun buildSearchQuery(title: String, artist: String): String {
        val cleanTitle = cleanTrackName(title)
        val cleanArtist = if (
            artist.isBlank() ||
            artist.equals("Artiste inconnu", ignoreCase = true) ||
            artist.equals("Unknown", ignoreCase = true) ||
            artist.equals("<unknown>", ignoreCase = true)
        ) {
            ""
        } else {
            val noNoiseArtist = NOISE_WORDS_REGEX.replace(
                BRACKETS_REGEX.replace(artist, " "),
                " ",
            )
            MULTI_SPACE_REGEX.replace(SEPARATORS_REGEX.replace(noNoiseArtist, " "), " ").trim()
        }
        return listOf(cleanArtist, cleanTitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    /**
     * Requête simplifiée contenant les 3 ou 4 premiers mots significatifs du titre nettoyé.
     */
    fun buildSimplifiedQuery(title: String): String {
        val cleaned = cleanTrackName(title)
        val words = cleaned.split(' ')
            .map { it.trim('-', '.', ',', '!', '?') }
            .filter { it.length > 1 }
        val significant = if (words.isNotEmpty()) words else cleaned.split(' ').filter { it.isNotBlank() }
        return significant.take(4).joinToString(" ").trim()
    }

    fun isUnknownArtist(artist: String?): Boolean {
        if (artist.isNullOrBlank()) return true
        val trimmed = artist.trim()
        return trimmed.equals("Artiste inconnu", ignoreCase = true) ||
            trimmed.equals("Unknown", ignoreCase = true) ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("Unknown Artist", ignoreCase = true)
    }

    private fun normalizeForMatch(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun extractKeywords(raw: String): List<String> {
        val stopWords = setOf("the", "and", "les", "des", "une", "for", "with", "feat", "ft")
        val tokens = normalizeForMatch(raw).split(' ').filter { it.isNotBlank() }
        val longTokens = tokens.filter { it.length >= 2 && it !in stopWords }
        return if (longTokens.isNotEmpty()) longTokens else tokens
    }

    /**
     * Vérifie qu'un résultat iTunes correspond à l'artiste déjà connu (et idéalement au titre).
     * Empêche d'écraser ou d'associer une pochette d'un autre artiste pour un titre homonyme.
     */
    fun doesItunesResultMatch(
        knownArtist: String,
        trackTitle: String,
        apiArtistName: String?,
        apiTrackName: String?,
    ): Boolean {
        if (isUnknownArtist(knownArtist)) return true
        val normApiArtist = normalizeForMatch(apiArtistName.orEmpty())
        val normApiTrack = normalizeForMatch(apiTrackName.orEmpty())
        if (normApiArtist.isBlank() && normApiTrack.isBlank()) return false

        val artistKeywords = extractKeywords(cleanTrackName(knownArtist))
        val artistMatches = artistKeywords.isNotEmpty() && artistKeywords.any { kw ->
            normApiArtist.contains(kw) || normApiTrack.contains(kw)
        }
        if (!artistMatches) return false

        val titleKeywords = extractKeywords(cleanTrackName(trackTitle))
        if (titleKeywords.isEmpty()) return true
        return titleKeywords.any { kw ->
            normApiTrack.contains(kw) || normApiArtist.contains(kw)
        }
    }

    fun extractYear(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return YEAR_REGEX.find(raw)?.value ?: ""
    }

    fun compressAndSaveCoverRgb565(
        context: Context,
        rawBytes: ByteArray,
        cacheKey: String,
    ): String? {
        if (rawBytes.isEmpty()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            val maxOriginal = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxOriginal / sampleSize > COVER_MAX_SIZE_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: return null

            val resized = scaleBitmapRgb565(decoded, COVER_MAX_SIZE_PX)
            if (resized !== decoded) {
                decoded.recycle()
            }

            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val fileName = "${sha256Short(cacheKey)}.jpg"
            val outFile = File(coversDir, fileName)
            FileOutputStream(outFile).use { fos ->
                resized.compress(Bitmap.CompressFormat.JPEG, 86, fos)
            }
            resized.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    fun loadCoverBitmapRgb565(coverPath: String?): Bitmap? {
        if (coverPath.isNullOrBlank()) return null
        val file = File(coverPath)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun scaleBitmapRgb565(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim && source.config == Bitmap.Config.RGB_565) {
            return source
        }
        val ratio = minOf(maxDim.toFloat() / w.coerceAtLeast(1), maxDim.toFloat() / h.coerceAtLeast(1))
            .coerceAtMost(1f)
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        return if (scaled.config == Bitmap.Config.RGB_565) {
            scaled
        } else {
            val copy = scaled.copy(Bitmap.Config.RGB_565, false)
            if (scaled !== source) scaled.recycle()
            copy
        }
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    suspend fun fetchOnlineMetadataForTrack(
        context: Context,
        track: AudioTrackEntity,
        forceRedownload: Boolean,
    ): FetchTrackResult = withContext(Dispatchers.IO) {
        val hasValidCover = !track.coverPath.isNullOrBlank() && File(track.coverPath).exists()
        val knownArtist = !isUnknownArtist(track.artist)
        val hasCompleteInfo = hasValidCover &&
            knownArtist &&
            !track.genre.equals("Inconnu", ignoreCase = true) &&
            track.year.isNotBlank()

        if (!forceRedownload && hasCompleteInfo) {
            return@withContext FetchTrackResult(track, wasUpdated = false)
        }

        val cleanTitle = cleanTrackName(track.title)
        val primaryQuery = buildSearchQuery(track.title, track.artist)
        val simplifiedQuery = if (knownArtist) {
            listOf(
                cleanTrackName(track.artist),
                buildSimplifiedQuery(cleanTitle),
            ).filter { it.isNotBlank() }.joinToString(" ").trim()
        } else {
            buildSimplifiedQuery(cleanTitle)
        }

        var item = findBestItunesMatch(
            query = primaryQuery,
            knownArtist = track.artist,
            trackTitle = cleanTitle,
        )
        if (item == null && simplifiedQuery.isNotBlank() && simplifiedQuery != primaryQuery) {
            delay(400L)
            item = findBestItunesMatch(
                query = simplifiedQuery,
                knownArtist = track.artist,
                trackTitle = cleanTitle,
            )
        }

        if (item == null) {
            return@withContext FetchTrackResult(track, wasUpdated = false)
        }

        val apiTitle = item.optString("trackName").takeIf { it.isNotBlank() }
        val apiArtist = item.optString("artistName").takeIf { it.isNotBlank() }
        val apiAlbum = item.optString("collectionName").takeIf { it.isNotBlank() }
        val apiGenre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
        val apiYear = extractYear(item.optString("releaseDate"))
        val rawArtUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() }
        val artUrl256 = rawArtUrl?.replace("100x100bb", "256x256bb")

        var newCoverPath = track.coverPath
        if ((forceRedownload || !hasValidCover) && !artUrl256.isNullOrBlank()) {
            val imageBytes = downloadBytesWithRetry(artUrl256)
            if (imageBytes != null) {
                val saved = compressAndSaveCoverRgb565(
                    context = context,
                    rawBytes = imageBytes,
                    cacheKey = "${track.uriString}_${track.id}",
                )
                if (saved != null) {
                    newCoverPath = saved
                }
            }
        }

        // Ne jamais écraser un artiste déjà valide par un autre artiste
        val updatedArtist = if (apiArtist != null && !knownArtist) {
            apiArtist
        } else {
            track.artist
        }

        val updatedAlbum = if (
            apiAlbum != null &&
            (track.album.isBlank() || track.album == track.folderName)
        ) {
            apiAlbum
        } else {
            track.album
        }

        val updatedGenre = if (
            apiGenre != null &&
            (track.genre.isBlank() || track.genre.equals("Inconnu", ignoreCase = true))
        ) {
            apiGenre
        } else {
            track.genre
        }

        val updatedYear = if (apiYear.isNotBlank() && track.year.isBlank()) {
            apiYear
        } else {
            track.year
        }

        val updatedTitle = if (
            apiTitle != null &&
            (track.title.isBlank() || track.title.equals("Piste audio", ignoreCase = true))
        ) {
            apiTitle
        } else {
            track.title
        }

        val updatedTrack = track.copy(
            title = updatedTitle,
            artist = updatedArtist,
            album = updatedAlbum,
            genre = updatedGenre,
            year = updatedYear,
            coverPath = newCoverPath,
        )

        FetchTrackResult(updatedTrack, wasUpdated = updatedTrack != track)
    }

    private suspend fun findBestItunesMatch(
        query: String,
        knownArtist: String,
        trackTitle: String,
    ): JSONObject? {
        val candidates = queryItunesSongsWithRetry(query)
        if (candidates.isEmpty()) return null
        if (isUnknownArtist(knownArtist)) {
            return candidates.firstOrNull()
        }
        return candidates.firstOrNull { obj ->
            doesItunesResultMatch(
                knownArtist = knownArtist,
                trackTitle = trackTitle,
                apiArtistName = obj.optString("artistName"),
                apiTrackName = obj.optString("trackName"),
            )
        }
    }

    private suspend fun queryItunesSongsWithRetry(query: String): List<JSONObject> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=5"

        repeat(2) { attempt ->
            var shouldRetry = false
            val parsed = try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "AutoPulseHeadUnit/1.0")
                }
                try {
                    val code = conn.responseCode
                    if (code == 429 || code == HttpURLConnection.HTTP_UNAVAILABLE) {
                        shouldRetry = true
                        emptyList()
                    } else if (code != HttpURLConnection.HTTP_OK) {
                        emptyList()
                    } else {
                        val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(jsonText)
                        val results = root.optJSONArray("results") ?: return emptyList()
                        buildList {
                            for (i in 0 until results.length()) {
                                results.optJSONObject(i)?.let { add(it) }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                shouldRetry = true
                emptyList()
            } catch (_: IOException) {
                shouldRetry = true
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            if (!shouldRetry || attempt == 1) {
                return parsed
            }
            delay(RETRY_BACKOFF_MS)
        }
        return emptyList()
    }

    private suspend fun downloadBytesWithRetry(urlString: String): ByteArray? {
        repeat(2) { attempt ->
            var shouldRetry = false
            val bytes = try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "AutoPulseHeadUnit/1.0")
                }
                try {
                    val code = conn.responseCode
                    if (code == 429 || code == HttpURLConnection.HTTP_UNAVAILABLE) {
                        shouldRetry = true
                        null
                    } else if (code != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        conn.inputStream.use { input ->
                            val buffer = ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = input.read(chunk)
                                if (read <= 0) break
                                total += read
                                if (total > 2 * 1024 * 1024) return null
                                buffer.write(chunk, 0, read)
                            }
                            buffer.toByteArray()
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                shouldRetry = true
                null
            } catch (_: IOException) {
                shouldRetry = true
                null
            } catch (_: Exception) {
                null
            }

            if (!shouldRetry || attempt == 1) {
                return bytes
            }
            delay(RETRY_BACKOFF_MS)
        }
        return null
    }

    const val REQUEST_THROTTLE_MS = 400L
    private const val RETRY_BACKOFF_MS = 2_000L
}
