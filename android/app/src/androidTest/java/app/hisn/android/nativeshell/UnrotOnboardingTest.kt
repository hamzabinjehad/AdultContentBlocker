package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisn.core.SetupPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakeUnrotSetup(var access: OnboardingProtectionState = OnboardingProtectionState.OFF) : UnrotSetupActions {
    var plan: SetupPlan? = null
    var note: String? = null
    var finished = false
    val requested = mutableListOf<String>()
    var accept = true

    override fun granted(key: String) = false
    override fun protectionState(key: String) =
        if (key == "accessibility") access else OnboardingProtectionState.OFF
    override fun request(key: String) {
        requested += key
    }
    override fun guardOn() = false
    override fun savePersonal(name: String, preset: String, reason: String) {}
    override fun completeSetup() {
        finished = true
    }
    override fun applyPlan(plan: SetupPlan, note: String): Boolean {
        this.plan = plan
        this.note = note
        return accept
    }
}

/** The general edition's setup: every step reachable, the plan saved once, the ending honest. */
@RunWith(AndroidJUnit4::class)
class UnrotOnboardingTest {
    @get:Rule val rule = createComposeRule()

    private fun show(actions: FakeUnrotSetup, reason: String = "") {
        rule.setContent {
            HisnTheme(darkTheme = false) {
                UnrotOnboarding(actions = actions, refresh = 0, initialReason = reason)
            }
        }
    }

    private val appBox = androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Checkbox
    ) and androidx.compose.ui.test.isOn()

    /** Unticks the suggested apps (never the adult switch), so the flow skips the per-app steps. */
    private fun untickApps() {
        rule.waitForIdle()
        while (rule.onAllNodes(appBox).fetchSemanticsNodes().isNotEmpty()) {
            rule.onAllNodes(appBox)[0].let { box -> runCatching { box.performScrollTo() }; box.performClick() }
            rule.waitForIdle()
        }
    }

    private fun tap(text: String) {
        val node = rule.onNode(hasText(text) and hasClickAction())
        // The top bar and the bottom button sit outside the scrolling step; only scroll inside it.
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    @Test fun skipLeavesSetupWithoutSavingAnything() {
        val actions = FakeUnrotSetup()
        show(actions)
        tap("Skip for now")
        assertTrue(actions.finished)
        assertEquals(null, actions.plan)
    }

    /** Through the demos and the questions about you, answering the way the test says. */
    private fun throughAboutYou(name: String = "") {
        tap("Set it up · 4 min")
        rule.onNodeWithText("You open TikTok. The feed closes.").assertIsDisplayed()
        tap("Next")
        tap("Next")
        tap("Set mine up")
        rule.onNodeWithText("What should we call you?").assertIsDisplayed()
        if (name.isNotEmpty()) {
            rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput(name)
            tap("Next")
        } else {
            tap("Skip")
        }
        // Age is optional: skipping it moves on.
        rule.onNodeWithText("How old are you?").assertIsDisplayed()
        tap("Skip")
        rule.onNodeWithText("What do you want back?").assertIsDisplayed()
        tap("Sleep")
        tap("1 picked · Next")
        // One tap answers and moves on.
        tap("Late at night, in bed")
        rule.onNodeWithText("How do you feel after a long scroll?").assertIsDisplayed()
        tap("Tired")
        tap("Next")
        // With or without usage access on this device.
        if (rule.onAllNodes(hasText("Use my guess") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) {
            tap("Use my guess")
        } else {
            tap("That’s me · Next")
        }
        // The problem, then why it's hard, before the answer.
        tap("What does it cost me?")
        rule.onNodeWithText("It doesn’t just take time", substring = true).assertIsDisplayed()
        tap("Why is it so hard?")
        // The Glasshouse chapter: the four tricks feeds use, then why it isn't willpower.
        rule.onNodeWithText("Feeds use", substring = true).assertIsDisplayed()
        tap("Next")
        rule.onNodeWithText("It’s not willpower", substring = true).assertIsDisplayed()
        tap("What does Unrot do?")
        // What helps, with the glass, before every part gets its answer.
        rule.onNodeWithText("How Unrot", substring = true).assertIsDisplayed()
        tap("Next")
        rule.onNodeWithText("Every part of it gets an answer.").assertIsDisplayed()
        tap("Let’s get them back")
        tap("Read")
        tap("Next")
        rule.onNodeWithText("Which apps take more than they give?").assertIsDisplayed()
    }

    @Test fun answersAreSavedOnThePhoneAndUsedOnThePauseScreens() {
        val actions = FakeUnrotSetup()
        show(actions)
        throughAboutYou()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("You said long feeds leave you tired.", UnrotAnswers.feelLine(context))
        assertEquals("Instead: read 10 pages", UnrotAnswers.insteadLine(context))
    }

    @Test fun noAppsGoesStraightToTheNoteAndSavesThePlanAtTheEnd() {
        val actions = FakeUnrotSetup()
        show(actions)
        throughAboutYou()
        untickApps()
        tap("Next")
        rule.onNode(hasText("You’ll be tired tomorrow.") and hasClickAction()).performClick()
        tap("Save my note")
        // The plan is drawn from the choices, then saved before the Android switches.
        rule.onNodeWithText("Make it live", substring = true).assertIsDisplayed()
        tap("Make it live")
        assertEquals("You’ll be tired tomorrow.", actions.note)
        assertTrue(actions.plan!!.apps.isEmpty())
        // "Sleep" and "late at night" set quiet hours to 23:00–07:00.
        assertEquals(23 * 60, actions.plan!!.quietStart)
        rule.onNodeWithText("Last step: switch it on.").assertIsDisplayed()
        rule.onAllNodes(hasText("Turn on") and hasClickAction())[0].performClick()
        assertEquals("accessibility", actions.requested.first())
    }

    private fun toTheEnd() {
        throughAboutYou()
        untickApps()
        tap("Next")
        tap("Skip for now")
        tap("Make it live")
        tap("Next")
        tap("Not now")
        tap("Skip, I’ll just start")
    }

    @Test fun theEndingSaysAlmostWhileAppProtectionIsOff() {
        val actions = FakeUnrotSetup()
        show(actions)
        toTheEnd()
        rule.onNodeWithText("Almost set.").assertIsDisplayed()
        tap("Go to Home")
        assertTrue(actions.finished)
    }

    @Test fun theEndingDatesTheFirstMilestoneOnceAppProtectionRuns() {
        val actions = FakeUnrotSetup(access = OnboardingProtectionState.ON)
        show(actions)
        toTheEnd()
        rule.onNodeWithText("Your first week starts now.").assertIsDisplayed()
        rule.onNodeWithText("First milestone:", substring = true).assertIsDisplayed()
    }
}
