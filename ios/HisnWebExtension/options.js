const TEXT = {
  en: {
    title: "Protection layers", subtitle: "Hisn for Safari", introLabel: "Your browser protection",
    introDetail: "Choose what Hisn checks and what you prefer to blur. Each layer has its own job.",
    openSettings: "Open full settings", permissionsTitle: "Allow access in Safari", permissionsBadge: "Required",
    permissionsDetail: "Hisn can check only the Safari websites you allow. These choices do not grant website access.",
    permissionsStepsTitle: "How to enable website protection",
    permissionsStepOne: "In Safari, open the Extensions menu and enable Hisn Text.",
    permissionsStepTwo: "In Settings → Apps → Safari → Extensions → Hisn Text, review website access and choose Allow for the sites you want protected.",
    permissionsStepThree: "Review access for your Safari profile and Private Browsing too. Return to the webpage after changing access.",
    textTitle: "Text checking", textBadge: "Included",
    textDetail: "Checks page text and individual X posts on-device. Text checks are separate from the optional visual choices below.",
    visualTitle: "Adult-media checks", visualBadge: "Experimental",
    adultDetail: "Checks readable images and sampled video frames for adult content. This does not depend on a person’s appearance.",
    adultLabel: "Hide media flagged as adult", personTitle: "Person blur", personBadge: "Optional",
    personDetail: "A personal viewing preference, not an adult-content label. A matching detected face blurs the whole image or video.",
    personLabel: "Choose who to blur", modeOff: "Off", modeOffDetail: "No person blur",
    modeFemale: "Women", modeFemaleDetail: "Estimated face appearance", modeMale: "Men", modeMaleDetail: "Estimated face appearance",
    modeAll: "All people", modeAllDetail: "Any detected face",
    faceDetail: "Face detection is not whole-body detection. Women/men are appearance estimates, can be wrong, and may miss people. They do not identify a person’s gender or whether content is adult.",
    statusTitle: "Visual checker status",
    statusDetail: "A ready model is not proof that every image or video was checked. Protected or cross-origin media may be unreadable; unreadable is not the same as safe.",
    limitsTitle: "Coverage, privacy and limitations",
    limitsDetail: "Only readable images and sampled video frames on permitted Safari webpages can be checked. Some frames can be missed. Normal videos are not hidden by default. This extension does not inspect other apps or every iPhone/iPad browser.",
    privacyDetail: "Models are bundled. Images and frames are processed on this device, never uploaded or saved by Hisn.",
    commitmentDetail: "These Safari visual settings are separate from the app commitment. Safari can disable the extension; this page does not prevent removal or claim system-wide protection.",
    saveHint: "Changes save on this device.", retrySettings: "Retry loading settings",
  },
  ar: {
    title: "طبقات الحماية", subtitle: "حصن لمتصفح سفاري", introLabel: "حماية متصفحك",
    introDetail: "اختر ما يفحصه حصن وما تفضّل تمويهه. لكل طبقة مهمة مستقلة.",
    openSettings: "فتح الإعدادات الكاملة", permissionsTitle: "اسمح بالوصول في سفاري", permissionsBadge: "مطلوب",
    permissionsDetail: "يفحص حصن مواقع سفاري التي تسمح له بها فقط. هذه الخيارات لا تمنح إذن الوصول إلى المواقع.",
    permissionsStepsTitle: "كيفية تفعيل حماية المواقع",
    permissionsStepOne: "في سفاري، افتح قائمة الإضافات وفعّل Hisn Text.",
    permissionsStepTwo: "من الإعدادات ← التطبيقات ← سفاري ← الإضافات ← Hisn Text، راجع الوصول إلى المواقع واختر السماح للمواقع التي تريد حمايتها.",
    permissionsStepThree: "راجع أيضًا الإذن لملف تعريف سفاري والتصفح الخاص. ارجع إلى صفحة الموقع بعد تغيير الإذن.",
    textTitle: "فحص النصوص", textBadge: "مضمّن",
    textDetail: "يفحص نصوص الصفحات ومنشورات X منفردة على الجهاز. فحص النصوص مستقل عن الخيارات البصرية أدناه.",
    visualTitle: "فحص الوسائط الإباحية", visualBadge: "تجريبي",
    adultDetail: "يفحص الصور القابلة للقراءة وعيّنات من إطارات الفيديو للكشف عن المحتوى الإباحي. لا يعتمد ذلك على مظهر الشخص.",
    adultLabel: "إخفاء الوسائط المصنّفة كمحتوى إباحي", personTitle: "تمويه الأشخاص", personBadge: "اختياري",
    personDetail: "تفضيل شخصي للمشاهدة، وليس تصنيفًا للمحتوى الإباحي. عند تطابق وجه مكتشف تُموّه الصورة أو الفيديو بالكامل.",
    personLabel: "اختر من تريد تمويهه", modeOff: "إيقاف", modeOffDetail: "دون تمويه الأشخاص",
    modeFemale: "النساء", modeFemaleDetail: "تقدير مظهر الوجه", modeMale: "الرجال", modeMaleDetail: "تقدير مظهر الوجه",
    modeAll: "جميع الأشخاص", modeAllDetail: "أي وجه مكتشف",
    faceDetail: "كشف الوجوه ليس كشفًا لكامل الجسم. خيارات النساء والرجال تقديرات للمظهر قد تخطئ أو تفوّت أشخاصًا. لا تحدد جنس الشخص ولا تعني أن المحتوى إباحي.",
    statusTitle: "حالة الفاحص البصري",
    statusDetail: "جاهزية النموذج لا تؤكد فحص كل صورة أو فيديو. بعض الوسائط المحمية أو القادمة من مصدر مختلف غير قابلة للقراءة؛ وهذا لا يعني أنها آمنة.",
    limitsTitle: "التغطية والخصوصية والحدود",
    limitsDetail: "يمكن فحص الصور القابلة للقراءة وعيّنات من إطارات الفيديو في صفحات سفاري المسموح بها فقط. قد تفوت بعض الإطارات. لا تُخفى الفيديوهات العادية افتراضيًا. لا تفحص الإضافة التطبيقات الأخرى أو كل متصفحات الآيفون والآيباد.",
    privacyDetail: "النماذج مضمّنة. تُعالج الصور والإطارات على هذا الجهاز دون رفعها أو حفظها بواسطة حصن.",
    commitmentDetail: "هذه الإعدادات البصرية في سفاري منفصلة عن التزام التطبيق. يمكن تعطيل الإضافة من سفاري؛ هذه الصفحة لا تمنع حذفها ولا تدّعي حماية الجهاز كله.",
    saveHint: "تُحفظ التغييرات على هذا الجهاز.", retrySettings: "إعادة محاولة تحميل الإعدادات",
  },
};
const MODES = ["off", "female", "male", "all"];
function validSettings(value) {
  return value?.state !== "unavailable" && typeof value?.inspectVisual === "boolean"
    && MODES.includes(value?.personBlur);
}

/** The controller takes its dependencies explicitly so contract tests can use
 * the exact production UI logic without Safari accounts or model execution.
 * Website access remains Safari-owned; this page never reports it as granted.
 */
export function createOptionsController({ api, root, language = "en", schedule = globalThis.setInterval,
  delay = globalThis.setTimeout, cancelDelay = globalThis.clearTimeout }) {
  const byID = id => root.getElementById(id);
  const adult = byID("inspectVisual"), modes = [...root.querySelectorAll('input[name="personBlur"]')];
  const status = byID("engineStatus"), saved = byID("saveStatus"), retry = byID("retrySettings");
  let arabic = language.startsWith("ar"), settings = { inspectVisual:false, personBlur:"off" };
  let ready = false, saving = false, loading = false, statusState = "waiting", saveState = "loading";
  let statusVersion = 0;
  const say = (en, ar) => arabic ? ar : en;
  function request(message) {
    return new Promise((resolve, reject) => {
      // A suspended Safari background page must not freeze the controls forever.
      // Timing out a save means "not confirmed", not "the worker did not save".
      const timer = delay(() => reject(new Error("Safari response timed out")), 5000);
      Promise.resolve().then(() => api.runtime.sendMessage(message)).then(resolve, reject)
        .finally(() => cancelDelay(timer));
    });
  }

  function renderStatus() {
    const enabled = settings.inspectVisual || settings.personBlur !== "off";
    const labels = {
      disabled: enabled ? say("Waiting for readable media. Models load only when needed.", "بانتظار وسائط قابلة للقراءة. تُحمّل النماذج عند الحاجة فقط.") : say("Visual checks are off.", "الفحص البصري متوقف."),
      loading: say("Loading bundled models on this device…", "يجري تحميل النماذج المضمّنة على الجهاز…"),
      waiting: say("Enabled. Waiting for readable media to load the selected models.", "مفعّل. بانتظار وسائط قابلة للقراءة لتحميل النماذج المختارة."),
      ready: say("Selected models are ready. Not every image or video can be checked.", "النماذج المختارة جاهزة. لا يمكن فحص كل صورة أو فيديو."),
      failed: say("Visual checks are unavailable. Text checking remains independent.", "الفحص البصري غير متاح. فحص النصوص مستقل عنه."),
      unavailable: say("Could not reach the visual checker in this Safari session.", "تعذر الوصول إلى الفاحص البصري في جلسة سفاري هذه."),
    };
    status.textContent = ready ? labels[statusState] ?? labels.unavailable : say("Visual checker status is not available until settings load.", "حالة الفاحص البصري غير متاحة حتى تُحمّل الإعدادات.");
    byID("statusDot").dataset.state = ready ? statusState : "unavailable";
  }
  function renderSaved() {
    const labels = {
      idle:"", loading:say("Loading saved choices…", "يجري تحميل الخيارات المحفوظة…"),
      saving:say("Saving…", "جارٍ الحفظ…"), saved:say("Saved on this device.", "حُفظت على هذا الجهاز."),
      failed:say("Could not confirm the change. Showing your last confirmed choices. Try again.", "تعذر تأكيد التغيير. تظهر خياراتك التي تأكد حفظها آخر مرة. حاول مجددًا."),
      unavailable:say("Could not load settings. Visual controls remain unavailable.", "تعذر تحميل الإعدادات. الخيارات البصرية غير متاحة."),
      "open-failed":say("Could not open full settings. You can keep using this page.", "تعذر فتح الإعدادات الكاملة. يمكنك متابعة استخدام هذه الصفحة."),
    };
    saved.textContent = labels[saveState] ?? "";
    saved.dataset.state = saveState;
    retry.hidden = !["unavailable", "failed"].includes(saveState);
    retry.disabled = loading;
  }
  function renderControls() {
    adult.checked = settings.inspectVisual;
    adult.disabled = !ready || saving || loading;
    byID("adultState").textContent = ready ? (settings.inspectVisual ? say("On", "مفعّل") : say("Off", "متوقف")) : say("Not loaded", "لم تُحمّل الإعدادات");
    for (const mode of modes) {
      mode.checked = mode.value === settings.personBlur;
      mode.disabled = !ready || saving || loading;
    }
  }
  function renderLanguage() {
    root.documentElement.lang = arabic ? "ar" : "en";
    root.documentElement.dir = arabic ? "rtl" : "ltr";
    root.title = say("Hisn Web Protection", "حماية الويب — حصن");
    for (const [id, value] of Object.entries(TEXT[arabic ? "ar" : "en"])) byID(id).textContent = value;
    byID("language").textContent = arabic ? "English" : "العربية";
    byID("language").lang = arabic ? "en" : "ar";
    byID("language").setAttribute("aria-label", say("Switch to Arabic", "التبديل إلى الإنجليزية"));
    renderControls(); renderStatus(); renderSaved();
  }
  async function refreshStatus() {
    if (!ready || saving || loading) return;
    const version = ++statusVersion;
    try {
      const result = await request({ type:"getVisualStatus" });
      if (version !== statusVersion) return;
      statusState = ["disabled", "loading", "waiting", "ready", "failed", "unavailable"].includes(result?.state) ? result.state : "unavailable";
    } catch { if (version !== statusVersion) return; statusState = "unavailable"; }
    renderStatus();
  }
  async function load() {
    if (loading || saving) return;
    loading = true; ready = false; statusVersion++; saveState = "loading";
    renderControls(); renderStatus(); renderSaved();
    try {
      const result = await request({ type:"getVisualSettings" });
      if (!validSettings(result)) throw new Error("Unavailable settings");
      settings = { inspectVisual:result.inspectVisual, personBlur:result.personBlur };
      ready = true; saveState = "idle";
    } catch { ready = false; saveState = "unavailable"; }
    finally { loading = false; renderControls(); renderStatus(); renderSaved(); }
    if (ready) await refreshStatus();
  }
  async function change(patch) {
    if (!ready || saving || loading) { renderControls(); return; }
    saving = true; statusVersion++; saveState = "saving";
    renderControls(); renderSaved();
    try {
      const result = await request({ type:"saveVisualSettings", patch });
      if (!validSettings(result?.settings)) throw new Error("Settings unavailable");
      settings = { inspectVisual:result.settings.inspectVisual, personBlur:result.settings.personBlur };
      saveState = "saved";
    } catch { saveState = "failed"; }
    finally { saving = false; renderControls(); renderSaved(); await refreshStatus(); }
  }
  adult.addEventListener("change", () => { change({ inspectVisual:adult.checked }); });
  for (const mode of modes) mode.addEventListener("change", () => { if (mode.checked) change({ personBlur:mode.value }); });
  byID("language").addEventListener("click", () => { arabic = !arabic; renderLanguage(); });
  retry.addEventListener("click", () => { load(); });
  const open = byID("openSettings");
  open.hidden = typeof api?.runtime?.openOptionsPage !== "function";
  open.addEventListener("click", async () => {
    try { await api.runtime.openOptionsPage(); }
    catch { saveState = "open-failed"; renderSaved(); }
  });
  renderLanguage();
  schedule?.(() => {
    if (root.visibilityState !== "hidden" && ready && !saving && !loading && (settings.inspectVisual || settings.personBlur !== "off")) refreshStatus();
  }, 5000);
  root.addEventListener("visibilitychange", () => { if (root.visibilityState !== "hidden") refreshStatus(); });
  return { load, refreshStatus };
}

const api = globalThis.browser ?? globalThis.chrome;
if (typeof document !== "undefined") {
  createOptionsController({ api, root:document,
    language:api?.i18n?.getUILanguage?.() ?? globalThis.navigator?.language ?? "en" }).load();
}
