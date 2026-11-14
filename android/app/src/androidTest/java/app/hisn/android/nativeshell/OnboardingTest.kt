package app.hisn.android.nativeshell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class FakeOnboardingActions(
    val grants: MutableSet<String> = mutableSetOf(),
    var guard: Boolean = false
) : OnboardingActions {
    val requested = mutableListOf<String>()
    var saved: Triple<String, String, String>? = null
    var finished: Boolean? = null

    override fun granted(key: String) = key in grants
    override fun request(key: String) { requested += key }
    override fun guardOn() = guard
    override fun savePersonal(name: String, preset: String, reason: String) {
        saved = Triple(name, preset, reason)
    }
    override fun finish(enableGuard: Boolean) { finished = enableGuard }
}

/** First run: steps, skipping, the personal page, live grants and the guard page. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val rule = createComposeRule()

    private val density = InstrumentationRegistry.getInstrumentation().targetContext
        .resources.displayMetrics.density

    private fun show(
        actions: FakeOnboardingActions = FakeOnboardingActions(),
        direct: Boolean = true,
        fontScale: Float = 1f,
        rtl: Boolean = false
    ): FakeOnboardingActions {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                HisnTheme(darkTheme = false) { Onboarding(actions, directBuild = direct) }
            }
        }
        rule.waitForIdle()
        return actions
    }

    private fun primary(label: String) =
        rule.onNode(hasText(label) and hasClickAction()).performClick().also { rule.waitForIdle() }

    /** Story pages are one big button; tapping anywhere goes on. */
    private fun tapStory() = primary("Tap to continue")

    private fun toYou() = repeat(8) { tapStory() }

    /** The five questions left unanswered, then every promise put off at once. */
    private fun toPermissions() {
        toYou()
        repeat(5) { primary("Continue") }
        primary("Not now")
    }

    /** Permissions, then the setup summary, then the guard (direct build). */
    private fun toGuard() {
        toPermissions()
        repeat(2) { primary("Continue") }
    }

    @Test fun storyThenSetupSteps() {
        show()
        rule.onNodeWithText("A shield for your phone").assertIsDisplayed()
        // The privacy promise is on the first page.
        rule.onNodeWithText("Your rules and activity stay on this phone. No account.").assertIsDisplayed()
        tapStory()
        rule.onNodeWithText("Break the autopilot").assertIsDisplayed()
        repeat(2) { tapStory() }
        rule.onNodeWithText("How it works").assertIsDisplayed()
        rule.onNodeWithText("Hisn steps in before it loads.").assertDoesNotExist()
        repeat(3) { tapStory() }
        rule.onNodeWithText("Four layers, one shield").assertIsDisplayed()
        rule.onNodeWithText("Closes the apps you choose.").assertIsDisplayed()
        tapStory()
        rule.onNodeWithText("A slip is not a reset").assertIsDisplayed()
        tapStory()
        rule.onNodeWithText("Step 1 of 11").assertIsDisplayed()
        rule.onNodeWithText("What should I call you?").assertIsDisplayed()
        rule.onNodeWithText("Your answers stay on this phone.").assertIsDisplayed()
    }

    @Test fun swipeAndBackArrowMoveBetweenPages() {
        show()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
        rule.onNodeWithTag(ONBOARDING_PAGER).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Break the autopilot").assertIsDisplayed()
        // The page left behind is not offered to a screen reader.
        rule.onNodeWithText("A shield for your phone").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("A shield for your phone").assertIsDisplayed()
    }

    @Test fun swipingOffThePersonalPageSavesIt() {
        val actions = show()
        toYou()
        rule.onNode(hasText("Your name (optional)") and SemanticsMatcher.keyIsDefined(
            SemanticsProperties.EditableText)).performTextInput("Sam")
        rule.onNodeWithTag(ONBOARDING_PAGER).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(Triple("Sam", "", ""), actions.saved)
        rule.onNodeWithText("What are you protecting?").assertIsDisplayed()
    }

    @Test fun whereItStartsOrdersTheGrants() {
        show()
        toYou()
        repeat(2) { primary("Continue") }
        rule.onNodeWithText("In the browser").performClick()
        rule.waitForIdle()
        repeat(2) { primary("Continue") }
        primary("Not now")
        rule.onNodeWithText("You said it usually starts in the browser, so Website filtering comes first.")
            .assertIsDisplayed()
        rule.onNodeWithText("0 of 2 on").assertIsDisplayed()
        val website = rule.onNode(hasText("Website filtering", substring = true) and isToggleable())
            .fetchSemanticsNode().boundsInRoot.top
        val apps = rule.onNode(hasText("App protection", substring = true) and isToggleable())
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Website filtering should come first", website < apps)
    }

    @Test fun thePromiseIsHeldOrPutOff() {
        show()
        toYou()
        repeat(5) { primary("Continue") }
        rule.onNodeWithText("Promise 1 of 3").assertIsDisplayed()
        rule.onNodeWithText("I will guard what I see.").assertIsDisplayed()
        // TalkBack has no long press to give; its double tap makes the same promise.
        rule.onNode(hasText("Hold to promise") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Promise 2 of 3").assertIsDisplayed()
        rule.onNodeWithText("If I slip, I will start again, not give up.").assertIsDisplayed()
        // The next promise starts empty, not already "Promised".
        rule.onNode(hasText("Hold to promise") and hasClickAction()).assertIsDisplayed()
        // "Not now" puts off the rest at once.
        primary("Not now")
        rule.onNodeWithText("Needed for blocking").assertIsDisplayed()
    }

    @Test fun playBuildHasNoGuardStep() {
        val actions = show(direct = false)
        toPermissions()
        rule.onNodeWithText("Step 9 of 10").assertIsDisplayed()
        primary("Continue")
        rule.onNodeWithText("Step 10 of 10").assertIsDisplayed()
        rule.onNodeWithText("Here is your setup").assertIsDisplayed()
        primary("Open Hisn")
        assertEquals(false, actions.finished)
    }

    @Test fun skipLeavesFromAnyPage() {
        val actions = show()
        tapStory()
        primary("Skip for now")
        assertEquals(false, actions.finished)
    }

    @Test fun personalWordsAreSavedAndUsed() {
        val actions = show()
        toYou()
        rule.onNode(hasText("Your name (optional)") and SemanticsMatcher.keyIsDefined(
            SemanticsProperties.EditableText)).performTextInput("Sam")
        primary("Continue")
        // Picking an answer is the answer: the next question comes without a Continue.
        rule.onNodeWithText("For my family").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Where does it usually start?").assertIsDisplayed()
        assertEquals(Triple("Sam", "family", ""), actions.saved)
        rule.onNodeWithText("I’m not sure").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Late at night").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("I deleted the blocker").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("“For my family”").assertIsDisplayed()
        primary("Not now")
        rule.onNodeWithText("I can help with that, Sam.").assertIsDisplayed()
        primary("Continue")
        // The summary repeats the person's words and points at the rule for the hour they named.
        rule.onNodeWithText("Here is your setup, Sam").assertIsDisplayed()
        rule.onNodeWithText("“For my family”").assertIsDisplayed()
        rule.onNodeWithText("Late nights are hardest for you. Rules › Schedule closes the apps you choose from 22:00 to 06:00.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You said you deleted a blocker before. The uninstall guard on the next page closes Hisn’s removal screens.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun playBuildNeverOffersTheGuardItLacks() {
        show(direct = false)
        toYou()
        repeat(4) { primary("Continue") }
        rule.onNodeWithText("I deleted the blocker").performClick()
        rule.waitForIdle()
        primary("Not now")
        primary("Continue")
        rule.onNodeWithText("Here is your setup").assertIsDisplayed()
        rule.onNodeWithText("You said you deleted a blocker before. The uninstall guard on the next page closes Hisn’s removal screens.")
            .assertDoesNotExist()
    }

    @Test fun summaryShowsWhatAndroidReportsRunning() {
        show(FakeOnboardingActions(grants = mutableSetOf("dns")))
        toPermissions()
        primary("Continue")
        rule.onNodeWithText("Running now").assertIsDisplayed()
        rule.onNodeWithText("Nothing is running yet. You can turn it on from the Protection tab at any time.")
            .assertDoesNotExist()
    }

    @Test fun grantsAreSwitchesThatReadAndroid() {
        val actions = show(FakeOnboardingActions(grants = mutableSetOf("dns")))
        toPermissions()
        rule.onNodeWithText("I can help with that.").assertIsDisplayed()
        rule.onNodeWithText("Needed for blocking").assertIsDisplayed()
        rule.onNodeWithText("Optional, for limits and reliability").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Website filtering", substring = true) and isToggleable()).assertIsOn()
        val access = rule.onNode(hasText("App protection", substring = true) and isToggleable())
        access.assertIsOff()
        access.performClick()
        rule.waitForIdle()
        assertEquals(listOf("accessibility"), actions.requested)
        // Flipping asks Android; the switch does not pretend the grant happened.
        access.assertIsOff()
    }

    @Test fun guardStepTurnsTheGuardOnOrFinishesWithout() {
        val actions = show()
        toGuard()
        rule.onNodeWithText("Keep Hisn in place").assertIsDisplayed()
        rule.onNodeWithText("Requires App protection above.").assertIsDisplayed()
        primary("Turn on and finish")
        assertEquals(true, actions.finished)
    }

    @Test fun guardAlreadyOnJustFinishes() {
        val actions = show(FakeOnboardingActions(guard = true))
        toGuard()
        primary("Finish")
        assertEquals(false, actions.finished)
    }

    @Test fun everyPageIsLabelledTappableAndFits() = assertFits(1f, false)

    @Test fun pagesFitAtDoubleText() = assertFits(2f, false)

    @Test fun pagesFitRightToLeftAtLargeText() = assertFits(1.3f, true)

    private fun assertFits(scale: Float, rtl: Boolean) {
        val problems = checkConfiguration(scale, rtl)
        assertTrue("Onboarding at ${scale}x${if (rtl) " RTL" else ""}:\n" + problems.joinToString("\n"),
            problems.isEmpty())
    }

    /** Walks every page; reports unlabelled controls, controls under 48 dp and text off screen. */
    private fun checkConfiguration(scale: Float, rtl: Boolean): List<String> {
        show(fontScale = scale, rtl = rtl)
        val problems = mutableListOf<String>()
        val buttons = List(8) { "Tap to continue" } +
            listOf("Continue", "Continue", "Continue", "Continue", "Continue", "Not now", "Continue", "Continue")
        for (index in 0..buttons.size) {
            val width = rule.onNode(isRoot()).fetchSemanticsNode().boundsInRoot.width
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                if (node.spokenLabel().isEmpty()) problems += "page ${index + 1}: unlabelled control"
                val w = maxOf(node.touchBoundsInRoot.width, node.size.width.toFloat()) / density
                val h = maxOf(node.touchBoundsInRoot.height, node.size.height.toFloat()) / density
                if (w + 0.5f < 48f || h + 0.5f < 48f) {
                    problems += "page ${index + 1}: '${node.spokenLabel().take(30)}' is ${w.toInt()}x${h.toInt()} dp"
                }
            }
            rule.onAllNodes(SemanticsMatcher("has text") {
                it.config.getOrNull(SemanticsProperties.Text) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { !it.insideHorizontalScroll() }
                .filter { it.boundsInRoot.left < -1f || it.boundsInRoot.right > width + 1f }
                .forEach { problems += "page ${index + 1}: '${it.spokenLabel().take(30)}' runs off screen" }
            if (index < buttons.size) {
                rule.onNode(hasText(buttons[index]) and hasClickAction()).performClick()
                rule.waitForIdle()
            }
        }
        return problems
    }
}
