package jp.example.locpic.data

import android.content.Context
import androidx.core.content.edit
import jp.example.locpic.model.DeliveryStatus
import jp.example.locpic.model.PhotoRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PhotoRepository(context: Context) {
    private val preferences = context.getSharedPreferences("photo_records", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _records = MutableStateFlow(load())

    val records: StateFlow<List<PhotoRecord>> = _records.asStateFlow()

    fun add(record: PhotoRecord) = synchronized(lock) {
        persist(listOf(record) + _records.value.filterNot { it.id == record.id })
    }

    fun update(record: PhotoRecord) = synchronized(lock) {
        persist(_records.value.map { if (it.id == record.id) record else it })
    }

    fun failed(): List<PhotoRecord> = _records.value.filter { it.status == DeliveryStatus.FAILED }

    private fun persist(records: List<PhotoRecord>) {
        val sorted = records.sortedByDescending(PhotoRecord::capturedAtMillis)
        val array = JSONArray()
        sorted.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_RECORDS, array.toString()) }
        _records.value = sorted
    }

    private fun load(): List<PhotoRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val record = array.getJSONObject(index).toPhotoRecord()
                    if (File(record.filePath).exists()) add(record)
                }
            }.sortedByDescending(PhotoRecord::capturedAtMillis)
        }.getOrDefault(emptyList())
    }

    private fun PhotoRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        put("note", note)
        put("capturedAtMillis", capturedAtMillis)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("accuracyMeters", accuracyMeters?.toDouble() ?: JSONObject.NULL)
        put("status", status.name)
        put("lastError", lastError ?: JSONObject.NULL)
        put("slackFileId", slackFileId ?: JSONObject.NULL)
    }

    private fun JSONObject.toPhotoRecord() = PhotoRecord(
        id = getString("id"),
        filePath = getString("filePath"),
        note = optString("note"),
        capturedAtMillis = getLong("capturedAtMillis"),
        latitude = nullableDouble("latitude"),
        longitude = nullableDouble("longitude"),
        accuracyMeters = nullableDouble("accuracyMeters")?.toFloat(),
        status = runCatching { DeliveryStatus.valueOf(getString("status")) }
            .getOrDefault(DeliveryStatus.FAILED)
            .let { if (it == DeliveryStatus.SENDING) DeliveryStatus.FAILED else it },
        lastError = nullableString("lastError"),
        slackFileId = nullableString("slackFileId"),
    )

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless(Double::isNaN)

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private companion object {
        const val KEY_RECORDS = "records"
    }
}
