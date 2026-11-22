package app.hisn.android.nativeshell

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.android.BlockActivity
import app.hisn.android.DnsVpnService
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A screenshot of every Unrot screen with believable data, for design review. Runs only with
 * `-e shots 1`. Files land in the app's external files under `screens/`.
 */
@RunWith(AndroidJUnit4::class)
class UnrotScreenShots {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun only() {
        assumeTrue(unrotEdition && InstrumentationRegistry.getArguments().getString("shots") == "1")
        FocusRequest.open.value = false
    }

    private fun save(name: String) {
        rule.waitForIdle()
        Thread.sleep(700)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun actions(healthy: Boolean = true, day: Int = 12) = FakeShellActions().apply {
        personalSummary = PersonalSummary(
            greeting = "Good evening, Sam", reason = "Evenings with my family", name = "Sam",
            customReason = "Go to sleep. Tomorrow-you has a big day.",
            hours = List(24) { if (it in 22..23) 4 else if (it == 0) 3 else if (it in 13..14) 1 else 0 },
            peakStart = 22, day = day, pauses = 9
        )
        if (healthy) {
            liveProtection = ProtectionRuntime(dns = DnsVpnService.State.RUNNING, dnsRequested = true,
                guardExpected = true, guardReady = true)
            grantedPermissions += listOf("accessibility", "notifications", "usage", "dns")
        }
    }

    private fun shell(name: String, tab: Int = 0, detail: String? = null, healthy: Boolean = true, settleMs: Long = 0) {
        rule.showShell(actions(healthy), tab = tab, detail = detail)
        if (settleMs > 0) { rule.waitForIdle(); Thread.sleep(settleMs) }
        save(name)
    }

    @Test fun today() = shell("01-today")
    @Test fun todayBroken() = shell("02-today-broken", healthy = false)
    @Test fun shield() = shell("03-shield", tab = 1)
    @Test fun shieldBroken() = shell("04-shield-broken", tab = 1, healthy = false)
    @Test fun progress() = shell("05-progress", tab = 2, settleMs = 4000)
    @Test fun you() = shell("06-you", tab = 3)
    @Test fun more() = shell("07-more", detail = "more")
    @Test fun rules() = shell("08-all-rules", detail = "rules")
    @Test fun sites() = shell("09-sites", detail = "domains")
    @Test fun words() = shell("10-words", detail = "words")
    @Test fun apps() = shell("11-apps", detail = "apps")
    @Test fun limits() = shell("12-limits", detail = "limit")
    @Test fun schedule() = shell("13-schedule", detail = "schedule")
    @Test fun diagnostics() = shell("15-health", detail = "diagnostics")
    @Test fun permissions() = shell("16-permissions", detail = "permissions")
    @Test fun phone() = shell("17-phone", detail = "phone")
    @Test fun lock() = shell("18-lock", detail = "lock")
    @Test fun personal() = shell("19-personal", detail = "personal")
    @Test fun listUpdate() = shell("20-list-update", detail = "listupdate")
    @Test fun rulesFile() = shell("21-rules-file", detail = "rulesfile")
    @Test fun progressPage() = shell("22-progress-page", detail = "progress", settleMs = 4000)
    @Test fun info() = shell("23-how-it-works", detail = "info:how")
    @Test fun why() = shell("27-why-this-works", detail = "info:why")
    @Test fun notes() = shell("28-notes", detail = "notes")
    @Test fun lists() = shell("24-lists", detail = "lists")
    @Test fun support() = shell("25-support", detail = "diagnostics:full")
    @Test fun progressDay() = shell("26-progress-day", detail = "progress:day:" + java.time.LocalDate.now().minusDays(1).toEpochDay())

    private fun block(name: String, vararg extras: Pair<String, Any>) {
        val intent = Intent(context, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        extras.forEach { (k, v) -> if (v is Boolean) intent.putExtra(k, v) else intent.putExtra(k, v.toString()) }
        ActivityScenario.launch<BlockActivity>(intent).use {
            Thread.sleep(3000)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
            val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
    }

    @Test fun todayFirstWeek() {
        rule.showShell(actions(day = 3), tab = 0)
        rule.waitForIdle(); Thread.sleep(2500)
        save("37-today-first-week")
    }

    @Test fun waitScreen() {
        // The breath never stops, so the test drives the clock past the wait itself.
        rule.setContent {
            HisnTheme(darkTheme = LocalConfigurationDark()) {
                OpenWaitScreen(
                    appLabel = "YouTube", appPackage = "com.google.android.youtube", seconds = 0,
                    openedToday = 2, closedToday = 5, reason = "Go to sleep. Tomorrow-you has a big day.",
                    onClose = {}, onOpen = {}, onOpenFor = {}
                )
            }
        }
        rule.waitForIdle(); Thread.sleep(800)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "36-wait-done.png").outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    @androidx.compose.runtime.Composable
    private fun LocalConfigurationDark() = androidx.compose.foundation.isSystemInDarkTheme()

    @Test fun blockFocus() {
        app.hisn.android.FocusSession.start(context, 25, setOf("com.google.android.youtube"))
        try { block("34-block-focus", "focus" to true, "app" to "com.google.android.youtube") }
        finally { app.hisn.android.FocusSession.stop(context) }
    }
    @Test fun focusSheet() {
        rule.showShell(actions(), tab = 0)
        rule.onNode(androidx.compose.ui.test.hasText("Focus") and androidx.compose.ui.test.hasClickAction())
            .performClick()
        save("35-focus-sheet")
        FocusRequest.open.value = false
    }
    @Test fun blockFeed() = block("30-block-feed", "shortvideo" to true, "app" to "com.google.android.youtube")
    @Test fun blockQuiet() = block("31-block-quiet", "schedule" to true, "app" to "com.google.android.youtube")
    @Test fun blockLimit() = block("32-block-limit", "limit" to true, "app" to "com.google.android.youtube")
    @Test fun blockApp() = block("33-block-app", "app" to "com.google.android.youtube")

    @Test fun winsCard() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val base = WinsBaseline(feedMs = 190 * 60_000L, opens = 71, firstMinute = 7 * 60 + 5, lastMinute = 40,
            longestMs = 58 * 60_000L, hours = null, firstWeek = false)
        val summary = WinsSummary(day = 12, start = java.time.LocalDate.now().minusDays(11), base = base,
            nowFeedMs = 105 * 60_000L, nowDays = 7, daily = listOf(180L, 150L, 160L, 120L).map { it * 60_000L },
            givenBackMs = 17 * 3_600_000L, yesterdayGainMs = 85 * 60_000L, nowOpens = 38, nowFirst = 9 * 60 + 40,
            nowLast = 22 * 60 + 50, nowLongestMs = 21 * 60_000L, nowHours = null)
        val bitmap = drawWinsCard(context, summary)
        val dir = java.io.File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        java.io.File(dir, "30-wins-card.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
