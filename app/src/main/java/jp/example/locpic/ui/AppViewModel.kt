package jp.example.locpic.ui

import android.app.Application
import android.location.Location
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.example.locpic.BuildConfig
import jp.example.locpic.data.PhotoRepository
import jp.example.locpic.location.DeviceLocationProvider
import jp.example.locpic.model.DeliveryStatus
import jp.example.locpic.model.PhotoRecord
import jp.example.locpic.model.PhotoText
import jp.example.locpic.network.SlackClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UiNotice(
    val title: String,
    val details: String,
    val isError: Boolean,
)

data class CaptureUiState(
    val isWorking: Boolean = false,
    val stage: String = "",
    val notice: UiNotice? = null,
)

data class RetryUiState(
    val isWorking: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val notice: UiNotice? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private val locationProvider = DeviceLocationProvider(application)
    private val slackClient = SlackClient(BuildConfig.SLACK_BOT_TOKEN, BuildConfig.SLACK_CHANNEL_ID)

    val records: StateFlow<List<PhotoRecord>> = repository.records
    val isSlackConfigured: Boolean =
        BuildConfig.SLACK_BOT_TOKEN.startsWith("xoxb-") && BuildConfig.SLACK_CHANNEL_ID.isNotBlank()

    private val _captureState = MutableStateFlow(CaptureUiState())
    val captureState: StateFlow<CaptureUiState> = _captureState.asStateFlow()

    private val _retryState = MutableStateFlow(RetryUiState())
    val retryState: StateFlow<RetryUiState> = _retryState.asStateFlow()

    fun captureAndPost(imageCapture: ImageCapture, note: String) {
        if (_captureState.value.isWorking) return
        viewModelScope.launch {
            _captureState.value = CaptureUiState(isWorking = true, stage = "撮影しています…")

            val capturedAt = System.currentTimeMillis()
            val photoDirectory = File(getApplication<Application>().filesDir, "photos").apply { mkdirs() }
            val photoFile = File(photoDirectory, "locpic_${capturedAt}_${UUID.randomUUID()}.jpg")

            try {
                takePhoto(imageCapture, photoFile)
                _captureState.value = CaptureUiState(isWorking = true, stage = "位置情報を取得しています…")
                val location = locationProvider.currentLocation()
                val record = createRecord(photoFile, note.trim(), capturedAt, location)
                repository.add(record)

                _captureState.value = CaptureUiState(isWorking = true, stage = "Slackへ投稿しています…")
                val upload = slackClient.upload(record)
                upload.fold(
                    onSuccess = { result ->
                        val posted = record.copy(
                            status = DeliveryStatus.POSTED,
                            lastError = null,
                            slackFileId = result.fileId,
                        )
                        repository.update(posted)
                        _captureState.value = CaptureUiState(
                            notice = UiNotice(
                                title = "投稿しました",
                                details = record.summary(),
                                isError = false,
                            ),
                        )
                    },
                    onFailure = { error ->
                        val failed = record.copy(
                            status = DeliveryStatus.FAILED,
                            lastError = error.userMessage(),
                        )
                        repository.update(failed)
                        _captureState.value = CaptureUiState(
                            notice = UiNotice(
                                title = "投稿できなかったため端末に保存しました",
                                details = "${record.summary()}\n理由: ${error.userMessage()}",
                                isError = true,
                            ),
                        )
                    },
                )
            } catch (error: Throwable) {
                photoFile.delete()
                _captureState.value = CaptureUiState(
                    notice = UiNotice(
                        title = "撮影できませんでした",
                        details = error.userMessage(),
                        isError = true,
                    ),
                )
            }
        }
    }

    fun retryAll() {
        if (_retryState.value.isWorking) return
        val failed = repository.failed()
        if (failed.isEmpty()) {
            _retryState.value = RetryUiState(
                notice = UiNotice("未送信の写真はありません", "すべて送信済みです", false),
            )
            return
        }

        viewModelScope.launch {
            var succeeded = 0
            _retryState.value = RetryUiState(isWorking = true, total = failed.size)
            failed.forEachIndexed { index, record ->
                val sending = record.copy(status = DeliveryStatus.SENDING, lastError = null)
                repository.update(sending)
                slackClient.upload(sending).fold(
                    onSuccess = { result ->
                        succeeded++
                        repository.update(
                            sending.copy(status = DeliveryStatus.POSTED, slackFileId = result.fileId),
                        )
                    },
                    onFailure = { error ->
                        repository.update(
                            sending.copy(status = DeliveryStatus.FAILED, lastError = error.userMessage()),
                        )
                    },
                )
                _retryState.value = RetryUiState(
                    isWorking = true,
                    completed = index + 1,
                    total = failed.size,
                )
            }
            val remaining = failed.size - succeeded
            _retryState.value = RetryUiState(
                completed = failed.size,
                total = failed.size,
                notice = UiNotice(
                    title = if (remaining == 0) "一括再送が完了しました" else "$succeeded 件を送信しました",
                    details = if (remaining == 0) {
                        "${failed.size} 件すべてをSlackへ投稿しました"
                    } else {
                        "$remaining 件は送信できず、端末に残しています"
                    },
                    isError = remaining > 0,
                ),
            )
        }
    }

    private fun createRecord(
        file: File,
        note: String,
        capturedAt: Long,
        location: Location?,
    ) = PhotoRecord(
        id = UUID.randomUUID().toString(),
        filePath = file.absolutePath,
        note = note,
        capturedAtMillis = capturedAt,
        latitude = location?.latitude,
        longitude = location?.longitude,
        accuracyMeters = location?.accuracy,
        status = DeliveryStatus.SENDING,
    )

    private suspend fun takePhoto(imageCapture: ImageCapture, target: File): Unit =
        suspendCancellableCoroutine { continuation ->
            val options = ImageCapture.OutputFileOptions.Builder(target).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(getApplication()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }

    private fun PhotoRecord.summary(): String = buildString {
        appendLine("内容: ${note.ifBlank { "（メモなし）" }}")
        appendLine("撮影時刻: ${PhotoText.capturedAt(capturedAtMillis)}")
        append("場所: ${PhotoText.mapsUrl(latitude, longitude) ?: "位置情報なし"}")
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}
