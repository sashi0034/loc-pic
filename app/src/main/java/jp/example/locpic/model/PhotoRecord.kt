package jp.example.locpic.model

enum class DeliveryStatus {
    POSTED,
    FAILED,
    SENDING,
}

data class PhotoRecord(
    val id: String,
    val filePath: String,
    val note: String,
    val capturedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val status: DeliveryStatus,
    val lastError: String? = null,
    val slackFileId: String? = null,
)
