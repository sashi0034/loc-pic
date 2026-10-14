package jp.example.locpic.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class PhotoTextTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun mapsUrl_usesGoogleMapsQueryFormat() {
        assertEquals(
            "https://www.google.com/maps?q=35.681236,139.767125",
            PhotoText.mapsUrl(35.681236, 139.767125),
        )
        assertNull(PhotoText.mapsUrl(null, 139.767125))
    }

    @Test
    fun slackComment_containsMemoTimeAndMapLink() {
        val record = PhotoRecord(
            id = "test",
            filePath = "photo.jpg",
            note = "東京駅の丸の内口",
            capturedAtMillis = 1_767_225_600_000,
            latitude = 35.681236,
            longitude = 139.767125,
            accuracyMeters = 8f,
            status = DeliveryStatus.SENDING,
        )

        val comment = PhotoText.slackComment(record)

        assert(comment.contains("東京駅の丸の内口"))
        assert(comment.contains("2026/01/01 09:00:00"))
        assert(comment.contains("https://www.google.com/maps?q=35.681236,139.767125"))
    }
}
