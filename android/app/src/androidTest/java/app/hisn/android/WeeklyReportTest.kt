package app.hisn.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyReportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val hour = 3_600_000L

    @Test fun firesOnTheNextSundayEvening() {
        // Wednesday 21 Oct 2026 → Sunday 25 Oct at 19:00.
        assertEquals(LocalDateTime.of(2026, 10, 25, 19, 0), WeeklyReport.nextFire(LocalDateTime.of(2026, 10, 21, 10, 0)))
        // Sunday before 19:00 → the same evening; after it → next Sunday.
        assertEquals(LocalDateTime.of(2026, 10, 25, 19, 0), WeeklyReport.nextFire(LocalDateTime.of(2026, 10, 25, 18, 59)))
        assertEquals(LocalDateTime.of(2026, 11, 1, 19, 0), WeeklyReport.nextFire(LocalDateTime.of(2026, 10, 25, 19, 0)))
    }

    @Test fun aLighterWeekSaysHowMuchLessAndOffersToShare() {
        val (title, body) = WeeklyReport.words(context, WeeklyReport.Week(feedMs = 2 * hour, beforeMs = 3 * hour, lastWeekMs = -1, days = 7))
        assertTrue(title, title.contains("33%"))
        assertTrue(body, body.contains("Share your week"))
    }

    @Test fun aHeavierWeekIsHonestWithoutBlame() {
        val (title, body) = WeeklyReport.words(context, WeeklyReport.Week(feedMs = 4 * hour, beforeMs = 2 * hour, lastWeekMs = -1, days = 7))
        assertTrue(body, body.contains("100%"))
        // Feed apps may be used for study or messages: never "you scrolled".
        assertFalse(title + body, (title + body).contains("scroll", ignoreCase = true))
    }

    @Test fun withoutABeforeItComparesWithLastWeek() {
        val (title, _) = WeeklyReport.words(context, WeeklyReport.Week(feedMs = hour, beforeMs = -1, lastWeekMs = 2 * hour, days = 7))
        assertTrue(title, title.contains("last week"))
    }
}
