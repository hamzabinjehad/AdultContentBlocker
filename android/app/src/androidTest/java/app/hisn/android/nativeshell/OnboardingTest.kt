package app.hisn.android.nativeshell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.core.ScanScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakeOnboardingActions(
    val protection: MutableMap<String, OnboardingProtectionState> = mutableMapOf(),
    var guard: Boolean = false
) : OnboardingActions {
    val requested = mutableListOf<String>()
    var saved: Triple<String, String, String>? = null
    var finished = false
    var guardRequests = 0
    val checks = mutableMapOf<String, Boolean>()
    var apps: Set<String> = emptySet()
    var onChanged: () -> Unit = {}

    override fun granted(key: String) = protection[key] == OnboardingProtectionState.ON
    override fun protectionState(key: String) = protection[key] ?: OnboardingProtectionState.OFF
    override fun request(key: String) {
        requested += key
    }

    fun report(key: String, state: OnboardingProtectionState) {
        protection[key] = state
        onChanged()
    }

    override fun guardOn() = guard
    override fun setGuard() {
        guardRequests++
        guard = true
        onChanged()
    }

    override fun savePersonal(name: String, preset: String, reason: String) {
        saved = Triple(name, preset, reason)
    }

    override fun completeSetup() {
        finished = true
    }

    override fun checkOn(key: String) = checks[key] == true
    override fun setCheck(key: String, on: Boolean) {
        requested += "check:$key:$on"
        checks[key] = on
        onChanged()
    }

    override fun checkedApps() = apps
    override fun setCheckedApps(packages: Set<String>) {
        apps = packages
        onChanged()
    }
}

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val rule = createComposeRule()

    /** The story-based setup is the Islamic edition's; the general edition has UnrotOnboardingTest. */
    @org.junit.Before fun islamicEditionOnly() {
        org.junit.Assume.assumeTrue(app.hisn.android.Edition.islamic)
    }

    private val density = InstrumentationRegistry.getInstrumentation().targetContext
        .resources.displayMetrics.density

    private fun show(
        actions: FakeOnboardingActions = FakeOnboardingActions(),
        direct: Boolean = true,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        initialName: String = "",
        initialPreset: String = ""
    ): FakeOnboardingActions {
        val refresh = mutableIntStateOf(0)
        actions.onChanged = { refresh.intValue++ }
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                HisnTheme(darkTheme = false) {
                    Onboarding(
                        actions,
                        directBuild = direct,
                        initialName = initialName,
                        initialPreset = initialPreset,
                        refresh = refresh.intValue
                    )
                }
            }
        }
        rule.waitForIdle()
        return actions
    }

    private fun action(label: String) = rule.onNode(hasText(label) and hasClickAction())

    private fun tap(label: String) {
        action(label).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        rule.waitForIdle()
    }

    private fun back() {
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
    }

    @Test fun firstActionOpensProtectionWithoutPersonalQuestions() {
        val actions = show(initialName = "Sam", initialPreset = "family")
        rule.onNodeWithText("Take your mind back").assertIsDisplayed()
        action("Skip the questions").assertIsDisplayed()
        action("Get started").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()

        tap("Skip the questions")
        rule.onNodeWithText("Turn on protection").assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        rule.onNodeWithText("What should I call you?").assertDoesNotExist()
        tap("Enable website filtering")

        rule.runOnIdle {
            assertEquals(listOf("dns"), actions.requested)
            assertNull(actions.saved)
        }
        action("Enable website filtering").assertExists()
    }

    @Test fun appPausePreviewUsesTheCountdownWithoutChangingSetup() {
        val actions = show()
        rule.mainClock.autoAdvance = false
        action("Preview an app pause").performScrollTo().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Pause preview").assertIsDisplayed()
        rule.onNodeWithText("Open in 5 seconds").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(5_150)
        action("Open Example app").performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithText("Take your mind back").assertIsDisplayed()
        rule.runOnIdle {
            assertFalse(actions.finished)
            assertTrue(actions.requested.isEmpty())
            assertNull(actions.saved)
        }
    }

    @Test fun optionalPermissionsExpandOnlyOnRequest() {
        val actions = show()
        tap("Skip the questions")
        rule.onNodeWithText("Daily time limits").assertDoesNotExist()
        action("Optional permissions").performScrollTo().performClick()
        rule.onNodeWithText("Daily time limits").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertTrue(actions.requested.isEmpty()) }
    }

    @Test fun reviewReflectsLiveProtectionAndCanRetryWebsiteFiltering() {
        val actions = show(direct = false)
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")
        rule.onNodeWithText("Review your setup").assertIsDisplayed()
        rule.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        rule.onAllNodes(hasText("Off")).onFirst().assertExists()
        rule.onNodeWithText("Active").assertDoesNotExist()

        action("Enable website filtering").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(listOf("dns"), actions.requested)
            actions.report("dns", OnboardingProtectionState.STARTING)
        }
        rule.onNodeWithText("Starting…").assertExists()
        rule.onNodeWithText("Active").assertDoesNotExist()

        rule.runOnIdle { actions.report("dns", OnboardingProtectionState.NEEDS_ATTENTION) }
        rule.onNodeWithText("Needs attention").assertExists()
        rule.onNodeWithText("Starting…").assertDoesNotExist()

        rule.runOnIdle { actions.report("dns", OnboardingProtectionState.ON) }
        rule.onNodeWithText("Active").assertExists()
        rule.onNodeWithText("Needs attention").assertDoesNotExist()
        tap("Open Hisn")
        rule.runOnIdle { assertTrue(actions.finished) }
    }

    @Test fun reviewShowsExistingAllAppsScopeWithoutCountingItAsAnApp() {
        val actions = FakeOnboardingActions(
            mutableMapOf("accessibility" to OnboardingProtectionState.ON)
        ).apply {
            checks["tags"] = true
            apps = setOf(ScanScope.ALL_APPS)
        }
        show(actions, direct = false)
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")

        rule.onNode(hasText("Adult words and tags") and hasText("On in all supported apps"))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("On · apps: 1").assertDoesNotExist()
    }

    /** Taps a control that may still be appearing, such as a chat reply. */
    private fun tapWhenShown(label: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tap(label)
    }

    private fun shown(label: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
    }

    /** Starts on the next question after the FAQ and checks the no-answer path. */
    private fun skipFirstMove() {
        shown("What will you do when it gets hard?")
        tap("Skip this question")
        shown("Rehearse your first move")
        shown("Something to try")
        tap("Continue")
        shown("Start again after a setback")
        tap("Continue")
    }

    /** From the five "How Hisn helps" pages, through Paths, Plan and Reminders, to the promise. */
    private fun showsToPledge() {
        shown("Adult sites stop at the door")
        repeat(5) { tap("Continue") }
        shown("Why Hisn and not just a filter")
        tap("Continue")
        shown("Built to hold in a weak moment")
        tap("Continue")
        shown("Two ways the next weeks can go")
        tap("Continue")
        shown("Questions people ask")
        tap("Continue")
        skipFirstMove()
        tap("Continue")
        tap("Continue")
        shown("Know when protection stops")
        tap("Not now")
        shown("Make a promise to yourself")
    }

    /** Skips every question from the welcome to the promise. */
    private fun skipToPledge() {
        tap("Get started")
        tap("Continue")
        tap("Skip the chat")
        repeat(3) { tap("Skip this question") }
        tap("Continue")
        repeat(7) { tap("Continue") }  // Why it’s hard, both brain pages included
        repeat(4) { tap("Skip this question") }
        showsToPledge()
    }

    @Test fun personalizationOrdersProtectionAndSavesAnswers() {
        val actions = show()
        tap("Get started")
        rule.onNodeWithText("What should I call you?").assertIsDisplayed()
        rule.onNode(hasText("Your name (optional)") and SemanticsMatcher.keyIsDefined(
            SemanticsProperties.EditableText
        )).performTextInput("Sam")
        tap("Continue")

        shown("Hi Sam.")
        tapWhenShown("Sure")
        tapWhenShown("That’s me")
        tapWhenShown("Show me how")

        shown("What do you want less of?")
        tap("Adult content")
        tap("Continue")
        tap("2 to 4 hours")
        shown("Some quick maths, Sam")
        rule.onNode(hasContentDescription("90")).assertExists()
        rule.onNode(hasContentDescription("46")).assertExists()
        tap("Continue")
        shown("19 dots are phone time.")
        tap("Continue")
        action("Guilty").performClick()
        tap("Continue")
        shown("Sam, it’s not a fair fight")
        tap("Continue")
        shown("Your brain learns what you repeat")
        tap("Continue")
        // Adult content was picked, so both brain pages are shown, feeds first.
        shown("What endless feeds do in your brain")
        tap("Continue")
        shown("What adult content does in your brain")
        tap("Continue")
        shown("…and the bad feeling starts it again")
        repeat(4) { tap("Continue") }
        tap("No, never")
        action("Late at night").performClick()
        tap("Continue")
        shown("What are you protecting?")
        action("For my family").performScrollTo().performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(Triple("Sam", "family", ""), actions.saved) }
        shown("What do you want more of?")
        action("Better sleep").performClick()
        tap("Continue")

        shown("Adult sites stop at the door")
        repeat(5) { tap("Continue") }
        tap("Continue")
        tap("Continue")
        shown("You keep feeling guilty afterwards")
        tap("With Hisn")
        shown("Adult sites and apps stay closed")
        tap("Continue")
        tap("Will anyone know I use Hisn?")
        shown("Only someone who looks at your phone. Android shows a key icon and a small notification while DNS filtering runs. Hisn tells no one.")
        tap("Continue")
        shown("What will you do when it gets hard?")
        tap("Take a breath")
        shown("Rehearse your first move")
        shown("Preview an app pause")
        back()
        tap("Put my phone down")
        shown("Set the phone somewhere out of reach and stand up. Give yourself a minute away from the screen.")
        action("Preview an app pause").assertDoesNotExist()
        tap("Continue")
        shown("Start again after a setback")
        shown("Check what is active")
        tap("Continue")
        shown("Adding tips for your hardest moments")
        tap("Continue")
        shown("Your plan, Sam")
        shown("You’re working towards")
        shown("Your first move")
        shown("Put my phone down")
        rule.onNodeWithText("Uninstall guard").assertDoesNotExist()
        shown("In Rules, add a schedule that blocks distracting apps at the hours you named.")
        tap("Continue")
        tap("Not now")
        shown("Make a promise to yourself, Sam")
        tap("Not now")
        shown("Sam, day 1 starts today")
        tap("Start setup")

        rule.onNodeWithText("Turn on protection").assertIsDisplayed()
        rule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        val websites = action("Enable website filtering").fetchSemanticsNode().positionInRoot.y
        val apps = action("Open Accessibility settings").fetchSemanticsNode().positionInRoot.y
        assertTrue("Website filtering should come first", websites < apps)
        rule.runOnIdle { assertTrue(actions.requested.isEmpty()) }

        back()
        shown("Sam, day 1 starts today")
        back()
        shown("Make a promise to yourself, Sam")
        back()
        shown("Know when protection stops")
    }

    @Test fun personalizationStartsFromTheExistingProfile() {
        val actions = show(initialName = "Sam", initialPreset = "family")
        tap("Get started")
        rule.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString("Sam")
        )).assertIsDisplayed()
        tap("Continue")
        tap("Skip the chat")
        tap("Short videos that never end")
        tap("Continue")
        tap("Skip this question")
        // Without an hours answer the maths pages are left out.
        shown("How do you feel after losing time like that?")
        tap("Skip this question")
        tap("Continue")
        // Only short videos were picked: the adult-content brain page is left out.
        repeat(6) { tap("Continue") }  // Why it’s hard
        tap("Yes, more than once")
        tap("Skip this question")
        // The saved reason is already chosen, so the question offers Continue rather than Skip.
        tap("Continue")
        rule.runOnIdle { assertEquals(Triple("Sam", "family", ""), actions.saved) }
        tap("Skip this question")
        // Short videos were picked, so the pages that answer them come first.
        shown("Shorts and Reels, closed")
        repeat(5) { tap("Continue") }
        tap("Continue")
        // The direct build lists the uninstall guard among the things that hold.
        shown("Stops Hisn from being removed while you are protected.")
        tap("Continue")
        shown("Hours keep disappearing into feeds")
        tap("Continue")
        tap("Continue")
        skipFirstMove()
        tap("Continue")

        shown("Closes Shorts, Reels and Spotlight while the rest of each app keeps working.")
        shown("Uninstall guard")
        shown("“For my family”")
        tap("Continue")
        tap("Not now")
        tap("Not now")
        tap("Start setup")

        val websites = action("Enable website filtering").fetchSemanticsNode().positionInRoot.y
        val apps = action("Open Accessibility settings").fetchSemanticsNode().positionInRoot.y
        assertTrue("App protection should come first", apps < websites)
    }

    @Test fun everyQuestionCanBeSkipped() {
        val actions = show()
        skipToPledge()
        rule.runOnIdle {
            assertEquals(Triple("", "", ""), actions.saved)
            assertTrue(actions.requested.isEmpty())
        }
        rule.onNodeWithText("Some quick maths").assertDoesNotExist()
    }

    @Test fun remindersAskForNotificationsOnlyWhenTapped() {
        val actions = show()
        tap("Get started")
        tap("Continue")
        tap("Skip the chat")
        repeat(3) { tap("Skip this question") }
        tap("Continue")
        repeat(7) { tap("Continue") }  // Why it’s hard, both brain pages included
        repeat(4) { tap("Skip this question") }
        shown("Adult sites stop at the door")
        repeat(9) { tap("Continue") }
        skipFirstMove()
        repeat(2) { tap("Continue") }
        rule.runOnIdle { assertTrue(actions.requested.isEmpty()) }
        tap("Allow notifications")
        rule.runOnIdle { assertEquals(listOf("notifications"), actions.requested) }
        shown("Make a promise to yourself")
    }

    @Test fun thePromiseIsMadeOnceAndMovesOn() {
        val actions = show(direct = false)
        skipToPledge()
        shown("Promise 1 of 3")
        tap("Hold to promise")
        shown("Promise 2 of 3")
        tap("Hold to promise")
        shown("When it gets hard, I pause instead of giving in.")
        tap("Hold to promise")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Day 1 starts today")).fetchSemanticsNodes().isNotEmpty() }
        tap("Start setup")
        rule.onNodeWithText("Step 1 of 3").assertIsDisplayed()

        back()
        back()
        rule.onNodeWithText("Promised").assertIsDisplayed()
        action("Hold to promise").assertDoesNotExist()
        tap("Continue")
        shown("Day 1 starts today")
        rule.runOnIdle { assertTrue(actions.requested.isEmpty()) }
    }

    @Test fun backingOutOfPersonalizationKeepsTheExistingReason() {
        val actions = show(initialName = "Sam", initialPreset = "family")
        tap("Get started")
        tap("Continue")
        tap("Skip the chat")
        tap("Adult content")
        tap("Continue")
        tap("Skip this question")
        tap("Skip this question")
        tap("Continue")
        repeat(7) { tap("Continue") }  // Why it’s hard, both brain pages included
        tap("No, never")
        tap("Skip this question")
        shown("What are you protecting?")
        rule.runOnIdle { assertNull(actions.saved) }

        action("In my own words").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("What are you protecting?").assertIsDisplayed()
        back()
        shown("When is it hardest for you?")
        // 15 pages back to the welcome, the two brain pages included.
        repeat(15) { back() }
        rule.onNodeWithText("Take your mind back").assertIsDisplayed()
        tap("Skip for now")

        rule.runOnIdle {
            assertNull(actions.saved)
            assertTrue(actions.finished)
        }
    }

    @Test fun reviewCelebratesWhenBothLayersAreOn() {
        show(
            FakeOnboardingActions(mutableMapOf(
                "accessibility" to OnboardingProtectionState.ON,
                "dns" to OnboardingProtectionState.ON
            )),
            direct = false
        )
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")
        rule.onNodeWithText("You’re protected").assertIsDisplayed()
        rule.onNodeWithText("Review your setup").assertDoesNotExist()
    }

    @Test fun backFromDefaultSetupReturnsToTheWelcome() {
        show()
        tap("Skip the questions")
        back()
        rule.onNodeWithText("Take your mind back").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test fun skippingSetupFinishesWithoutRequestingPermissionsOrGuard() {
        val actions = show()
        tap("Skip the questions")
        tap("Skip for now")
        rule.runOnIdle {
            assertTrue(actions.finished)
            assertEquals(0, actions.guardRequests)
            assertTrue(actions.requested.isEmpty())
        }
    }

    @Test fun checksRequestChangesAndSaveApps() {
        val actions = show()
        tap("Skip the questions")
        tap("Continue")
        rule.onNodeWithText("Choose what Hisn checks").assertIsDisplayed()
        val adultWords = rule.onNode(hasText("Adult words and tags") and isToggleable())
        adultWords.performScrollTo().performClick()
        adultWords.assertIsOn()
        rule.runOnIdle { assertEquals(listOf("check:tags:true"), actions.requested) }

        val appRows = rule.onAllNodes(isToggleable() and SemanticsMatcher.expectValue(
            SemanticsProperties.Role, Role.Checkbox
        ))
        rule.waitUntil(5_000) { appRows.fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Chosen: 0").performScrollTo().assertIsDisplayed()
        appRows.onFirst().performScrollTo().performClick()
        rule.onNodeWithText("Chosen: 1").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, actions.apps.size) }
    }

    @Test fun directGuardRequiresAppProtectionAndAnExplicitAction() {
        val actions = show()
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")
        rule.onNodeWithText("Keep Hisn in place").assertIsDisplayed()
        action("Turn on uninstall guard").assertDoesNotExist()
        action("Open Accessibility settings").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(listOf("accessibility"), actions.requested)
            assertEquals(0, actions.guardRequests)
            actions.report("accessibility", OnboardingProtectionState.ON)
        }

        action("Turn on uninstall guard").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(1, actions.guardRequests)
            assertTrue(actions.guard)
            assertFalse(actions.finished)
        }
        tap("Continue")
        rule.onNodeWithText("Review your setup").assertIsDisplayed()
        tap("Open Hisn")
        rule.runOnIdle { assertTrue(actions.finished) }
    }

    @Test fun continuingPastTheGuardLeavesItOff() {
        val actions = show(
            FakeOnboardingActions(mutableMapOf("accessibility" to OnboardingProtectionState.ON))
        )
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")
        action("Turn on uninstall guard").assertExists()
        tap("Continue")
        rule.onNodeWithText("Review your setup").assertIsDisplayed()
        rule.runOnIdle {
            assertFalse(actions.guard)
            assertEquals(0, actions.guardRequests)
        }
    }

    @Test fun playSetupOmitsTheGuardEvenWithAppProtectionActive() {
        val actions = show(
            FakeOnboardingActions(mutableMapOf("accessibility" to OnboardingProtectionState.ON)),
            direct = false
        )
        tap("Skip the questions")
        tap("Continue")
        tap("Continue")
        rule.onNodeWithText("Review your setup").assertIsDisplayed()
        rule.onNodeWithText("Keep Hisn in place").assertDoesNotExist()
        rule.onNodeWithText("Uninstall guard").assertDoesNotExist()
        tap("Open Hisn")
        rule.runOnIdle {
            assertTrue(actions.finished)
            assertEquals(0, actions.guardRequests)
        }
    }

    @Test fun setupFitsAtDoubleTextSize() = assertSetupFits(fontScale = 2f, rtl = false)

    @Test fun setupFitsRightToLeftAtLargeText() = assertSetupFits(fontScale = 1.3f, rtl = true)

    private fun assertSetupFits(fontScale: Float, rtl: Boolean) {
        show(fontScale = fontScale, rtl = rtl)
        val problems = mutableListOf<String>()
        val steps = listOf(
            "Welcome" to "Get started",
            "Name" to "Continue",
            "Chat" to "Skip the chat",
            "Focus" to "Skip this question",
            "Hours" to "2 to 4 hours",
            "Math" to "Continue",
            "Year" to "Continue",
            "Feelings" to "Skip this question",
            "Truth" to "Continue",
            "Habit" to "Continue",
            "Brain: feeds" to "Continue",
            "Brain: adult content" to "Continue",
            "Loop" to "Continue",
            "Feeds" to "Continue",
            "Willpower" to "Continue",
            "Change" to "Continue",
            "Moment" to "Skip this question",
            "Triggers" to "Skip this question",
            "Reason" to "Skip this question",
            "Goals" to "Skip this question",
            "Websites" to "Continue",
            "Pause" to "Continue",
            "Shorts" to "Continue",
            "Pictures" to "Continue",
            "Private" to "Continue",
            "Compare" to "Continue",
            "Hold" to "Continue",
            "Paths" to "Continue",
            "Questions" to "Continue",
            "First move" to "Skip this question",
            "Rehearse" to "Continue",
            "Restart" to "Continue",
            "Building" to "Continue",
            "Plan" to "Continue",
            "Reminders" to "Not now",
            "Pledge" to "Not now",
            "Day one" to "Start setup",
            "Protection" to "Continue",
            "Checks" to "Continue",
            "Guard" to "Continue",
            "Review" to "Open Hisn"
        )

        steps.forEach { (step, nextAction) ->
            action(nextAction).assertIsDisplayed()
            val width = rule.onNode(isRoot()).fetchSemanticsNode().boundsInRoot.width
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                val label = node.spokenLabel()
                if (label.isEmpty()) problems += "$step contains an unlabelled control"
                val touchWidth = maxOf(node.touchBoundsInRoot.width, node.size.width.toFloat()) / density
                val touchHeight = maxOf(node.touchBoundsInRoot.height, node.size.height.toFloat()) / density
                if (touchWidth + 0.5f < 48f || touchHeight + 0.5f < 48f) {
                    problems += "$step control '${label.take(30)}' is ${touchWidth.toInt()}x${touchHeight.toInt()} dp"
                }
            }
            rule.onAllNodes(SemanticsMatcher("has text") {
                it.config.getOrNull(SemanticsProperties.Text) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { !it.insideHorizontalScroll() }
                .filter { it.boundsInRoot.left < -1f || it.boundsInRoot.right > width + 1f }
                .forEach { problems += "$step text '${it.spokenLabel().take(30)}' runs off screen" }
            tap(nextAction)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
