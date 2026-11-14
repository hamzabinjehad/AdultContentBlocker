package app.hisn.android.nativeshell

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.android.OemSupport
import app.hisn.core.DeviceFamily
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone-specific setup screen: the three makers the owner named (Samsung, Xiaomi, Nothing),
 * plus the build classification read on whatever device runs the test.
 *
 * The fake actions supply the phone reading, so the layout is checked without opening a system
 * screen; the last test reads the real build on the real phone or emulator.
 */
@RunWith(AndroidJUnit4::class)
class PhoneSetupTest {
    @get:Rule val rule = createComposeRule()

    private fun phone(
        maker: String,
        model: String,
        family: DeviceFamily,
        battery: Boolean,
        background: Boolean?
    ) = PhoneState(
        maker = maker,
        model = model,
        android = "Android 16 · API 36",
        family = family,
        batteryUnrestricted = battery,
        backgroundRestricted = background
    )

    private fun openPhone(actions: FakeShellActions): FakeShellActions {
        rule.showShell(actions, tab = 2)
        rule.onNode(hasText("Keep protection running") and hasClickAction())
            .performScrollTo().performClick()
        rule.waitForIdle()
        return actions
    }

    @Test fun samsungShowsItsMeasuredGapsAndTheRecentsHint() {
        val actions = FakeShellActions().apply {
            phoneState = phone(
                "Samsung", "SM-G990B2", DeviceFamily.SAMSUNG,
                battery = false, background = null
            )
        }
        openPhone(actions)
        rule.onNodeWithText("Samsung SM-G990B2").assertIsDisplayed()
        rule.onNodeWithText("Samsung").assertIsDisplayed()
        rule.onNode(hasText("Battery") and hasClickAction()).assertIsDisplayed().performClick()
        rule.onNode(hasText("Keep Hisn in Recents")).performScrollTo().assertIsDisplayed()
        // Samsung has no autostart screen in the app's guide; a row there would be invented.
        rule.onNodeWithText("Autostart").assertDoesNotExist()
        assertEquals(listOf("openPhoneSetting:battery"), actions.calls)
    }

    @Test fun xiaomiAutostartTickIsSavedAndOpensTheMakerScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("hisn-native", Context.MODE_PRIVATE)
        prefs.edit().remove("phone.autostart").remove("phone.recents").apply()
        try {
            val actions = FakeShellActions().apply {
                phoneState = phone(
                    "Xiaomi", "23122PCD1G", DeviceFamily.XIAOMI,
                    battery = true, background = false
                )
            }
            openPhone(actions)
            rule.onNodeWithText("Autostart").assertIsDisplayed()
            rule.onNode(hasText("Keep Hisn in Recents")).performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("Confirm Autostart").performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Confirm Autostart").assertIsOn()
            assertTrue(
                "the owner's autostart tick was not saved",
                prefs.getBoolean("phone.autostart", false)
            )
            // Autostart's "Open" comes first; the pop-up row below has its own.
            rule.onAllNodes(hasText("Open") and hasClickAction()).onFirst().performClick()
            assertTrue("openPhoneSetting:autostart" in actions.calls)
        } finally {
            prefs.edit().remove("phone.autostart").remove("phone.recents").apply()
        }
    }

    @Test fun xiaomiPopupDenialIsAnActionThatOpensTheSwitch() {
        val actions = FakeShellActions().apply {
            phoneState = phone("Xiaomi", "23122PCD1G", DeviceFamily.XIAOMI, battery = true, background = false)
                .copy(popupsAllowed = false)
        }
        openPhone(actions)
        rule.onNode(hasText("Pop-ups from the background")).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Not allowed", substring = true)).assertIsDisplayed()
        rule.onNode(hasText("Pop-ups from the background") and hasClickAction(), useUnmergedTree = false)
            .performClick()
        assertTrue("openPhoneSetting:popups" in actions.calls)
    }

    @Test fun nothingOsNeedsOnlyAndroidOwnSetting() {
        val actions = FakeShellActions().apply {
            phoneState = phone(
                "Nothing", "A065", DeviceFamily.NOTHING,
                battery = true, background = false
            )
        }
        openPhone(actions)
        rule.onNodeWithText("Nothing A065").assertIsDisplayed()
        rule.onNodeWithText("Nothing").assertIsDisplayed()
        rule.onNodeWithText("Phone checks done").assertIsDisplayed()
        rule.onNodeWithText("Autostart").assertDoesNotExist()
        rule.onNodeWithText("Keep Hisn in Recents").assertDoesNotExist()
    }

    @Test fun homeOffersThePhoneFixWhenAndroidRestrictsTheBattery() {
        val actions = FakeShellActions().apply {
            phoneState = phone(
                "Xiaomi", "23122PCD1G", DeviceFamily.XIAOMI,
                battery = false, background = true
            )
        }
        rule.showShell(actions, tab = 0)
        rule.onNode(hasText("Xiaomi may pause Hisn") and hasClickAction())
            .performScrollTo().assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Xiaomi 23122PCD1G").assertIsDisplayed()
    }

    @Test fun theRealBuildIsClassifiedAndItsBackgroundSwitchReads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val family = OemSupport.family()
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        when {
            manufacturer.contains("samsung") -> assertEquals(DeviceFamily.SAMSUNG, family)
            manufacturer.contains("xiaomi") -> assertEquals(DeviceFamily.XIAOMI, family)
            manufacturer.contains("nothing") -> assertEquals(DeviceFamily.NOTHING, family)
            manufacturer.contains("google") || Build.MODEL.startsWith("sdk_gphone") ->
                assertEquals(DeviceFamily.STOCK, family)
        }
        assertTrue("the maker is empty", OemSupport.maker().isNotEmpty())
        assertTrue("the Android line is wrong: " + OemSupport.android(),
            OemSupport.android().startsWith("Android"))
        if (Build.VERSION.SDK_INT >= 28) {
            assertNotNull(
                "Android reports no background-restriction value",
                OemSupport.backgroundRestricted(context)
            )
        }
        if (family == DeviceFamily.SAMSUNG) {
            // One UI 8 does not resolve the standard battery request, and Device care's power
            // screens need a Samsung signature permission, so app info must be a candidate.
            val intents = OemSupport.batteryIntents(context)
            assertTrue(
                "a protected Device care screen is still a candidate",
                intents.none { it.component?.packageName == "com.samsung.android.lool" }
            )
            assertTrue(
                "no reachable Android battery screen is a candidate",
                intents.any { it.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS }
            )
        }
    }
}
