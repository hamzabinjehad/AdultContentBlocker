package app.hisn.android.nativeshell

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UX checks on every tab: a screen reader can name each control, each control is big enough to
 * tap (48 dp, Material and Android guidance), and nothing runs off the screen at 2x text or in
 * right-to-left layout. Failures list every offending control at once.
 */
@RunWith(AndroidJUnit4::class)
class ShellAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = if (unrotEdition) listOf("Today", "Shield", "Rules", "Progress", "You")
        else listOf("Protection", "Rules", "Settings")
    private val density = InstrumentationRegistry.getInstrumentation().targetContext
        .resources.displayMetrics.density

    private fun eachTab(check: (String) -> List<String>): List<String> {
        val problems = mutableListOf<String>()
        tabs.forEachIndexed { index, name ->
            if (index > 0) {
                rule.onNode(hasText(name) and isSelectable()).performClick()
                rule.waitForIdle()
            }
            problems += check(name).map { "$name: $it" }
        }
        return problems
    }

    private fun report(what: String, problems: List<String>) =
        assertTrue("$what (${problems.size}):\n" + problems.joinToString("\n"), problems.isEmpty())

    @Test fun everyControlHasASpokenLabel() {
        rule.showShell()
        report("Controls without a label", eachTab {
            val controls = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            // An empty tab would pass every check below; require real controls.
            assertTrue("$it shows no controls", controls.size >= 4)
            controls.filter { it.spokenLabel().isEmpty() }
                .map { "unlabelled control at ${it.boundsInRoot}" }
        })
    }

    @Test fun everyControlIsBigEnoughToTap() {
        rule.showShell()
        report("Controls under 48 dp", eachTab {
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
                // Off-screen rows have clipped (empty) touch bounds; fall back to their layout size.
                val bounds = node.touchBoundsInRoot
                val width = maxOf(bounds.width, node.size.width.toFloat()) / density
                val height = maxOf(bounds.height, node.size.height.toFloat()) / density
                if (width + 0.5f >= 48f && height + 0.5f >= 48f) null
                else "'${node.spokenLabel().take(40)}' is ${width.toInt()}x${height.toInt()} dp"
            }
        })
    }

    @Test fun nothingRunsOffScreenAtDoubleText() = checkFits(fontScale = 2f, rtl = false)

    @Test fun nothingRunsOffScreenRightToLeft() = checkFits(fontScale = 1f, rtl = true)

    @Test fun nothingRunsOffScreenRightToLeftAtLargeText() = checkFits(fontScale = 1.5f, rtl = true)

    private fun checkFits(fontScale: Float, rtl: Boolean) {
        rule.showShell(fontScale = fontScale, rtl = rtl)
        report("Text outside the screen at ${fontScale}x${if (rtl) ", RTL" else ""}", eachTab {
            val width = rule.onNode(isRoot()).fetchSemanticsNode().boundsInRoot.width
            rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("has text") {
                it.config.getOrNull(SemanticsProperties.Text) != null
            }, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { !it.insideHorizontalScroll() }
                .filter { it.boundsInRoot.left < -1f || it.boundsInRoot.right > width + 1f }
                .map { "'${it.spokenLabel().take(40)}' spans ${it.boundsInRoot.left.toInt()}..${it.boundsInRoot.right.toInt()} of ${width.toInt()} px" }
        })
    }
}
