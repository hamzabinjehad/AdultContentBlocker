package app.hisn.android;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The widget inflates the way a launcher inflates it, and says something true. */
@RunWith(AndroidJUnit4.class)
public final class DayWidgetTest {
    @Test public void inflatesAsALauncherWouldAndOffersThePause() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            RemoteViews views = DayWidget.views(context);
            View root = views.apply(context, new FrameLayout(context));
            TextView day = root.findViewById(R.id.widget_day);
            TextView breathe = root.findViewById(R.id.widget_breathe);
            int number = PersonalStore.dayNumber(new PersonalStore(context).profile());
            String expected = number > 0 ? context.getString(R.string.personal_day_number, number)
                    : context.getString(R.string.widget_day_start);
            assertEquals(expected, day.getText().toString());
            assertEquals(context.getString(R.string.today_tool_breathe), breathe.getText().toString());
            assertTrue(breathe.hasOnClickListeners());
        });
    }
}
