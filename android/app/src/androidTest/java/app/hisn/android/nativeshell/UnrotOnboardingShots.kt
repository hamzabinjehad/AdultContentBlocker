package app.hisn.android.nativeshell

import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.core.SetupPlan
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the whole setup and saves a screenshot of every step, for design review. Runs only with
 * `-e shots 1`; normal test runs skip it. Pull with
 * `adb pull /sdcard/Android/data/<test app>/files/shots`.
 */
@RunWith(AndroidJUnit4::class)
class UnrotOnboardingShots {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val actions = object : UnrotSetupActions {
        override fun granted(key: String) = false
        override fun protectionState(key: String) = OnboardingProtectionState.ON
        override fun request(key: String) {}
        override fun guardOn() = true
        override fun savePersonal(name: String, preset: String, reason: String) {}
        override fun completeSetup() {}
        override fun applyPlan(plan: SetupPlan, note: String) = true
    }

    private var n = 0
    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, String.format("%02d-%s.png", ++n, name)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) {
        val node = rule.onNode(hasText(text) and hasClickAction())
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    @Test fun everyStep() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("shots") == "1")
        rule.setContent {
            HisnTheme(darkTheme = InstrumentationRegistry.getArguments().getString("dark") == "1") {
                UnrotOnboarding(actions = actions, refresh = 0, initialReason = "")
            }
        }
        shot("hello"); tap("Set it up · 4 min")
        shot("demo1"); tap("Next")
        shot("demo2"); tap("Next")
        shot("demo3"); tap("Set mine up")
        rule.onNode(hasSetTextAction()).performTextInput("Sam")
        shot("name"); tap("Next")
        shot("age"); tap("25–34")
        tap("Sleep"); tap("Evenings with people")
        shot("goals"); tap("2 picked · Next")
        shot("when"); tap("Late at night, in bed")
        tap("Tired"); tap("Behind on things")
        shot("feel"); tap("Next")
        shot("start")
        if (rule.onAllNodes(hasText("Use my guess") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) tap("Use my guess")
        else tap("That’s me · Next")
        shot("year"); tap("What does it cost me?")
        shot("cost"); tap("Why is it so hard?")
        shot("hard"); tap("What does Unrot do?")
        shot("solve"); tap("Let’s get them back")
        tap("Read"); tap("See friends")
        shot("instead"); tap("Next")
        shot("apps")
        rule.onAllNodes(hasText("· Next", substring = true) and hasClickAction())[0].performClick(); rule.waitForIdle()
        shot("firm"); tap("Next")
        shot("try"); tap("That’s fair · Next")
        shot("quiet"); tap("Next")
        rule.onAllNodes(hasText("You’ll be tired tomorrow.") and hasClickAction())[0].performClick(); rule.waitForIdle()
        shot("note"); tap("Save my note")
        shot("plan"); tap("Make it live")
        shot("switches"); tap("Next")
        shot("notify"); tap("Not now")
        shot("test"); tap("I believe you · skip")
        shot("hold"); tap("Skip, I’ll just start")
        shot("done")
    }
}
