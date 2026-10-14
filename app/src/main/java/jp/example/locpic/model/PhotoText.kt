package jp.example.locpic.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PhotoText {
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)

    fun capturedAt(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(displayFormatter)

    fun mapsUrl(latitude: Double?, longitude: Double?): String? =
        if (latitude != null && longitude != null) {
            "https://www.google.com/maps?q=$latitude,$longitude"
        } else {
            null
        }

    fun slackComment(record: PhotoRecord): String = buildString {
        appendLine("📷 写真を撮影しました")
        appendLine("内容: ${record.note.ifBlank { "（メモなし）" }}")
        appendLine("撮影時刻: ${capturedAt(record.capturedAtMillis)}")
        val map = mapsUrl(record.latitude, record.longitude)
        if (map != null) {
            append("場所: $map")
        } else {
            append("場所: 位置情報を取得できませんでした")
        }
    }
}
