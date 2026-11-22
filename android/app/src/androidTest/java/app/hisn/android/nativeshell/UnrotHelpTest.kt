package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unrot's offered help: the first-visit tour, explain mode and the page explainer. */
@RunWith(AndroidJUnit4::class)
class UnrotHelpTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun unrotOnly() = assumeTrue(unrotEdition)
    @After fun reset() = Tour.reset(context, done = true)

    @Test fun tourShowsItsStopsInOrderAndNeverReturnsAfterASkip() {
        val actions = FakeShellActions().apply {
            personalSummary = PersonalSummary(greeting = "Hi", day = 3, pauses = 1)
        }
        rule.showShell(actions, tour = true)
        rule.waitForIdle()
        rule.onNodeWithText("Your day count").assertIsDisplayed()
        rule.onNode(hasText("Next") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your way out, any time").assertIsDisplayed()
        rule.onNode(hasText("Skip tour") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your way out, any time").assertDoesNotExist()
        assertTrue("a skipped tour must not come back", Tour.done(context))
    }

    @Test fun longPressOnAShieldRowExplainsIt() {
        rule.showShell(tab = RulesTab)
        rule.onNode(hasText("Quiet hours") and hasClickAction()).performScrollTo()
            .performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Calls, messages, maps and alarms always work.", substring = true).assertIsDisplayed()
        rule.onNode(hasText("Change it") and hasClickAction()).assertIsDisplayed()
        rule.onNode(hasText("Read aloud") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun progressExplainsWhatItDoesNotMeasure() {
        rule.showShell(tab = 2)
        rule.onNode(hasContentDescription("How to read this page") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("What isn’t measured").performScrollTo().assertIsDisplayed()
    }
}
