package app.hisn.android;

import android.content.Context;
import app.hisn.core.AdultTextPolicy;
import app.hisn.core.WordRuleList;
import java.io.InputStream;

/** Fixed synthetic examples against the actual APK asset; no owner policy or permissions touched. */
final class AdultPolicyChecks {
    static void run(Context context) throws Exception {
        final AdultTextPolicy policy;
        try (InputStream input = context.getAssets().open("terms.json")) {
            policy = AdultPolicyAssets.load(input, BuildConfig.TERMS_SEED_SHA256);
        }
        for (String text : new String[]{"Watch p.o.r.n videos", "Download p_0_r_n", "Watch h-e-n-t-4-1",
                "شاهد إ.ب.ا.ح.ي.ة مجانا", "تحميل س_ك_س مجانا", "Porn videos", "محتوى إباحي"}) {
            if (!policy.blocks(text)) throw new AssertionError("Synthetic compact-spelling detection failed");
        }
        for (String text : new String[]{"Download a free porn blocker", "Download a free p.o.r.n blocker",
                "شاهد طريقة حجب الإباحية", "تحميل أدوات حظر الإباحية", "Watch x.p.o.r.n.y",
                "Watch porn addiction recovery resources", "Watch sex education"}) {
            if (policy.blocks(text)) throw new AssertionError("Synthetic benign-context control blocked");
        }
        if (WordRuleList.compile("porn").matches("Download a free porn blocker").isEmpty())
            throw new AssertionError("Literal custom rule lost independence");
    }
    private AdultPolicyChecks() { }
}
