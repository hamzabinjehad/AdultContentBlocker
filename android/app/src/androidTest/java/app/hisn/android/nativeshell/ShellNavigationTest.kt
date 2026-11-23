package app.hisn.android.nativeshell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hisn.android.BuildConfig
import app.hisn.android.DnsVpnService
import app.hisn.android.PolicyStore
import app.hisn.core.DailyAppSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's tabs, rows and back navigation, and that the home screen tells the truth. */
@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    @get:Rule val rule = createComposeRule()

    private fun tab(label: String) = rule.onNode(hasText(label) and isSelectable())

    private fun ruleRow(label: String, substring: Boolean = true): androidx.compose.ui.test.SemanticsNodeInteraction {
        val row = hasText(label, substring = substring) and hasClickAction()
        rule.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(
            SemanticsProperties.VerticalScrollAxisRange
        )).performScrollToNode(row)
        return rule.onNode(row)
    }

    @Test fun tabsSwitchAndShowTheirContent() {
        rule.showShell()
        tab(HomeTabLabel).assertIsDisplayed()
        if (unrotEdition) {
            // Unrot: Shield holds the protection; its "All rules" row opens the full list.
            tab("Shield").performClick()
            rule.waitForIdle()
            ruleRow("All rules", substring = false).performClick()
        } else {
            tab("Rules").performClick()
        }
        rule.waitForIdle()
        // Unrot groups rules by outcome; personal sites live under "Your lists".
        ruleRow(if (unrotEdition) "Your lists" else "Blocked domains").assertIsDisplayed()
        if (unrotEdition) {
            rule.onNode(androidx.compose.ui.test.hasContentDescription("Back") and hasClickAction()).performClick()
            rule.waitForIdle()
        }
        tab(SettingsTabLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").performScrollTo().assertIsDisplayed()
        tab(HomeTabLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").assertDoesNotExist()
    }

    @Test fun aSavedRuleWithItsLayerOffOffersTheFixOnItsTile() {
        // Hisn's tile grid; Unrot shows the website filter's switch on the same rules page instead.
        org.junit.Assume.assumeFalse(unrotEdition)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val actions = FakeShellActions()
        val state = HisnState(context, actions)
        state.addDomain("tile-test.example")
        org.junit.Assert.assertEquals(1, state.domains.count { it == "tile-test.example" })
        try {
            rule.setContent { NativeShell(state = state, initialTab = RulesTab, initialDetail = RulesDetail) }
            rule.waitForIdle()
            val chip = ruleRow("DNS filtering is off", substring = false)
            val bounds = chip.fetchSemanticsNode().touchBoundsInRoot
            val density = context.resources.displayMetrics.density
            org.junit.Assert.assertTrue(
                "chip is ${bounds.width / density}x${bounds.height / density} dp",
                bounds.height / density + 0.5f >= 48f && bounds.width / density + 0.5f >= 48f
            )
            chip.performClick()
            rule.waitForIdle()
            // The chip asks Android for the tunnel; it does not open the editor or claim success.
            org.junit.Assert.assertEquals(listOf("requestDns"), actions.calls)
        } finally {
            state.removeDomain("tile-test.example")
        }
    }

    @Test fun appPickerOffersUsageAccessWhenItCannotSuggest() {
        // The offer only shows without usage access, so take it away for this test.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        fun usage(mode: String) = instrumentation.uiAutomation
            .executeShellCommand("appops set $pkg GET_USAGE_STATS $mode").close()
        usage("ignore")
        try { offerUsageAccess() } finally { usage("allow") }
    }

    private fun offerUsageAccess() {
        val actions = rule.showShell(tab = RulesTab, detail = RulesDetail)
        ruleRow(if (unrotEdition) "Whole apps" else "Apps to block").performClick()
        rule.waitForIdle()
        // Unrot's Your apps page offers usage access in its own card; Hisn's picker in a row.
        val offer = if (unrotEdition) "Allow usage access" else "most-used apps first"
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText(offer, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(offer, substring = true) and hasClickAction()).performClick()
        org.junit.Assert.assertTrue("openPermission:usage" in actions.calls)
    }

    @Test fun keyboardDoneAddsADomain() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val state = HisnState(context, FakeShellActions())
        try {
            rule.setContent { NativeShell(state = state, initialTab = RulesTab, initialDetail = if (unrotEdition) "lists" else RulesDetail) }
            rule.waitForIdle()
            if (unrotEdition) rule.onNode(hasText("Sites") and hasClickAction()).performClick()
            else ruleRow("Blocked domains").performClick()
            rule.waitForIdle()
            val field = rule.onNode(androidx.compose.ui.test.hasSetTextAction())
            field.performTextInput("done-key.example")
            field.performImeAction()
            rule.waitForIdle()
            org.junit.Assert.assertTrue("done-key.example" in state.domains)
        } finally {
            state.removeDomain("done-key.example")
        }
    }

    @Test fun ruleEditsKeepOtherWritersChangesAndRejectInvalidInput() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val store = PolicyStore.get(context)
        val original = store.snapshot()
        val stale = HisnState(context, FakeShellActions())
        try {
            store.update("other-writer.example", "another rule", original.blockedApps, original.scannedApps)
            assertTrue(stale.addDomain("HTTPS://My-RULE.example.:443/path"))
            assertFalse(stale.addDomain("my-rule.example/link"))
            assertFalse(stale.addDomain("https://127.0.0.1/a"))
            assertTrue(stale.addWord("Example Phrase"))
            assertFalse(stale.addWord("example phrase"))
            assertFalse(stale.addWord("…"))
            assertEquals(setOf("my-rule.example", "other-writer.example"), stale.domains.toSet())
            assertEquals(setOf("another rule", "example phrase"), stale.words.toSet())
            assertEquals(stale.domains.toSet(), store.snapshot().domains.blockedRules())
            assertEquals(stale.words.toSet(), store.snapshot().words.configuredRules())
        } finally {
            store.update(original.domains.blockedRules().joinToString("\n"),
                original.words.configuredRules().joinToString("\n"), original.blockedApps, original.scannedApps,
                original.adultWordsEnabled)
        }
    }

    @Test fun scheduleSavesOnlyAfterValidDraftAndExplicitSave() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val store = PolicyStore.get(context)
        val original = store.snapshot().schedule
        val initial = DailyAppSchedule.disabled()
        val actions = FakeShellActions().apply { scheduleSelection = setOf("org.hisn.fixture") }
        try {
            store.updateSchedule(initial)
            val state = HisnState(context, actions)
            rule.setContent { HisnTheme(darkTheme = false) { ScheduleScreen(state, onBack = {}) } }
            ruleRow("Save schedule", substring = false).assertIsNotEnabled()
            ruleRow("Enable this schedule", substring = false).performClick()
            ruleRow("Save schedule", substring = false).performClick()
            rule.onNodeWithText("Choose at least one app before enabling the schedule.").assertExists()
            assertEquals(initial, store.snapshot().schedule)

            ruleRow("Apps to block on this schedule").performClick()
            rule.runOnIdle {
                assertEquals(listOf("pickScheduleApps:false"), actions.calls)
                assertEquals(initial, store.snapshot().schedule)
            }
            ruleRow("Save schedule", substring = false).performClick()
            rule.runOnIdle {
                assertEquals(
                    DailyAppSchedule(true, initial.startMinute, initial.endMinute, setOf("org.hisn.fixture")),
                    store.snapshot().schedule
                )
            }
        } finally {
            store.updateSchedule(original)
        }
    }

    @Test fun unrotShieldSortsProtectionByOutcomeAndAddsFromOneSheet() {
        org.junit.Assume.assumeTrue(unrotEdition)
        val actions = FakeShellActions()
        rule.showShell(actions, tab = RulesTab)
        rule.onNode(hasText("Endless feeds") and hasClickAction()).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Quiet hours") and hasClickAction()).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Adult sites") and hasClickAction()).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Add") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Add a rule").assertIsDisplayed()
        rule.onNode(hasText("Block a website") and hasClickAction()).performClick()
        rule.waitForIdle()
        // The sheet closes and the sites list opens.
        rule.onNodeWithText("Add a rule").assertDoesNotExist()
        rule.onNode(hasContentDescription("Back") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun homeNeverClaimsProtectedWhileNothingRuns() {
        // In the test process the tunnel is not running and the guard is not connected.
        rule.showShell()
        rule.onNodeWithText("Protected").assertDoesNotExist()
    }

    @Test fun homeOffersABreathingPauseThatBlocksNothing() {
        val actions = FakeShellActions()
        rule.showShell(actions)
        // Unrot's pause is the Breathe tool on Today (the floating button shows once it scrolls
        // away); the Islamic edition keeps the card.
        if (unrotEdition) {
            // The tools row and the floating button both say Breathe; either opens the same pause.
            rule.onAllNodes(hasText("Breathe") and hasClickAction()).onFirst().performClick()
        } else {
            rule.onNodeWithText("Feeling the pull?").performScrollTo().assertIsDisplayed()
            rule.onNode(hasText("Take a pause") and hasClickAction()).performScrollTo().performClick()
        }
        assertEquals(listOf("openPause"), actions.calls)
    }

    @Test fun homeShowsTheDayCountWithTheNextMilestone() {
        val actions = FakeShellActions().apply {
            personalSummary = PersonalSummary(greeting = "Good evening, Sam", day = 12, pauses = 3)
            finishUnrotSetup()
        }
        rule.showShell(actions)
        // The day card lives on Progress now; Home keeps the day in its header pill.
        rule.onNode(hasText("Progress") and hasClickAction()).performClick()
        rule.waitForIdle()
        // Progress is a lazy list: bring the card into composition before looking for it.
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Day 12"))
        rule.onNodeWithText("Day 12").assertIsDisplayed()
        rule.onNodeWithText("2 days to day 14").assertIsDisplayed()
        rule.onNodeWithText("3 pauses taken").assertIsDisplayed()
    }

    @Test fun homeOffersPrivateDnsRepairWhenTheTunnelIsAttached() {
        val actions = FakeShellActions().apply {
            liveProtection = ProtectionRuntime(
                dns = DnsVpnService.State.RUNNING,
                dnsRequested = true,
                privateDnsStrict = true,
                guardExpected = true
            )
            grantedPermissions += "accessibility"
            finishUnrotSetup()
        }
        rule.showShell(actions)
        rule.onNodeWithText("Protection needs attention").assertIsDisplayed()
        rule.onNodeWithText("Fix now").performClick()
        assertEquals(listOf("openPermission:privateDns"), actions.calls)
    }

    @Test fun homeOffersARealRouteCheckForActiveWebsiteFiltering() {
        val actions = FakeShellActions().apply {
            liveProtection = ProtectionRuntime(dns = DnsVpnService.State.RUNNING, dnsRequested = true)
            if (unrotEdition) {
                // Unrot's checklist stands in for the status until setup is done; finish it with
                // app protection really connected, so the status is the healthy one.
                finishUnrotSetup()
                liveProtection = liveProtection.copy(guardExpected = true, guardReady = true)
            }
        }
        // Unrot keeps the route check on the Shield tab, under its one-sentence status.
        rule.showShell(actions, tab = if (unrotEdition) RulesTab else 0)
        if (!unrotEdition) rule.onNodeWithText("Protection is running").assertIsDisplayed()
        rule.onNodeWithText("Check the DNS route").performClick()
        assertEquals(listOf("confirmNetworkCheck"), actions.calls)
    }

    @Test fun homeOffersVpnSettingsWhenAndroidBlocksTrafficOutsideTheDnsTunnel() {
        val actions = FakeShellActions().apply {
            liveProtection = ProtectionRuntime(
                dns = DnsVpnService.State.RUNNING, dnsRequested = true, dnsLockdown = true
            )
            finishUnrotSetup()
        }
        rule.showShell(actions)
        rule.onNodeWithText("Protection needs attention").assertIsDisplayed()
        rule.onNodeWithText("Fix now").performClick()
        assertEquals(listOf("openPhoneSetting:vpn"), actions.calls)
    }

    @Test fun uninstallRowOpensTheRightThingForTheBuild() {
        val actions = rule.showShell(tab = SettingsTab)
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

    @Test fun unrotYouKeepsCommitmentsAndMovesToolsOneRowDeeper() {
        org.junit.Assume.assumeTrue(unrotEdition)
        rule.showShell(tab = SettingsTab)
        rule.onNodeWithText("Commitment").assertIsDisplayed()
        rule.onNodeWithText("Guardian pairing").performScrollTo().assertIsDisplayed()
        // Tools, appearance and help are one row deeper, all still there.
        rule.onNodeWithText("Theme").assertDoesNotExist()
        ruleRow("Tools and help").performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Theme") and hasClickAction()).assertIsDisplayed()
        rule.onNode(hasContentDescription("Back") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Commitment").assertIsDisplayed()
    }

    @Test fun detailScreenReturnsWithBack() {
        rule.showShell(tab = SettingsTab)
        rule.onNodeWithText("Permissions").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Back")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Uninstall protection").performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionRowsHandOffToAndroid() {
        val actions = rule.showShell(tab = SettingsTab)
        rule.onNodeWithText("Permissions").performScrollTo().performClick()
        rule.waitForIdle()
        // Every permission reads "off" from the fake; tapping App protection must ask Android.
        // Exactly one match also proves the covered Settings row is hidden from accessibility.
        // Unrot names it as on Shield: "See which app is open".
        val row = if (unrotEdition) "See which app is open" else "App protection"
        rule.onNode(hasText(row, substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertTrue("no hand-off in ${actions.calls}",
            actions.calls.any { it.startsWith("openPermission") || it == "openAccessibility" })
    }
}
