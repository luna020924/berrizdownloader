package app.berrizdownloader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            BerrizApp()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }
}

data class UiState(
    val link: String = "",
    val status: String = "링크를 넣고 영상을 확인하세요.",
    val detail: String = "",
    val loginStatus: LoginStatus = LoginStatus.Unknown,
    val isBusy: Boolean = false,
    val progress: Float? = null,
    val etaSeconds: Long = -1,
    val outputPath: String = "",
    val showLogin: Boolean = false,
    val previewTitle: String = "",
    val previewThumbnailUrl: String = "",
    val previewReady: Boolean = false,
    val isThumbnailBusy: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.System,
)

enum class LoginStatus {
    Unknown,
    LoggedOut,
    LoggedIn,
}

enum class AppThemeMode(val label: String) {
    System("시스템"),
    Light("화이트"),
    Dark("다크"),
}

object DownloadServiceContract {
    const val ACTION_START = "app.berrizdownloader.action.START_DOWNLOAD"
    const val ACTION_CANCEL = "app.berrizdownloader.action.CANCEL_DOWNLOAD"
    const val ACTION_STATE = "app.berrizdownloader.action.DOWNLOAD_STATE"
    const val EXTRA_PLAYBACK_URL = "playback_url"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MEDIA_ID = "media_id"
    const val EXTRA_STATUS = "status"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_OUTPUT = "output"
    const val EXTRA_BUSY = "busy"
}

data class BerrizMedia(
    val pageUrl: String,
    val type: String,
    val id: String,
)

data class PlaybackInfo(
    val title: String,
    val hlsUrl: String,
    val dashUrl: String,
    val isDrm: Boolean,
    val thumbnailUrl: String,
)

data class PreparedDownload(
    val media: BerrizMedia,
    val playback: PlaybackInfo,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "BerrizDown"
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val preferences = application.getSharedPreferences("berrizdown_settings", Context.MODE_PRIVATE)

    private val mediaRegex = Regex(
        """(?:https?://)?(?:www\.)?(?:link\.)?berriz\.in/(ko|en)/(?:web/main/)?([A-Za-z0-9_-]+)/(media/content|live/replay)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:[/?#][^\s]*)?""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val processId = "berrizdown-active"
    private val concurrentFragments = 8
    private val galleryCopyBufferSize = 1024 * 1024
    private val downloadStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadServiceContract.ACTION_STATE) return
            val progress = intent.getFloatExtra(DownloadServiceContract.EXTRA_PROGRESS, -1f)
                .takeIf { it >= 0f }
            _state.update {
                it.copy(
                    isBusy = intent.getBooleanExtra(DownloadServiceContract.EXTRA_BUSY, it.isBusy),
                    progress = progress,
                    outputPath = intent.getStringExtra(DownloadServiceContract.EXTRA_OUTPUT).orEmpty(),
                    status = intent.getStringExtra(DownloadServiceContract.EXTRA_STATUS) ?: it.status,
                    detail = intent.getStringExtra(DownloadServiceContract.EXTRA_DETAIL) ?: it.detail,
                )
            }
        }
    }
    private var ytdlpReady = false
    private var preparedDownload: PreparedDownload? = null

    init {
        _state.update { it.copy(themeMode = savedThemeMode()) }
        ContextCompat.registerReceiver(
            application,
            downloadStateReceiver,
            IntentFilter(DownloadServiceContract.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshLoginStatus()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(downloadStateReceiver) }
        super.onCleared()
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    private fun savedThemeMode(): AppThemeMode {
        val value = preferences.getString("theme_mode", AppThemeMode.System.name)
        return runCatching { AppThemeMode.valueOf(value ?: AppThemeMode.System.name) }
            .getOrDefault(AppThemeMode.System)
    }

    fun setLink(value: String) {
        val next = value.trim()
        preparedDownload = null
        _state.update {
            it.copy(
                link = next,
                outputPath = "",
                progress = null,
                previewTitle = "",
                previewThumbnailUrl = "",
                previewReady = false,
                isThumbnailBusy = false,
                status = if (next.isBlank()) "링크를 넣고 영상을 확인하세요." else "영상 확인을 눌러주세요.",
                detail = "",
            )
        }
    }

    fun downloadThumbnail() {
        val thumbnailUrl = state.value.previewThumbnailUrl
        if (thumbnailUrl.isBlank() || state.value.isThumbnailBusy) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isThumbnailBusy = true,
                    status = "썸네일 저장 중입니다.",
                    detail = "잠시만 기다려주세요.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    saveThumbnailToGallery(
                        thumbnailUrl = thumbnailUrl,
                        title = state.value.previewTitle,
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        isThumbnailBusy = false,
                        status = "썸네일을 저장했습니다.",
                        detail = "사진첩에서 볼 수 있습니다.",
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isThumbnailBusy = false,
                        status = "썸네일을 저장하지 못했습니다.",
                        detail = friendlyError(error),
                    )
                }
            }
        }
    }

    fun pasteFromClipboard() {
        val text = clipboardText()
        if (text.isNotBlank()) {
            val media = parseLink(text)
            val nextLink = media?.pageUrl ?: text.trim()
            setLink(nextLink)
            _state.update {
                it.copy(
                    status = if (media == null) {
                        "링크를 다시 확인해주세요."
                    } else {
                        "링크를 찾았습니다. 영상을 확인해 주세요."
                    },
                    detail = "",
                )
            }
        } else {
            _state.update { it.copy(status = "클립보드가 비어 있습니다.", detail = "") }
        }
    }

    fun openLogin() {
        _state.update { it.copy(showLogin = true) }
    }

    fun closeLogin() {
        CookieManager.getInstance().flush()
        refreshLoginStatus()
        _state.update { it.copy(showLogin = false) }
    }

    fun refreshLoginStatus() {
        val cookies = berrizCookies()
        _state.update {
            it.copy(
                loginStatus = when {
                    cookies.contains("auth_status=authenticated") -> LoginStatus.LoggedIn
                    cookies.isNotBlank() -> LoginStatus.Unknown
                    else -> LoginStatus.LoggedOut
                }
            )
        }
    }

    fun cancelDownload() {
        val app = getApplication<Application>()
        app.startService(Intent(app, DownloadService::class.java).setAction(DownloadServiceContract.ACTION_CANCEL))
        _state.update {
            it.copy(
                isBusy = false,
                progress = null,
                status = "다운로드를 중단했습니다.",
                detail = "",
            )
        }
    }

    fun startDownload() {
        val current = state.value.link.ifBlank { clipboardText() }
        val media = parseLink(current)
        if (media == null) {
            _state.update {
                it.copy(
                    status = "지원하지 않는 링크입니다.",
                    detail = "Berriz 영상 상세 링크를 넣어주세요.",
                )
            }
            return
        }
        if (state.value.link != media.pageUrl) {
            _state.update { it.copy(link = media.pageUrl) }
        }

        val cookies = berrizCookies()
        if (cookies.isBlank()) {
            _state.update {
                it.copy(
                    loginStatus = LoginStatus.LoggedOut,
                    status = "로그인이 필요합니다.",
                    detail = "로그인 후 다시 시도하세요.",
                    showLogin = true,
                )
            }
            return
        }

        val prepared = preparedDownload
        if (prepared == null || prepared.media.type != media.type || prepared.media.id != media.id) {
            preparePreview(media, cookies)
            return
        }
        downloadPrepared(prepared, cookies)
    }

    private fun preparePreview(media: BerrizMedia, cookies: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    progress = null,
                    etaSeconds = -1,
                    outputPath = "",
                    previewReady = false,
                    status = "영상을 확인하는 중입니다.",
                    detail = "잠시만 기다려주세요.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    val playback = fetchPlaybackInfo(media, cookies)
                    if (playback.hlsUrl.isBlank()) {
                        error("이 영상은 저장할 수 없습니다.")
                    }
                    PreparedDownload(media, playback)
                }
            }.onSuccess { prepared ->
                preparedDownload = prepared
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        previewTitle = prepared.playback.title,
                        previewThumbnailUrl = prepared.playback.thumbnailUrl,
                        previewReady = true,
                        status = "영상이 준비됐습니다.",
                        detail = "아래 영상이 맞으면 다운로드를 눌러주세요.",
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        status = "영상을 확인하지 못했습니다.",
                        detail = friendlyError(throwable),
                    )
                }
            }
        }
    }

    private fun downloadPrepared(prepared: PreparedDownload, cookies: String) {
        val app = getApplication<Application>()
        val intent = Intent(app, DownloadService::class.java).apply {
            action = DownloadServiceContract.ACTION_START
            putExtra(DownloadServiceContract.EXTRA_PLAYBACK_URL, prepared.playback.hlsUrl)
            putExtra(DownloadServiceContract.EXTRA_COOKIES, cookies)
            putExtra(DownloadServiceContract.EXTRA_TITLE, prepared.playback.title)
            putExtra(DownloadServiceContract.EXTRA_MEDIA_ID, prepared.media.id)
        }
        ContextCompat.startForegroundService(app, intent)
        _state.update {
            it.copy(
                isBusy = true,
                progress = null,
                etaSeconds = -1,
                outputPath = "",
                status = "백그라운드에서 저장 중입니다.",
                detail = "앱을 닫아도 계속 저장합니다.",
            )
        }
    }

    private fun parseLink(raw: String): BerrizMedia? {
        val value = URLDecoder.decode(raw.trim(), Charsets.UTF_8.name())
        val match = mediaRegex.find(value) ?: return null
        val language = match.groupValues[1].lowercase()
        val artist = match.groupValues[2]
        val type = match.groupValues[3].lowercase()
        val id = match.groupValues[4].lowercase()
        val url = "https://berriz.in/$language/$artist/$type/$id/"
        return BerrizMedia(
            pageUrl = url,
            type = type,
            id = id
        )
    }

    private fun clipboardText(): String {
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        return clipboard
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(getApplication())
            ?.toString()
            .orEmpty()
            .trim()
    }

    private fun berrizCookies(): String {
        CookieManager.getInstance().flush()
        return listOf(
            "https://berriz.in",
            "https://svc-api.berriz.in",
            "https://statics.berriz.in",
        )
            .mapNotNull { CookieManager.getInstance().getCookie(it) }
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
    }

    private fun ensureYtdlpReady() {
        if (ytdlpReady) return
        try {
            YoutubeDL.getInstance().init(getApplication())
            FFmpeg.getInstance().init(getApplication())
            ytdlpReady = true
        } catch (error: YoutubeDLException) {
            throw IllegalStateException("yt-dlp 초기화에 실패했습니다: ${error.message}", error)
        }
    }

    private fun endpoint(media: BerrizMedia): String {
        return when (media.type) {
            "media/content" -> "https://svc-api.berriz.in/service/v1/medias/vod/${media.id}/playback_area_context"
            "live/replay" -> "https://svc-api.berriz.in/service/v1/medias/live/replay/${media.id}/playback_area_context"
            else -> error("지원하지 않는 링크 형식입니다: ${media.type}")
        }
    }

    private fun fetchPlaybackInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        val connection = (URL(endpoint(media)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Origin", "https://berriz.in")
            setRequestProperty("Referer", media.pageUrl)
        }
        val body = runCatching {
            (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")

        if (connection.responseCode !in 200..299) {
            error("잠시 후 다시 시도해주세요.")
        }

        val root = JSONObject(body)
        if (root.optString("code") != "0000") {
            val code = root.optString("code")
            val message = root.optString("message", "API error")
            Log.w(logTag, "Berriz API rejected request: code=$code message=$message body=${body.take(500)}")
            if (code == "FS_ER4020" || message.contains("log in", ignoreCase = true)) {
                _state.update {
                    it.copy(
                        loginStatus = LoginStatus.LoggedOut,
                        showLogin = true,
                        status = "로그인이 필요합니다.",
                        detail = "로그인 후 다시 시도하세요.",
                    )
                }
            }
            error("영상을 불러오지 못했습니다.")
        }

        val mediaObject = root.getJSONObject("data").getJSONObject("media")
        val playback = when (media.type) {
            "media/content" -> mediaObject.getJSONObject("vod")
            else -> mediaObject.getJSONObject("live").getJSONObject("replay")
        }

        val title = mediaObject.optString("title", "Berriz video")
        val thumbnailUrl = mediaObject.optString("thumbnailUrl")
            .ifBlank { mediaObject.optString("thumbnailImageUrl") }
            .ifBlank { mediaObject.optString("imageUrl") }
        val hlsUrl = playback.optJSONObject("hls")?.optString("playbackUrl").orEmpty()
        val dashUrl = playback.optJSONObject("dash")?.optString("playbackUrl").orEmpty()
        val isDrm = playback.optBoolean("isDrm") || playback.has("drmInfo")
        Log.i(logTag, "Playback extracted title=$title hls=${hlsUrl.isNotBlank()} dash=${dashUrl.isNotBlank()} drm=$isDrm")
        return PlaybackInfo(
            title = title,
            hlsUrl = hlsUrl,
            dashUrl = dashUrl,
            isDrm = isDrm,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun downloadWithYtdlp(playback: PlaybackInfo, media: BerrizMedia, cookies: String): String {
        val downloadDir = File(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "BerrizDown/${media.id}-${System.currentTimeMillis()}"
        ).apply { mkdirs() }
        val safeTitle = safeFileName(playback.title.ifBlank { "berriz" })
        val outputTemplate = File(downloadDir, "$safeTitle.%(ext)s").absolutePath

        val request = YoutubeDLRequest(playback.hlsUrl).apply {
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--referer", "https://berriz.in/")
            addOption("--add-header", "Origin:https://berriz.in")
            addOption("--add-header", "Cookie:$cookies")
            addOption("-f", "bv*+ba/b")
            addOption("-N", concurrentFragments.toString())
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "20")
            addOption("--remux-video", "mp4")
            addOption("--merge-output-format", "mp4")
            addOption("-o", outputTemplate)
        }

        _state.update {
            it.copy(
                status = "저장 중입니다.",
                detail = "잠시만 기다려주세요.",
            )
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
            _state.update {
                it.copy(
                    progress = progress.coerceIn(0f, 100f) / 100f,
                    etaSeconds = etaInSeconds,
                    status = if (progress >= 100f) "마무리 중입니다." else "저장 중 ${progress.toInt()}%",
                    detail = "화면을 닫지 말고 잠시 기다려주세요.",
                )
            }
        }

        val output = downloadDir.listFiles()
            ?.filter { it.extension.equals("mp4", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("저장할 수 없습니다.")
        _state.update {
            it.copy(
                status = "사진첩에 저장 중입니다.",
                detail = "거의 끝났습니다.",
            )
        }
        val galleryUri = saveToGallery(output, "$safeTitle.${output.extension.ifBlank { "mp4" }}")
        runCatching { downloadDir.deleteRecursively() }
        return galleryUri.toString()
    }

    private fun saveToGallery(source: File, displayName: String): Uri {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BerrizDown")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("저장할 수 없습니다.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, galleryCopyBufferSize)
                }
            } ?: error("저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveThumbnailToGallery(thumbnailUrl: String, title: String): Uri {
        val connection = (URL(thumbnailUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "image/*")
        }
        val bytes = runCatching {
            connection.inputStream.use { it.readBytes() }
        }.getOrElse {
            connection.disconnect()
            throw it
        }
        val mimeType = connection.contentType
            ?.substringBefore(";")
            ?.lowercase()
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        connection.disconnect()
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val displayName = "${safeFileName(title.ifBlank { "thumbnail" })}.$extension"

        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BerrizDown")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("저장할 수 없습니다.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun safeFileName(value: String): String {
        return value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
            .take(80)
            .ifBlank { "berriz" }
    }

    private fun friendlyError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("로그인") || message.contains("FS_ER4020") -> "로그인 후 다시 시도하세요."
            message.contains("지원하지") -> message
            message.contains("저장할 수") -> message
            message.contains("Unable to download", ignoreCase = true) -> "네트워크 상태를 확인하고 다시 시도하세요."
            message.contains("No space", ignoreCase = true) -> "저장 공간을 확인해주세요."
            else -> "잠시 후 다시 시도해주세요."
        }
    }
}

class DownloadService : Service() {
    private val notificationId = 4100
    private val channelId = "berrizdown_downloads"
    private val processId = "berrizdown-background"
    private val concurrentFragments = 8
    private val galleryCopyBufferSize = 1024 * 1024
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var isCancelling = false
    private var activeDownloadDir: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DownloadServiceContract.ACTION_CANCEL -> cancelDownload()
            DownloadServiceContract.ACTION_START -> startDownload(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startDownload(intent: Intent) {
        if (downloadJob?.isActive == true) return
        isCancelling = false

        val playbackUrl = intent.getStringExtra(DownloadServiceContract.EXTRA_PLAYBACK_URL).orEmpty()
        val cookies = intent.getStringExtra(DownloadServiceContract.EXTRA_COOKIES).orEmpty()
        val title = intent.getStringExtra(DownloadServiceContract.EXTRA_TITLE).orEmpty()
        val mediaId = intent.getStringExtra(DownloadServiceContract.EXTRA_MEDIA_ID).orEmpty()

        if (playbackUrl.isBlank() || cookies.isBlank() || mediaId.isBlank()) {
            sendState(false, null, "", "저장하지 못했습니다.", "다시 시도해주세요.")
            stopSelf()
            return
        }

        startForeground(
            notificationId,
            buildNotification(
                title = "저장을 시작합니다.",
                text = "앱을 닫아도 계속 저장합니다.",
                progress = null,
                ongoing = true,
            )
        )
        sendState(true, null, "", "백그라운드에서 저장 중입니다.", "앱을 닫아도 계속 저장합니다.")

        downloadJob = serviceScope.launch {
            runCatching {
                ensureYtdlpReady()
                downloadWithYtdlp(playbackUrl, cookies, title, mediaId)
            }.onSuccess { output ->
                notifyComplete()
                sendState(false, 1f, output, "저장이 끝났습니다.", "사진첩의 동영상 앨범에서 볼 수 있습니다.")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }.onFailure { error ->
                runCatching { activeDownloadDir?.deleteRecursively() }
                activeDownloadDir = null
                if (isCancelling || error is CancellationException) {
                    updateNotification("다운로드를 중단했습니다.", "", null, ongoing = false)
                    sendState(false, null, "", "다운로드를 중단했습니다.", "")
                } else {
                    val message = friendlyError(error)
                    updateNotification("저장하지 못했습니다.", message, null, ongoing = false)
                    sendState(false, null, "", "저장하지 못했습니다.", message)
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun cancelDownload() {
        isCancelling = true
        downloadJob?.cancel()
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        runCatching { activeDownloadDir?.deleteRecursively() }
        activeDownloadDir = null
        updateNotification("다운로드를 중단했습니다.", "", null, ongoing = false)
        sendState(false, null, "", "다운로드를 중단했습니다.", "")
        runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        stopSelf()
    }

    private fun ensureYtdlpReady() {
        YoutubeDL.getInstance().init(application)
        FFmpeg.getInstance().init(application)
    }

    private fun downloadWithYtdlp(playbackUrl: String, cookies: String, title: String, mediaId: String): String {
        val downloadDir = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "BerrizDown/$mediaId-${System.currentTimeMillis()}"
        ).apply { mkdirs() }
        activeDownloadDir = downloadDir
        val safeTitle = safeFileName(title.ifBlank { "berriz" })
        val outputTemplate = File(
            downloadDir,
            "$safeTitle.%(ext)s"
        ).absolutePath

        val request = YoutubeDLRequest(playbackUrl).apply {
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--referer", "https://berriz.in/")
            addOption("--add-header", "Origin:https://berriz.in")
            addOption("--add-header", "Cookie:$cookies")
            addOption("-f", "bv*+ba/b")
            addOption("-N", concurrentFragments.toString())
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "20")
            addOption("--remux-video", "mp4")
            addOption("--merge-output-format", "mp4")
            addOption("-o", outputTemplate)
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            val normalizedProgress = progress.coerceIn(0f, 100f) / 100f
            val label = if (progress >= 100f) "마무리 중입니다." else "저장 중 ${progress.toInt()}%"
            updateNotification(label, "앱을 닫아도 계속 저장합니다.", normalizedProgress, ongoing = true)
            sendState(true, normalizedProgress, "", label, "앱을 닫아도 계속 저장합니다.")
        }

        val output = downloadDir.listFiles()
            ?.filter { it.extension.equals("mp4", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("저장할 수 없습니다.")
        updateNotification("사진첩에 저장 중입니다.", "거의 끝났습니다.", null, ongoing = true)
        sendState(true, null, "", "사진첩에 저장 중입니다.", "거의 끝났습니다.")
        val galleryUri = saveVideoToGallery(output, "$safeTitle.${output.extension.ifBlank { "mp4" }}")
        runCatching { downloadDir.deleteRecursively() }
        activeDownloadDir = null
        return galleryUri.toString()
    }

    private fun saveVideoToGallery(source: File, displayName: String): Uri {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BerrizDown")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("저장할 수 없습니다.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output, galleryCopyBufferSize)
                }
            } ?: error("저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun sendState(
        isBusy: Boolean,
        progress: Float?,
        output: String,
        status: String,
        detail: String,
    ) {
        sendBroadcast(
            Intent(DownloadServiceContract.ACTION_STATE).apply {
                setPackage(packageName)
                putExtra(DownloadServiceContract.EXTRA_BUSY, isBusy)
                progress?.let { putExtra(DownloadServiceContract.EXTRA_PROGRESS, it) }
                putExtra(DownloadServiceContract.EXTRA_OUTPUT, output)
                putExtra(DownloadServiceContract.EXTRA_STATUS, status)
                putExtra(DownloadServiceContract.EXTRA_DETAIL, detail)
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            channelId,
            "BerrizDown 저장",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(title: String, text: String, progress: Float?, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, buildNotification(title, text, progress, ongoing))
    }

    private fun notifyComplete() {
        updateNotification("저장이 끝났습니다.", "사진첩에서 볼 수 있습니다.", 1f, ongoing = false)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Float?,
        ongoing: Boolean,
    ): android.app.Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(DownloadServiceContract.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .apply {
                if (ongoing) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "중단", cancelPendingIntent)
                }
                if (progress == null) {
                    setProgress(0, 0, ongoing)
                } else {
                    setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
                }
            }
            .build()
    }

    private fun safeFileName(value: String): String {
        return value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
            .take(80)
            .ifBlank { "berriz" }
    }

    private fun friendlyError(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("로그인") || message.contains("FS_ER4020") -> "로그인 후 다시 시도하세요."
            message.contains("지원하지") -> message
            message.contains("저장할 수") -> message
            message.contains("Unable to download", ignoreCase = true) -> "네트워크 상태를 확인하고 다시 시도하세요."
            message.contains("No space", ignoreCase = true) -> "저장 공간을 확인해주세요."
            else -> "잠시 후 다시 시도해주세요."
        }
    }
}

@Composable
fun BerrizTheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (useDarkTheme) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFFF3B9D),
            onPrimary = Color.White,
            background = Color(0xFF111113),
            surface = Color(0xFF1E1E23),
            surfaceVariant = Color(0xFF2A2A31),
            onBackground = Color(0xFFF7F2F5),
            onSurface = Color(0xFFF7F2F5),
            onSurfaceVariant = Color(0xFFC9C3CA),
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = Color(0xFFE42D8F),
            onPrimary = Color.White,
            background = Color(0xFFFFF7FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFF4ECF2),
            onBackground = Color(0xFF221A20),
            onSurface = Color(0xFF221A20),
            onSurfaceVariant = Color(0xFF655D64),
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun BerrizApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val useDarkTheme = resolveDarkTheme(state.themeMode)

    BerrizTheme(useDarkTheme = useDarkTheme) {
        val context = LocalContext.current
        val background = MaterialTheme.colorScheme.background

        LaunchedEffect(Unit) {
            CookieManager.getInstance().setAcceptCookie(true)
            viewModel.refreshLoginStatus()
        }

        LaunchedEffect(useDarkTheme, background) {
            (context as? ComponentActivity)?.window?.let { window ->
                window.statusBarColor = background.toArgb()
                window.navigationBarColor = background.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            MainScreen(
                state = state,
                onLinkChange = viewModel::setLink,
                onPaste = viewModel::pasteFromClipboard,
                onDownload = viewModel::startDownload,
                onCancel = viewModel::cancelDownload,
                onLogin = viewModel::openLogin,
                onOpenVideo = { openSavedVideo(context, state.outputPath) },
                onThemeModeChange = viewModel::setThemeMode,
                onThumbnailDownload = viewModel::downloadThumbnail,
            )

            AnimatedVisibility(visible = state.showLogin) {
                LoginScreen(
                    onClose = viewModel::closeLogin,
                    onPageFinished = viewModel::refreshLoginStatus,
                )
            }
        }
    }
}

@Composable
private fun resolveDarkTheme(mode: AppThemeMode): Boolean {
    return when (mode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
}

private fun openSavedVideo(context: Context, path: String) {
    if (path.isBlank()) return
    val uri = if (path.startsWith("content://")) {
        Uri.parse(path)
    } else {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "파일을 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "영상 열기"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MainScreen(
    state: UiState,
    onLinkChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLogin: () -> Unit,
    onOpenVideo: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThumbnailDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HeaderCard(
            state = state,
            onLogin = onLogin,
            onThemeModeChange = onThemeModeChange,
        )
        LinkCard(
            link = state.link,
            isBusy = state.isBusy,
            onLinkChange = onLinkChange,
            onPaste = onPaste,
            onDownload = onDownload,
            onCancel = onCancel,
            previewReady = state.previewReady,
        )
        AnimatedVisibility(visible = state.previewReady) {
            PreviewCard(
                title = state.previewTitle,
                thumbnailUrl = state.previewThumbnailUrl,
                isThumbnailBusy = state.isThumbnailBusy,
                onThumbnailDownload = onThumbnailDownload,
            )
        }
        StatusCard(state = state, onOpenVideo = onOpenVideo)
    }
}

@Composable
private fun HeaderCard(
    state: UiState,
    onLogin: () -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(colors.surfaceVariant, colors.surface)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "BerrizDown",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Text(
                    text = "링크를 넣어 베리즈 영상을 다운로드할수 있는 앱입니다",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ThemeSelector(
                    selected = state.themeMode,
                    onChange = onThemeModeChange,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(state.loginStatus)
                    Button(
                        onClick = onLogin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurface,
                        ),
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("로그인 열기")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelector(selected: AppThemeMode, onChange: (AppThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "화면",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        AppThemeMode.entries.forEach { mode ->
            val selectedMode = mode == selected
            Surface(
                modifier = Modifier.clickable { onChange(mode) },
                color = if (selectedMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = mode.label,
                    color = if (selectedMode) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: LoginStatus) {
    val (label, color) = when (status) {
        LoginStatus.LoggedIn -> "로그인됨" to Color(0xFF52D77A)
        LoginStatus.LoggedOut -> "로그인 필요" to Color(0xFFFFC257)
        LoginStatus.Unknown -> "로그인 확인" to Color(0xFF9FA4FF)
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LinkCard(
    link: String,
    isBusy: Boolean,
    onLinkChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    previewReady: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("링크", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = link,
                onValueChange = onLinkChange,
                minLines = 3,
                enabled = !isBusy,
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                ),
                placeholder = { Text("Berriz 링크 붙여넣기") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPaste, enabled = !isBusy) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("붙여넣기")
                }
                Spacer(Modifier.weight(1f))
                if (isBusy) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("중단")
                    }
                } else {
                    Button(onClick = onDownload) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (previewReady) "다운로드" else "영상 확인")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    thumbnailUrl: String,
    isThumbnailBusy: Boolean,
    onThumbnailDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThumbnailImage(
                url = thumbnailUrl,
                isDownloading = isThumbnailBusy,
                onDownload = onThumbnailDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Text(
                text = title.ifBlank { "제목 없음" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "이 영상이 맞으면 다운로드를 누르세요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ThumbnailImage(
    url: String,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
        }
    }

    if (bitmap != null) {
        Box(modifier = modifier) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isDownloading) "저장 중" else "썸네일 다운로드")
            }
        }
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "미리보기",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusCard(state: UiState, onOpenVideo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(state.status, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (state.progress != null) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }
            Text(
                text = state.detail.ifBlank { "준비되었습니다." },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            AnimatedVisibility(visible = state.outputPath.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "사진첩 > 동영상 > BerrizDown",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenVideo) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("영상 열기")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onClose: () -> Unit, onPageFinished: () -> Unit) {
    var title by remember { mutableStateOf("Berriz 로그인") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "닫기")
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            title = view.title ?: "Berriz 로그인"
                            CookieManager.getInstance().flush()
                            onPageFinished()
                        }
                    }
                    loadUrl("https://berriz.in/ko/")
                }
            }
        )
    }
}
