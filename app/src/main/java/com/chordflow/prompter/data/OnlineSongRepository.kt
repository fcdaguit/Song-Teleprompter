package com.chordflow.prompter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A GitHub repository that holds open ChordPro / OnSong song files. */
data class SongCatalog(
    val label: String,
    val owner: String,
    val repo: String,
    val branch: String,
    val note: String = ""
) {
    val slug: String get() = "$owner/$repo"
}

/** One downloadable song file found in a catalog. */
data class OnlineSong(
    val title: String,
    val path: String,
    val rawUrl: String,
    val catalogLabel: String,
    val sizeBytes: Int?
)

@Serializable
private data class GitTree(
    @SerialName("tree") val tree: List<GitTreeNode> = emptyList(),
    @SerialName("truncated") val truncated: Boolean = false
)

@Serializable
private data class GitTreeNode(
    @SerialName("path") val path: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("size") val size: Int? = null
)

/**
 * Fetches open-licence worship song files from public GitHub repositories.
 *
 * This is the only part of the app that touches the network, and it is only
 * ever called from the "Download Free Songs" screen. Every failure is returned
 * as a [Result] so the UI can show a calm offline message instead of crashing
 * during a service.
 */
class OnlineSongRepository(private val context: Context) {

    companion object {
        /**
         * Starter catalogs. These are editable in the UI because public
         * repositories get renamed and restructured; if one returns "not
         * found", point the custom-repo field at another and carry on.
         */
        val DEFAULT_CATALOGS = listOf(
            SongCatalog(
                label = "WorshipCommonsContent",
                owner = "ChurchApps",
                repo = "WorshipCommonsContent",
                branch = "main",
                note = "Public-domain worship catalog from the ChurchApps project."
            ),
            SongCatalog(
                label = "OpenSong hymns",
                owner = "opensong",
                repo = "opensong-songs",
                branch = "master",
                note = "Community hymn and chord-sheet collection."
            )
        )

        private val SONG_EXTENSIONS = listOf(".cho", ".chopro", ".chord", ".pro", ".onsong", ".txt")
        private const val MAX_RESULTS = 400
        private const val MAX_FILE_BYTES = 512 * 1024
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** List every song-shaped file in a catalog repository. */
    suspend fun listCatalog(catalog: SongCatalog): Result<List<OnlineSong>> =
        withContext(Dispatchers.IO) {
            if (!isOnline()) {
                return@withContext Result.failure(
                    IOException("No internet connection. Song download needs Wi-Fi or mobile data.")
                )
            }
            val url = "https://api.github.com/repos/${catalog.owner}/${catalog.repo}" +
                "/git/trees/${catalog.branch}?recursive=1"
            try {
                val body = httpGet(url, accept = "application/vnd.github+json")
                val tree = json.decodeFromString(GitTree.serializer(), body)
                val songs = tree.tree.asSequence()
                    .filter { it.type == "blob" }
                    .filter { node ->
                        val lower = node.path.lowercase()
                        SONG_EXTENSIONS.any { lower.endsWith(it) }
                    }
                    .filter { (it.size ?: 0) <= MAX_FILE_BYTES }
                    .take(MAX_RESULTS)
                    .map { node ->
                        OnlineSong(
                            title = titleFromPath(node.path),
                            path = node.path,
                            rawUrl = "https://raw.githubusercontent.com/${catalog.owner}/" +
                                "${catalog.repo}/${catalog.branch}/${encodePath(node.path)}",
                            catalogLabel = catalog.label,
                            sizeBytes = node.size
                        )
                    }
                    .sortedBy { it.title.lowercase() }
                    .toList()

                if (songs.isEmpty()) {
                    Result.failure(
                        IOException(
                            "No .cho / .onsong / .txt song files found in ${catalog.slug} " +
                                "on branch ${catalog.branch}."
                        )
                    )
                } else {
                    Result.success(songs)
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /** Download the raw text of one song file. */
    suspend fun downloadSongText(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(
                IOException("No internet connection. Song download needs Wi-Fi or mobile data.")
            )
        }
        try {
            val text = httpGet(rawUrl, accept = "text/plain")
            if (text.isBlank()) {
                Result.failure(IOException("That file came back empty."))
            } else {
                Result.success(text)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Accepts either a raw.githubusercontent.com URL or a normal GitHub blob
     * URL and returns something that can be downloaded as plain text.
     */
    fun normaliseRawUrl(input: String): String {
        val url = input.trim()
        if (url.startsWith("https://raw.githubusercontent.com/")) return url
        if (url.startsWith("https://github.com/") && url.contains("/blob/")) {
            return url
                .replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("/blob/", "/")
        }
        return url
    }

    fun titleFromPath(path: String): String {
        val fileName = path.substringAfterLast('/')
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        return withoutExtension
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
            .ifBlank { fileName }
    }

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    private fun httpGet(url: String, accept: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", "ChordFlowPrompter/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(describeHttpFailure(response.code, url))
            }
            return body
        }
    }

    private fun describeHttpFailure(code: Int, url: String): String = when (code) {
        404 -> "Not found (404). That repository, branch or file does not exist at $url"
        403 -> "GitHub rate limit or access denied (403). Try again in a few minutes."
        in 500..599 -> "GitHub server error ($code). Try again later."
        else -> "Download failed with HTTP $code."
    }
}
