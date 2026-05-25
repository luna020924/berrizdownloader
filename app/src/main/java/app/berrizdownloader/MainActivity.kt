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
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.io.IOException
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
import org.json.JSONArray
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
    val status: String = "링크를 넣고 콘텐츠를 확인하세요.",
    val detail: String = "",
    val loginStatus: LoginStatus = LoginStatus.Unknown,
    val isBusy: Boolean = false,
    val progress: Float? = null,
    val etaSeconds: Long = -1,
    val outputPath: String = "",
    val outputMimeType: String = "",
    val outputHint: String = "",
    val showLogin: Boolean = false,
    val previewTitle: String = "",
    val previewThumbnailUrl: String = "",
    val previewMeta: String = "",
    val previewReady: Boolean = false,
    val queueItems: List<QueuePreviewItem> = emptyList(),
    val detectedLinkCount: Int = 0,
    val allowDuplicateDownloads: Boolean = false,
    val isThumbnailBusy: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val qualityMode: DownloadQuality = DownloadQuality.Best,
)

data class QueuePreviewItem(
    val index: Int,
    val title: String,
    val meta: String,
    val thumbnailUrl: String,
    val kind: BerrizContentKind,
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

enum class DownloadQuality(val label: String, val maxHeight: Int?) {
    Best("최고", null),
    Q1080("1080p", 1080),
    Q720("720p", 720),
    Q480("480p", 480),
}

object DownloadServiceContract {
    const val ACTION_START = "app.berrizdownloader.action.START_DOWNLOAD"
    const val ACTION_CANCEL = "app.berrizdownloader.action.CANCEL_DOWNLOAD"
    const val ACTION_STATE = "app.berrizdownloader.action.DOWNLOAD_STATE"
    const val EXTRA_PLAYBACK_URL = "playback_url"
    const val EXTRA_BATCH_JSON = "batch_json"
    const val EXTRA_COOKIES = "cookies"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MEDIA_ID = "media_id"
    const val EXTRA_MEDIA_TYPE = "media_type"
    const val EXTRA_QUALITY_MAX_HEIGHT = "quality_max_height"
    const val EXTRA_STATUS = "status"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_OUTPUT = "output"
    const val EXTRA_OUTPUT_MIME = "output_mime"
    const val EXTRA_OUTPUT_HINT = "output_hint"
    const val EXTRA_BUSY = "busy"
}

data class BerrizMedia(
    val pageUrl: String,
    val artist: String,
    val type: String,
    val id: String,
    val boardId: String = "",
)

enum class BerrizContentKind(val label: String) {
    Video("영상"),
    Photo("사진"),
    Post("게시글"),
    Notice("공지"),
}

data class PlaybackInfo(
    val title: String,
    val hlsUrl: String,
    val dashUrl: String,
    val isDrm: Boolean,
    val thumbnailUrl: String,
    val kind: BerrizContentKind = BerrizContentKind.Video,
    val photoUrls: List<String> = emptyList(),
    val documentJson: String = "",
    val documentHtml: String = "",
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
        """(?:https?://)?(?:www\.)?(?:link\.)?berriz\.in/(?:[a-z]{2}/)?(?:(?:app|web)/main/)?([A-Za-z0-9_-]+)/(media/content|live(?:/replay)?)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:[/?#][^\s]*)?""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val noticeRegex = Regex(
        """(?:https?://)?(?:www\.)?(?:link\.)?berriz\.in/(?:[a-z]{2}/)?(?:(?:app|web)/main/)?([A-Za-z0-9_-]+)/notice/([0-9]+)(?:[/?#][^\s]*)?""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val postRegex = Regex(
        """(?:https?://)?(?:www\.)?(?:link\.)?berriz\.in/(?:[a-z]{2}/)?(?:(?:app|web)/main/)?([A-Za-z0-9_-]+)/board/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/post/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:[/?#][^\s]*)?""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val processId = "berrizdown-active"
    private val concurrentFragments = 8
    private val galleryCopyBufferSize = 1024 * 1024
    private val downloadedIdsKey = "downloaded_media_ids"
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
                    outputMimeType = intent.getStringExtra(DownloadServiceContract.EXTRA_OUTPUT_MIME).orEmpty(),
                    outputHint = intent.getStringExtra(DownloadServiceContract.EXTRA_OUTPUT_HINT).orEmpty(),
                    status = intent.getStringExtra(DownloadServiceContract.EXTRA_STATUS) ?: it.status,
                    detail = intent.getStringExtra(DownloadServiceContract.EXTRA_DETAIL) ?: it.detail,
                )
            }
        }
    }
    private var ytdlpReady = false
    private var preparedDownload: PreparedDownload? = null
    private var preparedBatch: List<PreparedDownload> = emptyList()
    private var preparedKey: String = ""

    init {
        _state.update {
            it.copy(
                themeMode = savedThemeMode(),
                qualityMode = savedQualityMode(),
                allowDuplicateDownloads = savedAllowDuplicateDownloads(),
            )
        }
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

    fun setQualityMode(mode: DownloadQuality) {
        preferences.edit().putString("quality_mode", mode.name).apply()
        _state.update { it.copy(qualityMode = mode) }
    }

    fun setAllowDuplicateDownloads(allow: Boolean) {
        preferences.edit().putBoolean("allow_duplicate_downloads", allow).apply()
        _state.update { it.copy(allowDuplicateDownloads = allow) }
    }

    private fun savedThemeMode(): AppThemeMode {
        val value = preferences.getString("theme_mode", AppThemeMode.System.name)
        return runCatching { AppThemeMode.valueOf(value ?: AppThemeMode.System.name) }
            .getOrDefault(AppThemeMode.System)
    }

    private fun savedQualityMode(): DownloadQuality {
        val value = preferences.getString("quality_mode", DownloadQuality.Best.name)
        return runCatching { DownloadQuality.valueOf(value ?: DownloadQuality.Best.name) }
            .getOrDefault(DownloadQuality.Best)
    }

    private fun savedAllowDuplicateDownloads(): Boolean {
        return preferences.getBoolean("allow_duplicate_downloads", false)
    }

    fun setLink(value: String) {
        val next = value.trim()
        preparedDownload = null
        preparedBatch = emptyList()
        preparedKey = ""
        val detectedLinks = parseLinks(next).size
        _state.update {
            it.copy(
                link = next,
                outputPath = "",
                outputMimeType = "",
                outputHint = "",
                progress = null,
                previewTitle = "",
                previewThumbnailUrl = "",
                previewMeta = "",
                previewReady = false,
                queueItems = emptyList(),
                detectedLinkCount = detectedLinks,
                isThumbnailBusy = false,
                status = when {
                    next.isBlank() -> "링크를 넣고 콘텐츠를 확인하세요."
                    detectedLinks > 1 -> "${detectedLinks}개의 링크를 찾았습니다."
                    else -> "콘텐츠 확인을 눌러주세요."
                },
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
            val mediaList = parseLinks(text)
            val nextLink = if (mediaList.isNotEmpty()) {
                mediaList.joinToString("\n") { it.pageUrl }
            } else {
                text.trim()
            }
            setLink(nextLink)
            _state.update {
                it.copy(
                    status = if (mediaList.isEmpty()) {
                        "링크를 다시 확인해주세요."
                    } else if (mediaList.size > 1) {
                        "${mediaList.size}개의 링크를 찾았습니다."
                    } else {
                        "링크를 찾았습니다. 콘텐츠를 확인해 주세요."
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
        val mediaItems = parseLinks(current)
        if (mediaItems.isEmpty()) {
            _state.update {
                it.copy(
                    status = "지원하지 않는 링크입니다.",
                    detail = "Berriz 콘텐츠 상세 링크를 넣어주세요.",
                )
            }
            return
        }
        val normalizedLinks = mediaItems.joinToString("\n") { it.pageUrl }
        if (state.value.link != normalizedLinks) {
            _state.update { it.copy(link = normalizedLinks, detectedLinkCount = mediaItems.size) }
        }

        val cookies = berrizCookies()
        val needsLoginBeforeRequest = mediaItems.any { it.type == "media/content" || it.type == "live/replay" }
        if (cookies.isBlank() && needsLoginBeforeRequest) {
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

        val allowDuplicates = state.value.allowDuplicateDownloads
        val uniqueItems = if (allowDuplicates) {
            mediaItems
        } else {
            mediaItems.filterNot { isDownloaded(it) }
        }
        if (uniqueItems.isEmpty()) {
            _state.update {
                it.copy(
                    status = "이미 저장한 콘텐츠입니다.",
                    detail = "다시 저장하려면 중복 다시 저장을 켜주세요.",
                )
            }
            return
        }
        val skippedDuplicates = mediaItems.size - uniqueItems.size
        val key = mediaKey(uniqueItems)
        if (preparedBatch.isEmpty() || preparedKey != key) {
            preparePreview(uniqueItems, cookies, skippedDuplicates)
            return
        }
        downloadPreparedBatch(preparedBatch, cookies)
    }

    private fun preparePreview(mediaItems: List<BerrizMedia>, cookies: String, skippedDuplicates: Int) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    progress = null,
                    etaSeconds = -1,
                    outputPath = "",
                    outputMimeType = "",
                    outputHint = "",
                    previewReady = false,
                    status = "콘텐츠를 확인하는 중입니다.",
                    detail = "잠시만 기다려주세요.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    mediaItems.mapIndexed { index, media ->
                        _state.update {
                            it.copy(
                                status = "콘텐츠 확인 중 ${index + 1}/${mediaItems.size}",
                                progress = (index + 1).toFloat() / mediaItems.size.toFloat(),
                            )
                        }
                        val playback = fetchPlaybackInfo(media, cookies)
                        if (playback.kind == BerrizContentKind.Video) {
                            if (playback.downloadUrl().isBlank()) {
                                error("이 콘텐츠는 저장할 수 없습니다.")
                            }
                        }
                        PreparedDownload(media, playback)
                    }
                }
            }.onSuccess { preparedItems ->
                val first = preparedItems.first()
                preparedDownload = first
                preparedBatch = preparedItems
                preparedKey = mediaKey(preparedItems.map { it.media })
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        previewTitle = first.playback.title,
                        previewThumbnailUrl = first.playback.thumbnailUrl,
                        previewMeta = previewMeta(preparedItems),
                        previewReady = true,
                        queueItems = preparedItems.toQueuePreviewItems(),
                        status = "콘텐츠가 준비됐습니다.",
                        detail = previewDetail(preparedItems, skippedDuplicates),
                    )
                }
            }.onFailure { throwable ->
                preparedDownload = null
                preparedBatch = emptyList()
                preparedKey = ""
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        queueItems = emptyList(),
                        status = "콘텐츠를 확인하지 못했습니다.",
                        detail = friendlyError(throwable),
                    )
                }
            }
        }
    }

    private fun downloadPrepared(prepared: PreparedDownload, cookies: String) {
        if (prepared.playback.kind == BerrizContentKind.Photo) {
            downloadPhotoPrepared(prepared)
            return
        }
        if (prepared.playback.kind == BerrizContentKind.Post || prepared.playback.kind == BerrizContentKind.Notice) {
            downloadDocumentPrepared(prepared)
            return
        }
        if (prepared.playback.isDrm) {
            _state.update {
                it.copy(
                    isBusy = false,
                    progress = null,
                    status = "영상 저장을 지원하지 않습니다.",
                    detail = "보호된 콘텐츠라 썸네일만 저장할 수 있습니다.",
                )
            }
            return
        }

        val app = getApplication<Application>()
        val intent = Intent(app, DownloadService::class.java).apply {
            action = DownloadServiceContract.ACTION_START
            putExtra(DownloadServiceContract.EXTRA_PLAYBACK_URL, prepared.playback.downloadUrl())
            putExtra(DownloadServiceContract.EXTRA_COOKIES, cookies)
            putExtra(DownloadServiceContract.EXTRA_TITLE, prepared.playback.title)
            putExtra(DownloadServiceContract.EXTRA_MEDIA_ID, prepared.media.id)
            putExtra(DownloadServiceContract.EXTRA_MEDIA_TYPE, prepared.media.type)
            state.value.qualityMode.maxHeight?.let {
                putExtra(DownloadServiceContract.EXTRA_QUALITY_MAX_HEIGHT, it)
            }
        }
        ContextCompat.startForegroundService(app, intent)
        _state.update {
            it.copy(
                isBusy = true,
                progress = null,
                etaSeconds = -1,
                outputPath = "",
                outputMimeType = "",
                outputHint = "",
                status = "백그라운드에서 저장 중입니다.",
                detail = "앱을 닫아도 계속 저장합니다.",
            )
        }
    }

    private fun downloadPreparedBatch(preparedItems: List<PreparedDownload>, cookies: String) {
        if (preparedItems.size == 1) {
            downloadPrepared(preparedItems.first(), cookies)
            return
        }

        if (preparedItems.any { it.playback.kind == BerrizContentKind.Video && it.playback.isDrm }) {
            _state.update {
                it.copy(
                    isBusy = false,
                    progress = null,
                    status = "일부 영상을 저장할 수 없습니다.",
                    detail = "보호된 콘텐츠는 썸네일만 저장할 수 있습니다.",
                )
            }
            return
        }

        if (preparedItems.all { it.playback.kind == BerrizContentKind.Video }) {
            startVideoBatchDownload(preparedItems, cookies)
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    progress = null,
                    etaSeconds = -1,
                    outputPath = "",
                    outputMimeType = "",
                    outputHint = "",
                    status = "여러 콘텐츠를 저장 중입니다.",
                    detail = "앱을 열어둔 상태에서 순서대로 저장합니다.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    preparedItems.forEachIndexed { index, prepared ->
                        _state.update {
                            it.copy(
                                progress = index.toFloat() / preparedItems.size.toFloat(),
                                status = "저장 중 ${index + 1}/${preparedItems.size}",
                                detail = prepared.playback.title,
                            )
                        }
                        if (prepared.playback.kind == BerrizContentKind.Photo) {
                            val result = savePhotoSetToGallery(prepared.playback)
                            markDownloaded(prepared.media)
                            _state.update {
                                it.copy(
                                    outputPath = result.firstUri.toString(),
                                    outputMimeType = "image/*",
                                    outputHint = "사진첩 > 사진 > BerrizDown",
                                )
                            }
                        } else if (prepared.playback.kind == BerrizContentKind.Post || prepared.playback.kind == BerrizContentKind.Notice) {
                            val result = saveDocumentBackup(prepared.playback)
                            markDownloaded(prepared.media)
                            _state.update {
                                it.copy(
                                    outputPath = result.firstUri.toString(),
                                    outputMimeType = "text/html",
                                    outputHint = "다운로드 > BerrizDown",
                                )
                            }
                        } else {
                            downloadWithYtdlp(
                                playback = prepared.playback,
                                media = prepared.media,
                                cookies = cookies,
                                batchIndex = index,
                                batchTotal = preparedItems.size,
                            )
                            markDownloaded(prepared.media)
                        }
                    }
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = 1f,
                        status = "저장이 끝났습니다.",
                        detail = "사진첩의 BerrizDown 앨범에서 볼 수 있습니다.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        status = "저장하지 못했습니다.",
                        detail = friendlyError(error),
                    )
                }
            }
        }
    }

    private fun startVideoBatchDownload(preparedItems: List<PreparedDownload>, cookies: String) {
        val app = getApplication<Application>()
        val intent = Intent(app, DownloadService::class.java).apply {
            action = DownloadServiceContract.ACTION_START
            putExtra(DownloadServiceContract.EXTRA_BATCH_JSON, videoBatchPayload(preparedItems))
            putExtra(DownloadServiceContract.EXTRA_COOKIES, cookies)
            state.value.qualityMode.maxHeight?.let {
                putExtra(DownloadServiceContract.EXTRA_QUALITY_MAX_HEIGHT, it)
            }
        }
        ContextCompat.startForegroundService(app, intent)
        _state.update {
            it.copy(
                isBusy = true,
                progress = null,
                etaSeconds = -1,
                outputPath = "",
                outputMimeType = "",
                outputHint = "",
                status = "${preparedItems.size}개 영상을 백그라운드에서 저장 중입니다.",
                detail = "앱을 닫아도 순서대로 계속 저장합니다.",
            )
        }
    }

    private fun videoBatchPayload(preparedItems: List<PreparedDownload>): String {
        val array = JSONArray()
        preparedItems.forEach { prepared ->
            array.put(
                JSONObject()
                    .put("playbackUrl", prepared.playback.downloadUrl())
                    .put("title", prepared.playback.title)
                    .put("mediaId", prepared.media.id)
                    .put("mediaType", prepared.media.type)
            )
        }
        return array.toString()
    }

    private fun downloadPhotoPrepared(prepared: PreparedDownload) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    progress = null,
                    etaSeconds = -1,
                    outputPath = "",
                    outputMimeType = "",
                    outputHint = "",
                    status = "사진을 저장 중입니다.",
                    detail = "사진첩에 저장하고 있습니다.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    savePhotoSetToGallery(prepared.playback)
                }
            }.onSuccess { result ->
                markDownloaded(prepared.media)
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = 1f,
                        outputPath = result.firstUri.toString(),
                        outputMimeType = "image/*",
                        outputHint = "사진첩 > 사진 > BerrizDown",
                        status = "사진 저장이 끝났습니다.",
                        detail = "사진첩의 BerrizDown 앨범에서 ${result.count}장을 볼 수 있습니다.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        status = "사진을 저장하지 못했습니다.",
                        detail = friendlyError(error),
                    )
                }
            }
        }
    }

    private fun downloadDocumentPrepared(prepared: PreparedDownload) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    progress = null,
                    etaSeconds = -1,
                    outputPath = "",
                    outputMimeType = "",
                    outputHint = "",
                    status = "${prepared.playback.kind.label}을 저장 중입니다.",
                    detail = "다운로드 폴더에 백업하고 있습니다.",
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    saveDocumentBackup(prepared.playback)
                }
            }.onSuccess { result ->
                markDownloaded(prepared.media)
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = 1f,
                        outputPath = result.firstUri.toString(),
                        outputMimeType = "text/html",
                        outputHint = "다운로드 > BerrizDown",
                        status = "${prepared.playback.kind.label} 저장이 끝났습니다.",
                        detail = if (result.imageCount > 0) {
                            "HTML/JSON과 이미지 ${result.imageCount}장을 저장했습니다."
                        } else {
                            "HTML/JSON 백업 파일을 저장했습니다."
                        },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        progress = null,
                        status = "${prepared.playback.kind.label}을 저장하지 못했습니다.",
                        detail = friendlyError(error),
                    )
                }
            }
        }
    }

    private fun parseLink(raw: String): BerrizMedia? {
        return parseLinks(raw).firstOrNull()
    }

    private fun parseLinks(raw: String): List<BerrizMedia> {
        val value = runCatching {
            URLDecoder.decode(raw.trim(), Charsets.UTF_8.name())
        }.getOrElse {
            raw.trim()
        }
        val seen = linkedMapOf<String, BerrizMedia>()
        noticeRegex.findAll(value).forEach { match ->
            val artist = match.groupValues[1]
            val id = match.groupValues[2]
            val url = "https://berriz.in/ko/$artist/notice/$id/"
            seen["notice:$id"] = BerrizMedia(
                pageUrl = url,
                artist = artist,
                type = "notice",
                id = id,
            )
        }
        postRegex.findAll(value).forEach { match ->
            val artist = match.groupValues[1]
            val boardId = match.groupValues[2].lowercase()
            val id = match.groupValues[3].lowercase()
            val url = "https://berriz.in/ko/$artist/board/$boardId/post/$id/"
            seen["board/post:$id"] = BerrizMedia(
                pageUrl = url,
                artist = artist,
                type = "board/post",
                id = id,
                boardId = boardId,
            )
        }
        mediaRegex.findAll(value).forEach { match ->
            val artist = match.groupValues[1]
            val type = match.groupValues[2].lowercase()
            val id = match.groupValues[3].lowercase()
            val normalizedType = if (type.startsWith("live")) "live/replay" else type
            val url = "https://berriz.in/ko/$artist/$normalizedType/$id/"
            seen["$normalizedType:$id"] = BerrizMedia(
                pageUrl = url,
                artist = artist,
                type = normalizedType,
                id = id
            )
        }
        return seen.values.toList()
    }

    private fun mediaKey(items: List<BerrizMedia>): String {
        return items.joinToString("|") { "${it.type}:${it.id}" }
    }

    private fun isDownloaded(media: BerrizMedia): Boolean {
        val ids = preferences.getStringSet(downloadedIdsKey, emptySet()).orEmpty()
        return ids.contains(downloadedKey(media)) || ids.contains(media.id)
    }

    private fun markDownloaded(media: BerrizMedia) {
        val next = preferences.getStringSet(downloadedIdsKey, emptySet())
            .orEmpty()
            .toMutableSet()
        next.add(downloadedKey(media))
        preferences.edit().putStringSet(downloadedIdsKey, next).apply()
    }

    private fun downloadedKey(media: BerrizMedia): String {
        return "${media.type}:${media.id}"
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

    private fun fetchPlaybackInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        if (media.type == "notice") {
            return fetchNoticeBackupInfo(media, cookies)
        }
        if (media.type == "board/post") {
            return fetchPostBackupInfo(media, cookies)
        }
        if (media.type == "live/replay") {
            return fetchLiveReplayPlaybackInfo(media, cookies)
        }
        return fetchMediaContentInfo(media, cookies)
    }

    private fun fetchMediaContentInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        val publicRoot = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/medias/${media.id}/public_context",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        val publicMedia = publicRoot.getJSONObject("data").getJSONObject("media")
        val mediaType = publicMedia.optString("mediaType").uppercase()
        if (mediaType == "PHOTO") {
            val playbackRoot = requestBerrizJson(
                url = "https://svc-api.berriz.in/service/v1/medias/${media.id}/playback_info",
                cookies = cookies,
                referer = media.pageUrl,
                requireSuccess = true,
            )
            val photo = playbackRoot.getJSONObject("data").optJSONObject("photo")
                ?: error("사진 정보를 찾지 못했습니다.")
            val photoUrls = photo.optJSONArray("images")
                .toImageUrls()
            if (photoUrls.isEmpty()) {
                error("저장할 사진을 찾지 못했습니다.")
            }
            return PlaybackInfo(
                title = publicMedia.optString("title", "Berriz photo"),
                hlsUrl = "",
                dashUrl = "",
                isDrm = false,
                thumbnailUrl = publicMedia.optString("thumbnailUrl")
                    .ifBlank { photoUrls.firstOrNull().orEmpty() },
                kind = BerrizContentKind.Photo,
                photoUrls = photoUrls,
            )
        }

        val root = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/medias/vod/${media.id}/playback_area_context",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        return parseVodPlayback(root)
    }

    private fun fetchLiveReplayPlaybackInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        val root = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/medias/live/replay/${media.id}/playback_area_context",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        return parseLivePlayback(root)
    }

    private fun fetchPostBackupInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        val communityId = fetchCommunityId(media, cookies)
        val root = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/community/$communityId/post/${media.id}",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        val data = root.getJSONObject("data")
        val post = data.optJSONObject("post")
        val writer = data.optJSONObject("writer")
        val body = post?.optString("body").orEmpty()
            .ifBlank { post?.optString("plainBody").orEmpty() }
        val title = post?.optString("title").orEmpty()
            .ifBlank { post?.optString("plainBody").orEmpty().lineSequence().firstOrNull().orEmpty() }
            .ifBlank { "Berriz post" }
        val writerName = writer?.optString("nickname").orEmpty()
            .ifBlank { writer?.optString("name").orEmpty() }
        val comments = runCatching {
            fetchPostComments(communityId, data, cookies, media.pageUrl)
        }.getOrElse {
            JSONArray()
        }
        val backupRoot = JSONObject(root.toString()).put("commentsBackup", comments)
        val html = buildBackupHtml(
            typeLabel = "게시글",
            title = title,
            bodyHtml = body + commentsToHtml(comments),
            author = writerName,
            sourceUrl = media.pageUrl,
        )
        return PlaybackInfo(
            title = title,
            hlsUrl = "",
            dashUrl = "",
            isDrm = false,
            thumbnailUrl = firstImageUrl(backupRoot, html),
            kind = BerrizContentKind.Post,
            documentJson = backupRoot.toString(2),
            documentHtml = html,
        )
    }

    private fun fetchPostComments(communityId: Int, postData: JSONObject, cookies: String, referer: String): JSONArray {
        val comment = postData.optJSONObject("comment") ?: return JSONArray()
        val contentTypeCode = comment.optString("contentTypeCode").ifBlank { return JSONArray() }
        val contentId = comment.optString("readContentId").ifBlank { return JSONArray() }
        val params = "contentTypeCode=$contentTypeCode&contentId=$contentId&pageSize=999999999&languageCode=en"
        val artistRoot = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/comment/$communityId/artists/comments?$params",
            cookies = cookies,
            referer = referer,
            requireSuccess = false,
        )
        val artistContents = artistRoot.optJSONObject("data")?.optJSONArray("contents")
        val comments = if (artistRoot.optString("code") == "0000" && artistContents != null && artistContents.length() > 0) {
            artistContents
        } else {
            val userRoot = requestBerrizJson(
                url = "https://svc-api.berriz.in/service/v1/comment/comments?$params",
                cookies = cookies,
                referer = referer,
                requireSuccess = false,
            )
            userRoot.optJSONObject("data")?.optJSONArray("contents") ?: JSONArray()
        }
        appendRepliesToComments(communityId, contentTypeCode, contentId, comments, cookies, referer)
        return comments
    }

    private fun appendRepliesToComments(
        communityId: Int,
        contentTypeCode: String,
        contentId: String,
        comments: JSONArray,
        cookies: String,
        referer: String,
    ) {
        for (index in 0 until comments.length()) {
            val comment = comments.optJSONObject(index) ?: continue
            val element = comment.optJSONObject("element") ?: continue
            val replyCount = element.optInt("replyCount", 0)
            val parentSeq = element.optString("seq")
            if (replyCount <= 0 || parentSeq.isBlank()) continue
            val root = requestBerrizJson(
                url = "https://svc-api.berriz.in/service/v1/comment/$communityId/artists/$parentSeq/replies?contentTypeCode=$contentTypeCode&contentId=$contentId&pageSize=999999999&languageCode=en",
                cookies = cookies,
                referer = referer,
                requireSuccess = false,
            )
            val replies = root.optJSONObject("data")?.optJSONArray("contents") ?: JSONArray()
            comment.put("repliesBackup", replies)
        }
    }

    private fun commentsToHtml(comments: JSONArray): String {
        if (comments.length() == 0) return ""
        val items = buildString {
            append("<section class=\"comments\"><h2>댓글</h2>")
            for (index in 0 until comments.length()) {
                val comment = comments.optJSONObject(index) ?: continue
                append(commentToHtml(comment))
                val replies = comment.optJSONArray("repliesBackup") ?: JSONArray()
                if (replies.length() > 0) {
                    append("<div class=\"replies\">")
                    for (replyIndex in 0 until replies.length()) {
                        replies.optJSONObject(replyIndex)?.let { append(commentToHtml(it)) }
                    }
                    append("</div>")
                }
            }
            append("</section>")
        }
        return items
    }

    private fun commentToHtml(comment: JSONObject): String {
        val author = comment.optJSONObject("author")?.optString("authorDisplayName").orEmpty()
        val text = comment.optJSONObject("element")?.optString("text").orEmpty()
        return "<div class=\"comment\"><strong>${escapeHtml(author)}</strong><p>${escapeHtml(text).replace("\n", "<br>")}</p></div>"
    }

    private fun fetchNoticeBackupInfo(media: BerrizMedia, cookies: String): PlaybackInfo {
        val communityId = fetchCommunityId(media, cookies)
        val root = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/community/$communityId/notices/${media.id}",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        val notice = root.getJSONObject("data").getJSONObject("communityNotice")
        val title = notice.optString("title", "Berriz notice")
        val body = notice.optString("body")
        val html = buildBackupHtml(
            typeLabel = "공지",
            title = title,
            bodyHtml = body,
            author = media.artist,
            sourceUrl = media.pageUrl,
        )
        return PlaybackInfo(
            title = title,
            hlsUrl = "",
            dashUrl = "",
            isDrm = false,
            thumbnailUrl = firstImageUrl(root, html),
            kind = BerrizContentKind.Notice,
            documentJson = root.toString(2),
            documentHtml = html,
        )
    }

    private fun fetchCommunityId(media: BerrizMedia, cookies: String): Int {
        val root = requestBerrizJson(
            url = "https://svc-api.berriz.in/service/v1/community/id/${media.artist}",
            cookies = cookies,
            referer = media.pageUrl,
            requireSuccess = true,
        )
        return root.getJSONObject("data").getInt("communityId")
    }

    private fun buildBackupHtml(typeLabel: String, title: String, bodyHtml: String, author: String, sourceUrl: String): String {
        val safeTitle = escapeHtml(title.ifBlank { typeLabel })
        val safeAuthor = escapeHtml(author)
        val safeSource = escapeHtml(sourceUrl)
        val renderedBody = if (bodyHtml.contains("<")) {
            bodyHtml
        } else {
            escapeHtml(bodyHtml).replace("\n", "<br>")
        }
        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$safeTitle</title>
              <style>
                body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.65;margin:24px;color:#171217;background:#fff}
                main{max-width:820px;margin:0 auto}
                h1{font-size:24px;line-height:1.3;margin:0 0 12px}
                .meta{color:#6f6470;font-size:14px;margin-bottom:24px}
                img{max-width:100%;height:auto;border-radius:8px}
                .comments{margin-top:36px;border-top:1px solid #eee;padding-top:20px}
                .comment{padding:12px 0;border-bottom:1px solid #f1edf1}
                .comment p{margin:6px 0 0}
                .replies{margin-left:18px;border-left:3px solid #f3d6e8;padding-left:14px}
                a{color:#d92b8c}
              </style>
            </head>
            <body>
              <main>
                <h1>$safeTitle</h1>
                <div class="meta">$typeLabel${if (safeAuthor.isNotBlank()) " · $safeAuthor" else ""} · <a href="$safeSource">$safeSource</a></div>
                <article>$renderedBody</article>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun requestBerrizJson(
        url: String,
        cookies: String,
        referer: String,
        requireSuccess: Boolean,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cookie", cookies)
            setRequestProperty("Origin", "https://berriz.in")
            setRequestProperty("Referer", referer)
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
        if (requireSuccess && root.optString("code") != "0000") {
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
            error("콘텐츠를 불러오지 못했습니다.")
        }
        return root
    }

    private fun parseVodPlayback(root: JSONObject): PlaybackInfo {
        val mediaObject = root.getJSONObject("data").getJSONObject("media")
        val playback = mediaObject.getJSONObject("vod")

        val title = mediaObject.optString("title", "Berriz video")
        val thumbnailUrl = mediaObject.optString("thumbnailUrl")
            .ifBlank { mediaObject.optString("thumbnailImageUrl") }
            .ifBlank { mediaObject.optString("imageUrl") }
        val hlsUrl = playback.optJSONObject("hls")?.optString("playbackUrl").orEmpty()
        val dashUrl = playback.optJSONObject("dash")?.optString("playbackUrl").orEmpty()
        val isDrm = playback.optBoolean("isDrm", false) || playback.hasMeaningfulDrmInfo()
        Log.i(logTag, "Playback extracted title=$title hls=${hlsUrl.isNotBlank()} dash=${dashUrl.isNotBlank()} drm=$isDrm")
        return PlaybackInfo(
            title = title,
            hlsUrl = hlsUrl,
            dashUrl = dashUrl,
            isDrm = isDrm,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun parseLivePlayback(root: JSONObject): PlaybackInfo {
        val mediaObject = root.getJSONObject("data").getJSONObject("media")
        val playback = mediaObject.getJSONObject("live").getJSONObject("replay")
        val title = mediaObject.optString("title", "Berriz live")
        val thumbnailUrl = mediaObject.optString("thumbnailUrl")
            .ifBlank { mediaObject.optString("thumbnailImageUrl") }
            .ifBlank { mediaObject.optString("imageUrl") }
        val hlsUrl = playback.optJSONObject("hls")?.optString("playbackUrl").orEmpty()
        val dashUrl = playback.optJSONObject("dash")?.optString("playbackUrl").orEmpty()
        val isDrm = playback.optBoolean("isDrm", false) || playback.hasMeaningfulDrmInfo()
        return PlaybackInfo(
            title = title,
            hlsUrl = hlsUrl,
            dashUrl = dashUrl,
            isDrm = isDrm,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun PlaybackInfo.downloadUrl(): String {
        return hlsUrl.ifBlank { dashUrl }
    }

    private fun JSONObject.hasMeaningfulDrmInfo(): Boolean {
        val drmInfo = opt("drmInfo") ?: return false
        if (drmInfo == JSONObject.NULL) return false
        return when (drmInfo) {
            is JSONObject -> drmInfo.length() > 0
            is JSONArray -> drmInfo.length() > 0
            is String -> drmInfo.isNotBlank() && !drmInfo.equals("null", ignoreCase = true)
            is Boolean -> drmInfo
            else -> true
        }
    }

    private fun JSONArray?.toImageUrls(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index)
                val imageUrl = item?.optString("imageUrl").orEmpty()
                if (imageUrl.isNotBlank()) add(imageUrl)
            }
        }
    }

    private fun firstImageUrl(root: JSONObject, html: String): String {
        return collectImageUrls(root).firstOrNull()
            ?: extractImageUrls(html).firstOrNull()
            ?: ""
    }

    private fun collectImageUrls(value: Any?): List<String> {
        val urls = linkedSetOf<String>()
        fun visit(item: Any?) {
            when (item) {
                is JSONObject -> {
                    val keys = item.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = item.opt(key)
                        if (key.contains("image", ignoreCase = true) && child is String && child.startsWith("http")) {
                            urls.add(child)
                        }
                        visit(child)
                    }
                }
                is JSONArray -> {
                    for (index in 0 until item.length()) visit(item.opt(index))
                }
                is String -> extractImageUrls(item).forEach { urls.add(it) }
            }
        }
        visit(value)
        return urls.toList()
    }

    private fun extractImageUrls(text: String): List<String> {
        val srcUrls = Regex("""(?i)<img[^>]+src=["']([^"']+)["']""")
            .findAll(text)
            .map { it.groupValues[1] }
        val directUrls = Regex("""https?://[^\s"'<>]+""")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')') }
        return (srcUrls + directUrls)
            .filter { it.startsWith("http") }
            .filter { candidate ->
                candidate.contains("image", ignoreCase = true) ||
                    candidate.contains("cdn", ignoreCase = true) ||
                    candidate.contains("statics", ignoreCase = true)
            }
            .distinct()
            .toList()
    }

    private fun previewMeta(playback: PlaybackInfo): String {
        return when (playback.kind) {
            BerrizContentKind.Video -> "영상"
            BerrizContentKind.Photo -> "사진 ${playback.photoUrls.size}장"
            BerrizContentKind.Post -> "게시글"
            BerrizContentKind.Notice -> "공지"
        }
    }

    private fun previewMeta(preparedItems: List<PreparedDownload>): String {
        if (preparedItems.size == 1) return previewMeta(preparedItems.first().playback)
        val videoCount = preparedItems.count { it.playback.kind == BerrizContentKind.Video }
        val photoCount = preparedItems.count { it.playback.kind == BerrizContentKind.Photo }
        return buildList {
            if (videoCount > 0) add("영상 ${videoCount}개")
            if (photoCount > 0) add("사진글 ${photoCount}개")
            val postCount = preparedItems.count { it.playback.kind == BerrizContentKind.Post }
            val noticeCount = preparedItems.count { it.playback.kind == BerrizContentKind.Notice }
            if (postCount > 0) add("게시글 ${postCount}개")
            if (noticeCount > 0) add("공지 ${noticeCount}개")
        }.joinToString(" + ")
    }

    private fun previewDetail(preparedItems: List<PreparedDownload>, skippedDuplicates: Int): String {
        val hasProtectedVideo = preparedItems.any {
            it.playback.kind == BerrizContentKind.Video && it.playback.isDrm
        }
        val base = if (hasProtectedVideo) {
            "보호된 콘텐츠라 영상 저장은 지원하지 않습니다. 썸네일은 저장할 수 있습니다."
        } else if (preparedItems.size == 1) {
            "아래 콘텐츠가 맞으면 다운로드를 눌러주세요."
        } else if (preparedItems.all { it.playback.kind == BerrizContentKind.Video }) {
            "${preparedItems.size}개 영상을 백그라운드에서 순서대로 저장합니다."
        } else {
            "${preparedItems.size}개 콘텐츠를 순서대로 저장합니다."
        }
        return if (skippedDuplicates > 0) {
            "$base 이미 저장한 ${skippedDuplicates}개는 제외했습니다."
        } else {
            base
        }
    }

    private fun List<PreparedDownload>.toQueuePreviewItems(): List<QueuePreviewItem> {
        return mapIndexed { index, prepared ->
            QueuePreviewItem(
                index = index + 1,
                title = prepared.playback.title.ifBlank { "제목 없음" },
                meta = previewMeta(prepared.playback),
                thumbnailUrl = prepared.playback.thumbnailUrl,
                kind = prepared.playback.kind,
            )
        }
    }

    private fun downloadWithYtdlp(
        playback: PlaybackInfo,
        media: BerrizMedia,
        cookies: String,
        batchIndex: Int = 0,
        batchTotal: Int = 1,
    ): String {
        ensureYtdlpReady()
        val downloadDir = File(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "BerrizDown/${media.id}-${System.currentTimeMillis()}"
        ).apply { mkdirs() }
        val safeTitle = safeFileName(playback.title.ifBlank { "berriz" })
        val outputTemplate = File(downloadDir, "$safeTitle.%(ext)s").absolutePath

        val request = YoutubeDLRequest(playback.downloadUrl()).apply {
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--referer", "https://berriz.in/")
            addOption("--add-header", "Origin:https://berriz.in")
            addOption("--add-header", "Cookie:$cookies")
            addOption("-f", formatSelector(state.value.qualityMode.maxHeight))
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
            val itemProgress = progress.coerceIn(0f, 100f) / 100f
            val overallProgress = ((batchIndex.toFloat() + itemProgress) / batchTotal.toFloat()).coerceIn(0f, 1f)
            val prefix = if (batchTotal > 1) "${batchIndex + 1}/$batchTotal " else ""
            _state.update {
                it.copy(
                    progress = overallProgress,
                    etaSeconds = etaInSeconds,
                    status = if (progress >= 100f) "${prefix}마무리 중입니다." else "${prefix}저장 중 ${progress.toInt()}%",
                    detail = playback.title.ifBlank { "화면을 닫지 말고 잠시 기다려주세요." },
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
        _state.update {
            it.copy(
                outputPath = galleryUri.toString(),
                outputMimeType = "video/mp4",
                outputHint = "사진첩 > 동영상 > BerrizDown",
            )
        }
        return galleryUri.toString()
    }

    private fun formatSelector(maxHeight: Int?): String {
        return if (maxHeight == null) {
            "bv*+ba/b"
        } else {
            "bv*[height<=${maxHeight}]+ba/b[height<=${maxHeight}]/b"
        }
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
        val image = downloadImage(thumbnailUrl)
        val extension = extensionForMime(image.mimeType)
        val displayName = "${safeFileName(title.ifBlank { "thumbnail" })}.$extension"
        return saveImageToGallery(image.bytes, image.mimeType, displayName)
    }

    private data class PhotoSaveResult(val count: Int, val firstUri: Uri)

    private data class DocumentSaveResult(val firstUri: Uri, val imageCount: Int)

    private fun savePhotoSetToGallery(playback: PlaybackInfo): PhotoSaveResult {
        val title = safeFileName(playback.title.ifBlank { "photo" })
        var firstUri: Uri? = null
        playback.photoUrls.forEachIndexed { index, url ->
            val image = downloadImage(url)
            val extension = extensionForMime(image.mimeType)
            val numberedTitle = if (playback.photoUrls.size == 1) title else "$title ${(index + 1).toString().padStart(2, '0')}"
            val uri = saveImageToGallery(image.bytes, image.mimeType, "$numberedTitle.$extension")
            if (firstUri == null) firstUri = uri
            _state.update {
                it.copy(
                    progress = (index + 1).toFloat() / playback.photoUrls.size.toFloat(),
                    status = "사진 저장 중 ${index + 1}/${playback.photoUrls.size}",
                )
            }
        }
        return PhotoSaveResult(playback.photoUrls.size, firstUri ?: error("저장할 사진을 찾지 못했습니다."))
    }

    private fun saveDocumentBackup(playback: PlaybackInfo): DocumentSaveResult {
        val title = safeFileName(playback.title.ifBlank { playback.kind.label })
        val htmlUri = saveTextToDownloads(
            bytes = playback.documentHtml.toByteArray(Charsets.UTF_8),
            mimeType = "text/html",
            displayName = "$title.html",
        )
        saveTextToDownloads(
            bytes = playback.documentJson.toByteArray(Charsets.UTF_8),
            mimeType = "application/json",
            displayName = "$title.json",
        )

        val imageUrls = (collectImageUrls(JSONObject(playback.documentJson)) + extractImageUrls(playback.documentHtml))
            .distinct()
            .take(80)
        var imageCount = 0
        imageUrls.forEachIndexed { index, url ->
            runCatching {
                val image = downloadImage(url)
                val extension = extensionForMime(image.mimeType)
                val displayName = "$title ${(index + 1).toString().padStart(2, '0')}.$extension"
                saveImageToGallery(image.bytes, image.mimeType, displayName)
                imageCount++
                _state.update {
                    it.copy(
                        progress = (index + 1).toFloat() / imageUrls.size.toFloat(),
                        status = "이미지 저장 중 ${index + 1}/${imageUrls.size}",
                    )
                }
            }
        }
        return DocumentSaveResult(htmlUri, imageCount)
    }

    private fun saveTextToDownloads(bytes: ByteArray, mimeType: String, displayName: String): Uri {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BerrizDown")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("저장할 수 없습니다.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private data class ImageBytes(val bytes: ByteArray, val mimeType: String)

    private fun downloadImage(imageUrl: String): ImageBytes {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "image/*")
        }
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            val mimeType = connection.contentType
                ?.substringBefore(";")
                ?.lowercase()
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"
            return ImageBytes(bytes, mimeType)
        } catch (error: IOException) {
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun extensionForMime(mimeType: String): String {
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun saveImageToGallery(bytes: ByteArray, mimeType: String, displayName: String): Uri {
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
            message.contains("보호된 콘텐츠") -> "보호된 콘텐츠는 앱에서 저장할 수 없습니다."
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
    private val downloadedIdsKey = "downloaded_media_ids"
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

        val batchJson = intent.getStringExtra(DownloadServiceContract.EXTRA_BATCH_JSON).orEmpty()
        val playbackUrl = intent.getStringExtra(DownloadServiceContract.EXTRA_PLAYBACK_URL).orEmpty()
        val cookies = intent.getStringExtra(DownloadServiceContract.EXTRA_COOKIES).orEmpty()
        val title = intent.getStringExtra(DownloadServiceContract.EXTRA_TITLE).orEmpty()
        val mediaId = intent.getStringExtra(DownloadServiceContract.EXTRA_MEDIA_ID).orEmpty()
        val mediaType = intent.getStringExtra(DownloadServiceContract.EXTRA_MEDIA_TYPE).orEmpty()
        val qualityMaxHeight = intent.getIntExtra(DownloadServiceContract.EXTRA_QUALITY_MAX_HEIGHT, 0)
            .takeIf { it > 0 }

        val items = if (batchJson.isNotBlank()) {
            parseVideoBatch(batchJson)
        } else {
            listOf(VideoDownloadItem(playbackUrl, title, mediaId, mediaType))
        }

        if (cookies.isBlank() || items.isEmpty() || items.any { it.playbackUrl.isBlank() || it.mediaId.isBlank() }) {
            sendState(false, null, "", "저장하지 못했습니다.", "다시 시도해주세요.")
            stopSelf()
            return
        }

        startForeground(
            notificationId,
            buildNotification(
                title = "저장을 시작합니다.",
                text = if (items.size > 1) "${items.size}개 영상을 순서대로 저장합니다." else "앱을 닫아도 계속 저장합니다.",
                progress = null,
                ongoing = true,
            )
        )
        sendState(
            true,
            null,
            "",
            if (items.size > 1) "${items.size}개 영상을 백그라운드에서 저장 중입니다." else "백그라운드에서 저장 중입니다.",
            "앱을 닫아도 계속 저장합니다.",
        )

        downloadJob = serviceScope.launch {
            runCatching {
                ensureYtdlpReady()
                var latestOutput = ""
                items.forEachIndexed { index, item ->
                    latestOutput = downloadWithYtdlp(
                        playbackUrl = item.playbackUrl,
                        cookies = cookies,
                        title = item.title,
                        mediaId = item.mediaId,
                        qualityMaxHeight = qualityMaxHeight,
                        batchIndex = index,
                        batchTotal = items.size,
                    )
                    markDownloaded(item.mediaType, item.mediaId)
                }
                latestOutput
            }.onSuccess { output ->
                notifyComplete(items.size)
                val completeDetail = if (items.size > 1) {
                    "${items.size}개 영상을 사진첩에 저장했습니다."
                } else {
                    "사진첩의 동영상 앨범에서 볼 수 있습니다."
                }
                sendState(false, 1f, output, "저장이 끝났습니다.", completeDetail, "video/mp4", "사진첩 > 동영상 > BerrizDown")
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

    private data class VideoDownloadItem(
        val playbackUrl: String,
        val title: String,
        val mediaId: String,
        val mediaType: String,
    )

    private fun parseVideoBatch(batchJson: String): List<VideoDownloadItem> {
        return runCatching {
            val array = JSONArray(batchJson)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        VideoDownloadItem(
                            playbackUrl = item.optString("playbackUrl"),
                            title = item.optString("title"),
                            mediaId = item.optString("mediaId"),
                            mediaType = item.optString("mediaType"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
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

    private fun downloadWithYtdlp(
        playbackUrl: String,
        cookies: String,
        title: String,
        mediaId: String,
        qualityMaxHeight: Int?,
        batchIndex: Int = 0,
        batchTotal: Int = 1,
    ): String {
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
            addOption("-f", formatSelector(qualityMaxHeight))
            addOption("-N", concurrentFragments.toString())
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--socket-timeout", "20")
            addOption("--remux-video", "mp4")
            addOption("--merge-output-format", "mp4")
            addOption("-o", outputTemplate)
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            val itemProgress = progress.coerceIn(0f, 100f) / 100f
            val normalizedProgress = ((batchIndex.toFloat() + itemProgress) / batchTotal.toFloat()).coerceIn(0f, 1f)
            val prefix = if (batchTotal > 1) "${batchIndex + 1}/$batchTotal " else ""
            val label = if (progress >= 100f) "${prefix}마무리 중입니다." else "${prefix}저장 중 ${progress.toInt()}%"
            val detail = if (batchTotal > 1) title.ifBlank { "앱을 닫아도 계속 저장합니다." } else "앱을 닫아도 계속 저장합니다."
            updateNotification(label, detail, normalizedProgress, ongoing = true)
            sendState(true, normalizedProgress, "", label, detail)
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

    private fun formatSelector(maxHeight: Int?): String {
        return if (maxHeight == null) {
            "bv*+ba/b"
        } else {
            "bv*[height<=${maxHeight}]+ba/b[height<=${maxHeight}]/b"
        }
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
        mimeType: String = "",
        outputHint: String = "",
    ) {
        sendBroadcast(
            Intent(DownloadServiceContract.ACTION_STATE).apply {
                setPackage(packageName)
                putExtra(DownloadServiceContract.EXTRA_BUSY, isBusy)
                progress?.let { putExtra(DownloadServiceContract.EXTRA_PROGRESS, it) }
                putExtra(DownloadServiceContract.EXTRA_OUTPUT, output)
                putExtra(DownloadServiceContract.EXTRA_OUTPUT_MIME, mimeType)
                putExtra(DownloadServiceContract.EXTRA_OUTPUT_HINT, outputHint)
                putExtra(DownloadServiceContract.EXTRA_STATUS, status)
                putExtra(DownloadServiceContract.EXTRA_DETAIL, detail)
            }
        )
    }

    private fun markDownloaded(mediaType: String, mediaId: String) {
        val preferences = getSharedPreferences("berrizdown_settings", Context.MODE_PRIVATE)
        val next = preferences.getStringSet(downloadedIdsKey, emptySet())
            .orEmpty()
            .toMutableSet()
        next.add("${mediaType.ifBlank { "media" }}:$mediaId")
        preferences.edit().putStringSet(downloadedIdsKey, next).apply()
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

    private fun notifyComplete(count: Int) {
        val text = if (count > 1) "${count}개 영상 저장 완료" else "사진첩에서 볼 수 있습니다."
        updateNotification("저장이 끝났습니다.", text, 1f, ongoing = false)
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
            message.contains("보호된 콘텐츠") -> "보호된 콘텐츠는 앱에서 저장할 수 없습니다."
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
                onOpenVideo = { openSavedMedia(context, state.outputPath, state.outputMimeType) },
                onThemeModeChange = viewModel::setThemeMode,
                onQualityModeChange = viewModel::setQualityMode,
                onAllowDuplicateDownloadsChange = viewModel::setAllowDuplicateDownloads,
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

private fun openSavedMedia(context: Context, path: String, mimeType: String) {
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
        setDataAndType(uri, mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "사진첩 열기"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun loadNetworkBitmap(url: String): Bitmap? {
    if (url.isBlank()) return null
    return runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
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
    onQualityModeChange: (DownloadQuality) -> Unit,
    onAllowDuplicateDownloadsChange: (Boolean) -> Unit,
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
            detectedLinkCount = state.detectedLinkCount,
            queueCount = state.queueItems.size,
            isVideoQueue = state.queueItems.size > 1 && state.queueItems.all { it.kind == BerrizContentKind.Video },
            qualityMode = state.qualityMode,
            onQualityModeChange = onQualityModeChange,
            allowDuplicateDownloads = state.allowDuplicateDownloads,
            onAllowDuplicateDownloadsChange = onAllowDuplicateDownloadsChange,
        )
        AnimatedVisibility(visible = state.previewReady) {
            PreviewCard(
                title = state.previewTitle,
                thumbnailUrl = state.previewThumbnailUrl,
                meta = state.previewMeta,
                queueItems = state.queueItems,
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
                    text = "링크를 넣어 베리즈 콘텐츠를 저장할 수 있는 앱입니다",
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
    detectedLinkCount: Int,
    queueCount: Int,
    isVideoQueue: Boolean,
    qualityMode: DownloadQuality,
    onQualityModeChange: (DownloadQuality) -> Unit,
    allowDuplicateDownloads: Boolean,
    onAllowDuplicateDownloadsChange: (Boolean) -> Unit,
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
                placeholder = { Text("Berriz 링크 여러 개 붙여넣기") },
            )
            if (detectedLinkCount > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "${detectedLinkCount}개의 링크를 찾았습니다. 확인 후 한 번에 저장할 수 있습니다.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            QualitySelector(
                selected = qualityMode,
                onChange = onQualityModeChange,
                enabled = !isBusy,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "중복 다시 저장",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "꺼두면 이미 저장한 링크는 건너뜁니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = allowDuplicateDownloads,
                    onCheckedChange = onAllowDuplicateDownloadsChange,
                    enabled = !isBusy,
                )
            }
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
                        Text(
                            when {
                                previewReady && queueCount > 1 && isVideoQueue -> "영상 전체 다운로드"
                                previewReady && queueCount > 1 -> "전체 다운로드"
                                previewReady -> "다운로드"
                                detectedLinkCount > 1 -> "전체 확인"
                                else -> "콘텐츠 확인"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySelector(
    selected: DownloadQuality,
    onChange: (DownloadQuality) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "품질",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadQuality.entries.forEach { quality ->
                val selectedQuality = selected == quality
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = enabled) { onChange(quality) },
                    color = if (selectedQuality) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = quality.label,
                        textAlign = TextAlign.Center,
                        color = if (selectedQuality) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    thumbnailUrl: String,
    meta: String,
    queueItems: List<QueuePreviewItem>,
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
            if (meta.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = meta,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Text(
                text = "이 콘텐츠가 맞으면 다운로드를 누르세요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (queueItems.size > 1) {
                QueuePreviewList(queueItems)
            }
        }
    }
}

@Composable
private fun QueuePreviewList(items: List<QueuePreviewItem>) {
    val allVideo = items.all { it.kind == BerrizContentKind.Video }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "저장 대기열",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                color = if (allVideo) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = if (allVideo) "백그라운드 저장" else "${items.size}개",
                    color = if (allVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            text = if (allVideo) {
                "위에서부터 순서대로 저장하고, 앱을 닫아도 계속 진행됩니다."
            } else {
                "위에서부터 순서대로 저장합니다."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        items.take(12).forEach { item ->
            QueuePreviewRow(item)
        }
        if (items.size > 12) {
            Text(
                text = "외 ${items.size - 12}개",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun QueuePreviewRow(item: QueuePreviewItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueuePreviewThumb(item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.index}. ${item.meta}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QueuePreviewThumb(item: QueuePreviewItem) {
    val bitmap = rememberNetworkBitmap(item.thumbnailUrl)
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.kind.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
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
    val bitmap = rememberNetworkBitmap(url)

    if (bitmap != null) {
        Box(modifier = modifier) {
            Image(
                bitmap = bitmap.asImageBitmap(),
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
private fun rememberNetworkBitmap(url: String): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            loadNetworkBitmap(url)
        }
    }
    return bitmap
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
                        text = state.outputHint.ifBlank { "사진첩 > BerrizDown" },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenVideo) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("사진첩 열기")
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
                    configureLoginWebView(
                        onTitleChange = { title = it },
                        onPageFinished = onPageFinished,
                    )
                    loadUrl("https://berriz.in/ko/")
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureLoginWebView(
    onTitleChange: (String) -> Unit,
    onPageFinished: () -> Unit,
) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = userAgentString.toLoginUserAgent()
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleNonHttpLoginUrl(view.context, request.url)
        }

        @Deprecated("Deprecated in Android framework")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleNonHttpLoginUrl(view.context, Uri.parse(url))
        }

        override fun onPageFinished(view: WebView, url: String) {
            onTitleChange(view.title ?: "Berriz 로그인")
            CookieManager.getInstance().flush()
            onPageFinished()
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val popup = WebView(view.context).apply {
                configureLoginWebView(onTitleChange, onPageFinished)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        return if (isHttpUrl(uri)) {
                            view.loadUrl(uri.toString())
                            true
                        } else {
                            handleNonHttpLoginUrl(view.context, uri)
                        }
                    }

                    @Deprecated("Deprecated in Android framework")
                    override fun shouldOverrideUrlLoading(popupView: WebView, url: String): Boolean {
                        val uri = Uri.parse(url)
                        return if (isHttpUrl(uri)) {
                            view.loadUrl(url)
                            true
                        } else {
                            handleNonHttpLoginUrl(view.context, uri)
                        }
                    }
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }
}

private fun String.toLoginUserAgent(): String {
    return replace("; wv", "")
        .replace("Version/4.0 ", "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun handleNonHttpLoginUrl(context: Context, uri: Uri): Boolean {
    if (isHttpUrl(uri)) return false
    val intent = when (uri.scheme?.lowercase()) {
        "intent" -> runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
        "market" -> Intent(Intent.ACTION_VIEW, uri)
        else -> null
    } ?: return false
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun isHttpUrl(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}
