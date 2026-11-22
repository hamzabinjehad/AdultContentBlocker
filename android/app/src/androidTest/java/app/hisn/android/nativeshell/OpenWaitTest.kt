package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenWaitTest {
    @get:Rule val rule = createComposeRule()

    private var closed = 0
    private var opened = 0

    private fun showWait(seconds: Int = 3, reason: String = "My family") {
        // The breath circle never stops, so the test drives the clock itself.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HisnTheme(darkTheme = false) {
                OpenWaitScreen(
                    appLabel = "Chrome", appPackage = "com.android.chrome", seconds = seconds,
                    openedToday = 2, closedToday = 3, reason = reason,
                    onClose = { closed++ }, onOpen = { opened++ }
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test fun openUnlocksOnlyAfterTheWait() {
        showWait(seconds = 3)
        rule.onNodeWithText("Take a breath first").assertIsDisplayed()
        rule.onNodeWithText("Open in 3 seconds").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(1_100)
        rule.onNodeWithText("Open in 2 seconds").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(2_100)
        rule.onNodeWithText("The wait is over. You choose.").assertIsDisplayed()
        rule.onNodeWithText("Open Chrome").assertIsEnabled().performClick()
        assertEquals(1, opened)
        assertEquals(0, closed)
    }

    @Test fun aTimedVisitIsOfferedOnceTheWaitIsOver() {
        var minutes = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HisnTheme(darkTheme = false) {
                OpenWaitScreen(
                    appLabel = "Chrome", appPackage = "com.android.chrome", seconds = 3,
                    openedToday = 0, closedToday = 0, reason = "",
                    onClose = { closed++ }, onOpen = { opened++ }, onOpenFor = { minutes = it }
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(rule.onAllNodes(hasText("5 min")).fetchSemanticsNodes().isEmpty())
        rule.mainClock.advanceTimeBy(3_200)
        rule.onNodeWithText("5 min").performClick()
        assertEquals(5, minutes)
        assertEquals(0, opened)
    }

    @Test fun closeWorksAtOnceAndShowsTodaysCounts() {
        showWait()
        rule.onNodeWithText("Today: opened 2 · closed instead 3").assertIsDisplayed()
        rule.onNodeWithText("You said: My family").assertIsDisplayed()
        rule.onNodeWithText("Close Chrome").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, closed)
        assertEquals(0, opened)
    }

    @Test fun noReasonLineWithoutAReason() {
        showWait(reason = "")
        assertTrue(rule.onAllNodes(hasText("You said", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test fun settingsSaveTheAppsTheWaitAndGrowth() {
        var saved: Triple<Set<String>, Int, Boolean>? = null
        rule.setContent {
            HisnTheme(darkTheme = false) {
                OpenWaitSettings(
                    apps = listOf(InstalledApp("com.android.chrome", "Chrome"), InstalledApp("com.example.feed", "Feed")),
                    chosen = setOf("com.android.chrome"), seconds = 10, grows = true, guardOn = false,
                    onChange = { a, s, g -> saved = Triple(a, s, g) }, onFixGuard = {}, onBack = {}
                )
            }
        }
        rule.onNodeWithText("The wait runs through App protection, which is off.").assertIsDisplayed()
        rule.onNodeWithText("30 s").performClick()
        assertEquals(Triple(setOf("com.android.chrome"), 30, true), saved)
        rule.onNode(hasText("Feed") and isToggleable()).performClick()
        assertEquals(setOf("com.android.chrome", "com.example.feed"), saved!!.first)
        rule.onNode(hasText("Longer each time") and isToggleable()).performClick()
        assertEquals(false, saved!!.third)
    }
}
