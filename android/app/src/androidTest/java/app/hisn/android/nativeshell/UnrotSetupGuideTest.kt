package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisn.android.DnsVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unrot's setup checklist: one count, one list, a "Show me" sheet, and gone once done. */
@RunWith(AndroidJUnit4::class)
class UnrotSetupGuideTest {
    @get:Rule val rule = createComposeRule()

    @Before fun unrotOnly() = assumeTrue(unrotEdition)

    @Test fun freshInstallShowsWhatTheAppDoesAndEveryStepLeft() {
        rule.showShell()
        rule.onNodeWithText("What Unrot does".uppercase()).assertIsDisplayed()
        rule.onNodeWithText("Finish setup").assertIsDisplayed()
        rule.onNodeWithText("0 of 4 done").assertIsDisplayed()
        // The header chip counts the same steps instead of a bare "Needs you".
        rule.onNodeWithText("4 steps left").assertIsDisplayed()
        rule.onNodeWithText("Needs you").assertDoesNotExist()
        rule.onNodeWithText("Stop Samsung from pausing Unrot").assertIsDisplayed()
    }

    @Test fun showMeExplainsTheStepAndOpensTheRightSetting() {
        val actions = FakeShellActions().apply {
            liveProtection = ProtectionRuntime(dns = DnsVpnService.State.RUNNING, dnsRequested = true)
        }
        rule.showShell(actions)
        rule.onNodeWithText("1 of 4 done").assertIsDisplayed()
        rule.onNode(hasText("Show me") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("STEP 2 OF 4").assertIsDisplayed()
        rule.onNodeWithText("Settings › Accessibility › Downloaded apps").assertExists()
        rule.onNode(hasText("Open settings") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("openPermission:accessibility"), actions.calls)
    }

    @Test fun theWebsiteStepTurnsTheFilterOnFromTheSheet() {
        val actions = FakeShellActions()
        rule.showShell(actions)
        rule.onNode(hasText("Show me") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Connection request").assertExists()
        rule.onNode(hasText("Turn on") and hasClickAction()).performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(actions.calls.toString(), "requestDns" in actions.calls)
    }

    @Test fun finishedSetupLeavesNoChecklistAndSaysWhatIsRunning() {
        val actions = FakeShellActions().apply {
            liveProtection = ProtectionRuntime(dns = DnsVpnService.State.RUNNING, dnsRequested = true)
            finishUnrotSetup()
        }
        rule.showShell(actions)
        rule.onNodeWithText("Finish setup").assertDoesNotExist()
        rule.onNodeWithText("4 steps left").assertDoesNotExist()
    }

    @Test fun shieldShowsTheSameChecklistInsteadOfAnotherWarning() {
        rule.showShell(tab = RulesTab)
        rule.onNodeWithText("Finish setup").assertIsDisplayed()
        rule.onNodeWithText("Protection needs attention").assertDoesNotExist()
    }
}
