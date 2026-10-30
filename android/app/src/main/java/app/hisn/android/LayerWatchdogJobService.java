package app.hisn.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Runs the periodic layer-health check. The work is a few local reads and one
 * notification at most, so it finishes synchronously on the job thread and never
 * keeps the process alive. A failure is caught: a watchdog that crashes must not
 * take the app's other components with it.
 */
public final class LayerWatchdogJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        try {
            LayerWatchdog.check(this);
        } catch (RuntimeException failure) {
            android.util.Log.w("HisnLayerWatch", "Layer check failed", failure);
        }
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
