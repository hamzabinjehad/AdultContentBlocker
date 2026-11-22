package app.hisn.android.nativeshell

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saves the Progress hero and medal wall drawn from sample numbers. Runs only with `-e shots 1`. */
@RunWith(AndroidJUnit4::class)
class UnrotProgressShots {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun heroAndMedals() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("shots") == "1")
        val dark = args.getString("dark") == "1"
        val base = WinsBaseline(feedMs = 190 * 60_000L, opens = 71, firstMinute = 7 * 60 + 5, lastMinute = 40,
            longestMs = 58 * 60_000L, hours = null, firstWeek = false)
        val w = WinsSummary(day = 12, start = java.time.LocalDate.now().minusDays(11), base = base,
            nowFeedMs = 105 * 60_000L, nowDays = 7,
            daily = listOf(196L, 182L, 170L, 151L, 160L, 128L, 117L, 104L, 96L).map { it * 60_000L },
            givenBackMs = 17 * 3_600_000L, yesterdayGainMs = 85 * 60_000L, nowOpens = 38, nowFirst = 9 * 60 + 40,
            nowLast = 22 * 60 + 50, nowLongestMs = 21 * 60_000L, nowHours = null)
        rule.setContent {
            HisnTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().unrotBackdrop().systemBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (args.getString("heavy") == "1") {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val heavyBase = WinsBaseline(feedMs = 50 * 60_000L, opens = 7, firstMinute = 8 * 60, lastMinute = 30,
                            longestMs = 30 * 60_000L, hours = null, firstWeek = false)
                        val heavy = WinsSummary(day = 9, start = java.time.LocalDate.now().minusDays(8), base = heavyBase,
                            nowFeedMs = 66 * 60_000L, nowDays = 7, daily = listOf(50L, 60L, 70L, 66L).map { it * 60_000L },
                            givenBackMs = 0, yesterdayGainMs = 0, nowOpens = 9, nowFirst = 8 * 60, nowLast = 50,
                            nowLongestMs = 40 * 60_000L, nowHours = null, topApp = "com.google.android.youtube",
                            topAppMs = 66 * 60_000L, peakStart = 0)
                        UnrotHeavyCard(heavy, androidx.compose.runtime.remember { HisnState(ctx, FakeShellActions()) }, 0, {})
                    } else {
                        UnrotProgressHero(w)
                        UnrotMedalWall(12)
                    }
                }
            }
        }
        rule.waitForIdle(); Thread.sleep(900)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, (if (args.getString("heavy") == "1") "heads-up-" else "progress-hero-") + (if (dark) "dark" else "light") + ".png").outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
