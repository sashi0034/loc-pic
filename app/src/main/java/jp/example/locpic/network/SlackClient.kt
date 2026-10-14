package jp.example.locpic.network

import jp.example.locpic.model.PhotoRecord
import jp.example.locpic.model.PhotoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SlackUploadResult(val fileId: String)

class SlackClient(
    private val token: String,
    private val channelId: String,
) {
    suspend fun upload(record: PhotoRecord): Result<SlackUploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.startsWith("xoxb-")) {
                "Slack Bot Tokenが未設定です。secrets.ymlを確認してください"
            }
            require(channelId.isNotBlank()) {
                "SlackチャンネルIDが未設定です。secrets.ymlを確認してください"
            }

            val photo = File(record.filePath)
            require(photo.isFile) { "保存した写真が見つかりません" }

            val uploadInfo = postSlackForm(
                method = "files.getUploadURLExternal",
                parameters = mapOf(
                    "filename" to photo.name,
                    "length" to photo.length().toString(),
                    "alt_txt" to record.note.ifBlank { "位置情報付き写真" },
                ),
            )
            val uploadUrl = uploadInfo.getString("upload_url")
            val fileId = uploadInfo.getString("file_id")

            uploadBytes(uploadUrl, photo)

            val files = JSONArray().put(
                JSONObject()
                    .put("id", fileId)
                    .put("title", record.note.ifBlank { "LocPic ${photo.nameWithoutExtension}" }),
            )
            postSlackForm(
                method = "files.completeUploadExternal",
                parameters = mapOf(
                    "files" to files.toString(),
                    "channel_id" to channelId,
                    "initial_comment" to PhotoText.slackComment(record),
                ),
            )

            SlackUploadResult(fileId)
        }
    }

    private fun postSlackForm(method: String, parameters: Map<String, String>): JSONObject {
        val body = parameters.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }.toByteArray(StandardCharsets.UTF_8)

        val connection = URI("https://slack.com/api/$method").toURL().openConnection() as HttpURLConnection
        return connection.useConnection {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setFixedLengthStreamingMode(body.size)
            outputStream.use { it.write(body) }

            val response = readResponse(this)
            val json = JSONObject(response)
            if (responseCode !in 200..299 || !json.optBoolean("ok")) {
                error("Slack APIエラー: ${json.optString("error", "HTTP $responseCode")}")
            }
            json
        }
    }

    private fun uploadBytes(uploadUrl: String, photo: File) {
        val connection = URI(uploadUrl).toURL().openConnection() as HttpURLConnection
        connection.useConnection {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(photo.length())
            outputStream.use { output -> photo.inputStream().use { input -> input.copyTo(output) } }
            val response = readResponse(this)
            if (responseCode !in 200..299) {
                error("画像アップロードエラー: HTTP $responseCode ${response.take(160)}")
            }
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T =
        try {
            block()
        } finally {
            disconnect()
        }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
