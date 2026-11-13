package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisn.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's tabs, rows and back navigation, and that the home screen tells the truth. */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    @get:Rule val rule = createComposeRule()

    private fun tab(label: String) = rule.onNode(hasText(label) and isSelectable())

    @Test fun tabsSwitchAndShowTheirContent() {
        rule.showShell()
        tab("Protection").assertIsDisplayed()
        tab("Rules").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Blocked domains", substring = true) and hasClickAction())
            .performScrollTo().assertIsDisplayed()
        tab("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").performScrollTo().assertIsDisplayed()
        tab("Protection").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").assertDoesNotExist()
    }

    @Test fun homeNeverClaimsProtectedWhileNothingRuns() {
        // In the test process the tunnel is not running and the guard is not connected.
        rule.showShell()
        rule.onNodeWithText("Protected").assertDoesNotExist()
    }

    @Test fun uninstallRowOpensTheRightThingForTheBuild() {
        val actions = rule.showShell(tab = 2)
        rule.onNodeWithText("Uninstall protection").performScrollTo().performClick()
        rule.waitForIdle()
        if (BuildConfig.SELF_PROTECTION) {
            assertEquals(listOf("openUninstall"), actions.calls)
            // The row shows the live state, not a fixed line.
            rule.onNodeWithText("Off. Android can uninstall Hisn.").assertExists()
        } else {
            rule.onNode(hasContentDescription("Back")).assertIsDisplayed()
        }
    }

    @Test fun detailScreenReturnsWithBack() {
        rule.showShell(tab = 2)
        rule.onNodeWithText("Permissions").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Back")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionRowsHandOffToAndroid() {
        val actions = rule.showShell(tab = 2)
        rule.onNodeWithText("Permissions").performScrollTo().performClick()
        rule.waitForIdle()
        // Every permission reads "off" from the fake; tapping App protection must ask Android.
        // Exactly one match also proves the covered Settings row is hidden from accessibility.
        rule.onNode(hasText("App protection", substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertTrue("no hand-off in ${actions.calls}",
            actions.calls.any { it.startsWith("openPermission") || it == "openAccessibility" })
    }
}
