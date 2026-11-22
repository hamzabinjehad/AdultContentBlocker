package app.hisn.android.nativeshell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** My notes: write anything, kept per day, edit and delete with undo. */
@RunWith(AndroidJUnit4::class)
class UnrotNotesTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val today get() = LocalDate.now()

    @Before fun clean() {
        assumeTrue(unrotEdition)
        Notes.day(context, today).forEach { Notes.remove(context, today, it) }
    }
    @After fun tidy() = Notes.day(context, today).forEach { Notes.remove(context, today, it) }

    private fun show() = rule.setContent { HisnTheme(darkTheme = false) { UnrotNotesScreen(onBack = {}) } }

    @Test fun aNoteIsSavedOnTodayAndCanBeEditedAndDeleted() {
        show()
        rule.onNodeWithText("Nothing written today yet.").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("Long day at school, phone stayed in the bag")
        rule.onNode(hasText("Save") and hasClickAction()).performClick()
        rule.onNodeWithText("Long day at school, phone stayed in the bag").assertIsDisplayed()
        assertEquals(1, Notes.day(context, today).size)

        rule.onNode(hasText("Edit") and hasClickAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("Edited note")
        rule.onNode(hasText("Save changes") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(listOf("Edited note"), Notes.day(context, today).map { it.text })

        rule.onNode(hasText("Delete") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(0, Notes.day(context, today).size)
    }

    @Test fun notesStayOnTheirOwnDay() {
        val yesterday = today.minusDays(1)
        Notes.add(context, yesterday, "Yesterday's note")
        try {
            assertEquals(0, Notes.day(context, today).size)
            assertEquals(setOf(yesterday.dayOfMonth).takeIf { yesterday.month == today.month } ?: emptySet<Int>(),
                Notes.daysWithNotes(context, java.time.YearMonth.from(today)))
        } finally {
            Notes.day(context, yesterday).forEach { Notes.remove(context, yesterday, it) }
        }
    }
}
