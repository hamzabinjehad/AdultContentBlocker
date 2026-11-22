package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.android.HardHoursNudge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Progress names the hard hours and arms the opt-in nudge 15 minutes before them. */
@RunWith(AndroidJUnit4::class)
class ProgressHardHoursTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun off() = HardHoursNudge.set(context, false)
    @After fun reset() = HardHoursNudge.set(context, false)

    @Test fun nudgeTimeIsFifteenMinutesBeforeTheWindowAndWrapsPastMidnight() {
        assertEquals("21:45", HardHoursNudge.time(HardHoursNudge.nudgeMinute(22)))
        assertEquals("23:45", HardHoursNudge.time(HardHoursNudge.nudgeMinute(0)))
    }

    @Test fun hardHoursCardTurnsTheNudgeOnAndOff() {
        assumeTrue(unrotEdition)
        val hours = List(24) { if (it in 22..23) 5 else 1 }
        val actions = FakeShellActions().apply {
            personalSummary = PersonalSummary(greeting = "Hi", day = 3, pauses = 12, hours = hours, peakStart = 22)
        }
        rule.showShell(actions, tab = 2)
        rule.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
            .performScrollToNode(hasText("Nudge me at 21:45"))
        rule.onNodeWithText("Your hard hours: 22:00–01:00").assertIsDisplayed()
        rule.onNode(hasText("Nudge me at 21:45") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(HardHoursNudge.isOn(context))
        rule.onNodeWithText("Nudge on · 21:45").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Turn off") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(HardHoursNudge.isOn(context))
    }
}
