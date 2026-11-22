package app.hisn.android.nativeshell

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.android.BlockActivity
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The general edition's block screen: says what closed, what still works, and the way out. */
@RunWith(AndroidJUnit4::class)
class BlockScreenTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Before fun generalEditionOnly() = assumeFalse(app.hisn.android.Edition.islamic)

    private fun open(vararg extras: Pair<String, Any>): ActivityScenario<BlockActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, BlockActivity::class.java)
        extras.forEach { (key, value) ->
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
            }
        }
        return ActivityScenario.launch(intent)
    }

    /** Leaves the screen up for a screenshot when the runner is given -e hold <millis>. */
    private fun hold() {
        val millis = InstrumentationRegistry.getArguments().getString("hold")?.toLongOrNull() ?: return
        Thread.sleep(millis)
    }

    @Test fun aClosedShortsFeedNamesTheFeedAndKeepsTheRestOfTheApp() {
        open("shortvideo" to true, "app" to "com.google.android.youtube").use {
            rule.onNodeWithText("Shorts are closed.").assertIsDisplayed()
            rule.onNode(hasText("Back to my day") and hasClickAction()).assertIsDisplayed()
            rule.onNode(hasText("Breathe first") and hasClickAction()).assertIsDisplayed()
            hold()
        }
    }

    @Test fun aUsedUpLimitSaysItOpensTomorrow() {
        open("limit" to true).use {
            rule.onNodeWithText("That’s today’s time for this app.").assertIsDisplayed()
            rule.onNodeWithText("It opens again tomorrow. Today’s time is used up, as you planned.").assertIsDisplayed()
            hold()
        }
    }

    @Test fun quietHoursSayCallsStillWork() {
        open("schedule" to true).use {
            rule.onNodeWithText("Quiet hours.").assertIsDisplayed()
            hold()
        }
    }

    @Test fun aClosedPageIsHonestAboutMistakes() {
        open("visual" to true).use {
            rule.onNodeWithText("This page was closed.").assertIsDisplayed()
            hold()
        }
    }
}
