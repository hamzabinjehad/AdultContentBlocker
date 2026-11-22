package app.hisn.android.nativeshell

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisn.android.FocusSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** "Focus now" closes only the apps it names, only until its end, and ends early on request. */
@RunWith(AndroidJUnit4::class)
class FocusSessionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun clear() = FocusSession.stop(context)

    @Test fun aSessionClosesItsAppsUntilItEnds() {
        assertTrue(FocusSession.start(context, 25, setOf("com.google.android.youtube")))
        assertTrue(FocusSession.active(context))
        assertTrue(FocusSession.blocks("com.google.android.youtube"))
        assertFalse(FocusSession.blocks("com.google.android.apps.messaging"))
        val left = FocusSession.until(context) - System.currentTimeMillis()
        assertTrue(left in 24 * 60_000L..25 * 60_000L)
    }

    @Test fun endingEarlyOpensEverythingAgain() {
        FocusSession.start(context, 50, setOf("com.instagram.android"))
        FocusSession.stop(context)
        assertFalse(FocusSession.active(context))
        assertFalse(FocusSession.blocks("com.instagram.android"))
        assertEquals(0L, FocusSession.until(context))
    }

    @Test fun nothingToCloseStartsNothing() {
        assertFalse(FocusSession.start(context, 25, emptySet()))
        assertFalse(FocusSession.start(context, 0, setOf("com.instagram.android")))
        assertFalse(FocusSession.active(context))
    }
}
