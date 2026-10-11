const api = globalThis.browser ?? globalThis.chrome;
const arabic = (api.i18n?.getUILanguage?.() ?? navigator.language ?? "en").startsWith("ar");
const strings = {
  title: "طبقات الحماية", subtitle: "حصن لمتصفح سفاري", textTitle: "فحص النصوص", textBadge: "مضمّن",
  textDetail: "يفحص نصوص الصفحات ومنشورات X منفردة على الجهاز. اسمح لحصن بالوصول إلى المواقع التي تريد حمايتها في إعدادات سفاري.",
  visualTitle: "فحص بصري تجريبي", adultLabel: "إخفاء الوسائط المصنّفة كمحتوى إباحي", personLabel: "تمويه الوسائط ذات الوجوه المكتشفة",
  modeOff: "إيقاف", modeAll: "أي وجه مكتشف", modeFemale: "وجوه يُقدّر أنها أنثوية", modeMale: "وجوه يُقدّر أنها ذكورية",
  faceDetail: "كشف الوجوه ليس كشفًا لكامل الجسم. تقدير المظهر قد يخطئ ولا يعني أن المحتوى إباحي. عند التطابق تُموّه الصورة أو الفيديو بالكامل.",
  engineStatus: "الفحص البصري متوقف.", limitsTitle: "اعرف الحدود",
  limitsDetail: "يمكن فحص الصور القابلة للقراءة وعيّنات من إطارات الفيديو في صفحات سفاري المسموح بها فقط. بعض الوسائط المحمية أو القادمة من مصدر مختلف غير قابلة للقراءة؛ وهذا لا يعني أنها آمنة. لا تُخفى الفيديوهات العادية افتراضيًا.",
  privacyDetail: "النماذج مضمّنة. تُعالج الصور والإطارات على هذا الجهاز دون رفعها أو حفظها بواسطة حصن.",
  commitmentDetail: "هذه الإعدادات البصرية في سفاري منفصلة عن التزام التطبيق. يمكن تعطيل الإضافة من سفاري؛ هذه الصفحة لا تمنع حذفها ولا تدّعي حماية الجهاز كله.",
};
if (arabic) {
  document.documentElement.lang = "ar"; document.documentElement.dir = "rtl";
  for (const [id, value] of Object.entries(strings)) document.getElementById(id).textContent = value;
}
const adult = document.getElementById("inspectVisual"), person = document.getElementById("personBlur");
const status = document.getElementById("engineStatus"), saved = document.getElementById("saveStatus");
let settings = { inspectVisual: false, personBlur: "off" }, saving = false;
const say = (en, ar) => arabic ? ar : en;
function show(next) {
  settings = { inspectVisual: next?.inspectVisual === true,
    personBlur: ["all", "female", "male"].includes(next?.personBlur) ? next.personBlur : "off" };
  adult.checked = settings.inspectVisual; person.value = settings.personBlur;
  adult.disabled = person.disabled = saving;
}
async function refreshStatus() {
  try {
    const result = await api.runtime.sendMessage({ type: "getVisualStatus" });
    const enabled = settings.inspectVisual || settings.personBlur !== "off";
    const labels = {
      disabled: enabled ? say("Waiting for readable media. Models load only when needed.", "بانتظار وسائط قابلة للقراءة. تُحمّل النماذج عند الحاجة فقط.") : say("Visual checks are off.", "الفحص البصري متوقف."),
      loading: say("Loading bundled models on this device…", "يجري تحميل النماذج المضمّنة على الجهاز…"),
      waiting: say("Enabled. Waiting for readable media to load the selected models.", "مفعّل. بانتظار وسائط قابلة للقراءة لتحميل النماذج المختارة."),
      ready: say("Models ready. This does not mean every image or video was checked.", "النماذج جاهزة. هذا لا يعني أنه تم فحص كل صورة أو فيديو."),
      failed: say("Visual checks are unavailable. Text checking remains independent.", "الفحص البصري غير متاح. فحص النصوص مستقل عنه."),
      unavailable: say("Visual checks are unavailable in this Safari session.", "الفحص البصري غير متاح في جلسة سفاري هذه."),
    };
    status.textContent = labels[result?.state] ?? labels.unavailable;
  } catch { status.textContent = say("Could not reach the visual checker.", "تعذر الوصول إلى الفاحص البصري."); }
}
async function change(patch) {
  if (saving) return;
  saving = true; show(settings); saved.textContent = "";
  try {
    const result = await api.runtime.sendMessage({ type: "saveVisualSettings", patch });
    if (!result?.settings) throw new Error("Settings unavailable");
    show(result.settings);
    saved.textContent = say("Saved on this device.", "حُفظت على هذا الجهاز.");
  } catch { saved.textContent = say("Settings were not saved. Try again.", "لم تُحفظ الإعدادات. حاول مجددًا."); }
  finally { saving = false; show(settings); await refreshStatus(); }
}
adult.addEventListener("change", () => change({ inspectVisual: adult.checked }));
person.addEventListener("change", () => change({ personBlur: person.value }));
try {
  const result = await api.runtime.sendMessage({ type: "getVisualSettings" });
  if (result?.state === "unavailable" || typeof result?.inspectVisual !== "boolean") throw new Error("Unavailable settings");
  show(result); await refreshStatus();
} catch { saved.textContent = say("Could not load settings; visual controls remain unavailable.", "تعذر تحميل الإعدادات؛ الخيارات البصرية غير متاحة."); }
setInterval(() => { if (!saving && (settings.inspectVisual || settings.personBlur !== "off")) refreshStatus(); }, 5000);
