package app.hisn.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import app.hisn.core.PersonalProfile;

/**
 * A home-screen widget with the day count and the breathing pause: the two things worth seeing
 * without opening the app. Tapping the count opens the app; the button opens the pause directly.
 * Nothing on it is private beyond a number, so it is safe on a shared home screen.
 */
public final class DayWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = views(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    /** Redraws every placed widget; called when the count or the pauses change. */
    static void update(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager == null) return;
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, DayWidget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews views = views(context);
            for (int id : ids) manager.updateAppWidget(id, views);
        } catch (RuntimeException unavailable) {
            // No widget host (some launchers, tests): nothing to redraw.
        }
    }

    static RemoteViews views(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day);
        PersonalProfile profile = new PersonalStore(context).profile();
        int day = PersonalStore.dayNumber(profile);
        views.setTextViewText(R.id.widget_day, day > 0
                ? context.getString(R.string.personal_day_number, day)
                : context.getString(R.string.widget_day_start));
        // Unrot: today's feed time next to "before", the number the app is about.
        String feeds = null;
        try { feeds = WeeklyReport.widgetLine(context); } catch (RuntimeException unavailable) { }
        views.setViewVisibility(R.id.widget_feeds, feeds == null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (feeds != null) views.setTextViewText(R.id.widget_feeds, feeds);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        Intent app = new Intent(context, ShellActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 10, app, flags));
        Intent pause = PauseEntry.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widget_breathe, PendingIntent.getActivity(context, 11, pause, flags));
        return views;
    }
}
