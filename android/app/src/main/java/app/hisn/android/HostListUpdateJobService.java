package app.hisn.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs one gated list check; a failed check keeps the last verified generation. */
public final class HostListUpdateJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        AdultListUpdater.check(this, false, result -> jobFinished(params, false));
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return false; }
}
