// Every string the site shows, in English and Arabic. The vault is offline and
// account-free, so the language is chosen from the browser and remembered
// locally — nothing is sent anywhere to pick it.

const en = {
  'brand': 'Hisn',
  'vault': 'Vault',
  'lang': 'العربية',

  'start.title': 'A code you cannot take back',
  'start.lede': 'Seal a Screen Time passcode until the day you choose. It is not stored on this device or on any server — only inside an encrypted file you keep.',
  'start.make': 'Make a vault',
  'start.whatFor': 'What is the code for?',
  'start.screenTime': 'Screen Time (iPhone, iPad)',
  'start.macProfile': 'A Mac profile password',
  'start.other': 'Something else',
  'start.howLong': 'When does it open?',
  'start.day1': '1 day',
  'start.day7': '7 days',
  'start.day30': '30 days',
  'start.day90': '90 days',
  'start.custom': 'Another date',
  'start.howOpen': 'How can it open?',
  'start.modeBoth': 'The date, or a partner’s approval',
  'start.modeTime': 'Only when the date arrives',
  'start.modePartner': 'Only with a partner’s approval',
  'start.partnerKey': 'Partner public key',
  'start.partnerHint': 'Paste the key your partner saved on their device. It starts with HISN-PK-.',
  'start.getPartnerKey': 'How a partner gets a key',
  'start.understand': 'I understand this cannot be undone early without the partner key, and that Hisn cannot recover the code.',
  'start.importConfirm': 'Replace the vault on this device with this file?',
  'start.notice': 'Hisn cannot recover this code. Before the date only your partner can release it; after the date the drand beacon can. If you lose the vault file, it is gone.',
  'start.create': 'Create the vault',
  'start.import': 'Open an existing vault',
  'start.importHint': 'Choose the .json file you saved when you made it.',
  'start.importButton': 'Open file',
  'start.ready': 'Ready',

  'setup.title': 'Set it now',
  'setup.lede': 'This is the only time the code is shown. It hides in {time}.',
  'setup.copy': 'Copy',
  'setup.copied': 'Copied',
  'setup.seal': 'I have set it — seal now',
  'setup.download': 'Download the vault file',
  'setup.downloadHint': 'Do this before the window closes. The file is the only copy.',
  'setup.steps': 'In Settings',
  'setup.step1': 'Screen Time → Use Screen Time Passcode',
  'setup.step2': 'Enter the code above',
  'setup.step3': 'Content & Privacy → App Store, Media, Web & Games → Deleting Apps → Don’t Allow',
  'setup.step4': 'Content & Privacy → Web Content → Limit Adult Websites',
  'setup.generic': 'Store the code where the lock needs it, then seal the vault.',
  'setup.hideNote': 'After the window the code is hidden until the date.',

  'sealed.title': 'Sealed',
  'sealed.opensOn': 'Opens {date}',
  'sealed.remaining': '{d} days, {h} hours left',
  'sealed.timePath': 'Time lock',
  'sealed.timeDetail': 'drand round {round} — the beacon exists only after the date.',
  'sealed.partnerPath': 'Partner',
  'sealed.partnerDetail': 'They can release it at any time.',
  'sealed.open': 'Open the vault',
  'sealed.openEarly': 'Before the date, opening only works with your partner’s release key.',
  'sealed.request': 'Get a release from your partner',
  'sealed.requestHint': 'Send them this request. They send back a line starting with HISN-VK-.',
  'sealed.copyRequest': 'Copy request',
  'sealed.releaseKey': 'Release key from your partner',
  'sealed.releasePlaceholder': 'HISN-VK-…',
  'sealed.openWithKey': 'Open with this key',
  'sealed.download': 'Download the vault file',
  'sealed.forget': 'Forget this device',
  'sealed.forgetHint': 'Removes the local copy. Your downloaded file still opens it.',
  'sealed.forgetConfirm': 'Remove this vault from this browser? Your downloaded file is the only other copy.',

  'interrupted.title': 'The code was not saved',
  'interrupted.body': 'This page reloaded during the setup window. The code was never written to disk, so it cannot be shown again. If you did not finish setting it, discard this vault and make a new one — nothing is locked yet.',
  'interrupted.discard': 'Discard this vault',
  'interrupted.keep': 'Keep it — it opens on the date',

  'due.title': 'The date has passed',
  'due.lede': 'The beacon for this vault exists now. Opening needs a connection to drand.',
  'due.open': 'Open now',
  'due.offline': 'No beacon. Check your connection and try again.',

  'revealed.title': 'Open',
  'revealed.code': 'The code',
  'revealed.close': 'Close',
  'revealed.new': 'Start a new vault',
  'revealed.newConfirm': 'Start over? This replaces the vault on this device. The old file still works.',

  'error.date': 'Choose a date in the future.',
  'error.partner': 'That partner key is not valid.',
  'error.file': 'That file is not a Hisn vault.',
  'error.notYet': 'The date has not come yet.',
  'error.offline': 'Could not reach the beacon. Try again with a connection.',
  'error.badKey': 'That release key does not open this vault.',
  'error.noPartner': 'This vault has no partner path.',
  'error.noTime': 'This vault has no time path.',
  'error.generic': 'Something went wrong. Nothing was changed.',

  'footer.note': 'No account, no server, no analytics. The vault file is yours.',
  'footer.partner': 'Partner key',

  'partner.pill': 'Partner',
  'partner.footer': 'No account, no server, no analytics. Keep a backup of your key.',
  'partner.title': 'Partner key',
  'partner.lede': 'You hold the second key. You can release the vault early; you never see the code.',
  'partner.empty': 'You do not have a key on this device yet. Make one, save a backup, and give the public half to the person.',
  'partner.create': 'Create my key',
  'partner.fingerprint': 'Fingerprint',
  'partner.publicKey': 'Public key — give this to the person',
  'partner.copy': 'Copy',
  'partner.backup': 'Save a backup',
  'partner.restore': 'Restore a backup',
  'partner.replace': 'Replace the key',
  'partner.replaceConfirm': 'Replace this key? Requests made for the old key cannot be opened any more.',
  'partner.requestTitle': 'Release a vault',
  'partner.requestHint': 'Paste the request they sent you.',
  'partner.requestButton': 'Open the request',
  'partner.releaseTitle': 'Release key',
  'partner.releaseHint': 'Send this line back. It does not show them your key or reveal their code to you.',
  'partner.noKey': 'Create a key first.',
  'partner.badRequest': 'That is not a request for this key.',
  'partner.created': 'Key created. Save a backup before you close this page.',
  'partner.restored': 'Key restored.',
  'partner.copied': 'Copied',
};

const ar = {
  'brand': 'حصن',
  'vault': 'الخزنة',
  'lang': 'English',

  'start.title': 'رمز لا يمكنك استعادته',
  'start.lede': 'اختم رمز وقت الشاشة حتى اليوم الذي تختاره. لا يُحفظ على هذا الجهاز ولا على أي خادم — فقط داخل ملف مشفّر تحتفظ به.',
  'start.make': 'أنشئ خزنة',
  'start.whatFor': 'لِمَ هذا الرمز؟',
  'start.screenTime': 'وقت الشاشة (آيفون، آيباد)',
  'start.macProfile': 'كلمة سر ملف تعريف على الماك',
  'start.other': 'شيء آخر',
  'start.howLong': 'متى تُفتح؟',
  'start.day1': '١ يوم',
  'start.day7': '٧ أيام',
  'start.day30': '٣٠ يومًا',
  'start.day90': '٩٠ يومًا',
  'start.custom': 'تاريخ آخر',
  'start.howOpen': 'كيف تُفتح؟',
  'start.modeBoth': 'التاريخ، أو موافقة شريك',
  'start.modeTime': 'فقط عند حلول التاريخ',
  'start.modePartner': 'فقط بموافقة شريك',
  'start.partnerKey': 'المفتاح العام للشريك',
  'start.partnerHint': 'الصق المفتاح الذي حفظه شريكك على جهازه. يبدأ بـ HISN-PK-.',
  'start.getPartnerKey': 'كيف يحصل الشريك على مفتاح؟',
  'start.understand': 'I understand this cannot be undone early without the partner key, and that Hisn cannot recover the code.',
  'start.importConfirm': 'Replace the vault on this device with this file?',
  'start.understand': 'أفهم أن الفتح المبكر لا يحدث إلا بمفتاح الشريك، وأن حصن لا يستطيع استعادة الرمز.',
  'start.importConfirm': 'أتريد استبدال الخزنة على هذا الجهاز بهذا الملف؟',
  'start.notice': 'لا يستطيع حصن استعادة هذا الرمز. قبل التاريخ لا يفتحه إلا شريكك، وبعده يفتحه شعاع drand. إن فقدت ملف الخزنة فقدته.',
  'start.create': 'أنشئ الخزنة',
  'start.import': 'افتح خزنة موجودة',
  'start.importHint': 'اختر ملف ‎.json الذي حفظته عند الإنشاء.',
  'start.importButton': 'افتح الملف',
  'start.ready': 'جاهز',

  'setup.title': 'اضبطه الآن',
  'setup.lede': 'هذه المرة الوحيدة التي يظهر فيها الرمز. يختفي بعد {time}.',
  'setup.copy': 'نسخ',
  'setup.copied': 'تم النسخ',
  'setup.seal': 'أدخلته — اختم الآن',
  'setup.download': 'نزّل ملف الخزنة',
  'setup.downloadHint': 'افعل ذلك قبل انتهاء المهلة. الملف هو النسخة الوحيدة.',
  'setup.steps': 'في الإعدادات',
  'setup.step1': 'وقت الشاشة ← استخدام رمز وقت الشاشة',
  'setup.step2': 'أدخل الرمز أعلاه',
  'setup.step3': 'المحتوى والخصوصية ← متجر التطبيقات والوسائط والويب والألعاب ← حذف التطبيقات ← لا تسمح',
  'setup.step4': 'المحتوى والخصوصية ← محتوى الويب ← تقييد المواقع الإباحية',
  'setup.generic': 'احفظ الرمز حيث يحتاجه القفل، ثم اختم الخزنة.',
  'setup.hideNote': 'بعد المهلة يختفي الرمز حتى التاريخ.',

  'sealed.title': 'مختومة',
  'sealed.opensOn': 'تُفتح في {date}',
  'sealed.remaining': 'باقٍ {d} يومًا و{h} ساعة',
  'sealed.timePath': 'قفل زمني',
  'sealed.timeDetail': 'جولة drand رقم {round} — لا يوجد الشعاع قبل التاريخ.',
  'sealed.partnerPath': 'الشريك',
  'sealed.partnerDetail': 'يستطيع فتحها في أي وقت.',
  'sealed.open': 'افتح الخزنة',
  'sealed.openEarly': 'قبل التاريخ لا ينجح الفتح إلا بمفتاح إفراج من شريكك.',
  'sealed.request': 'اطلب إفراجًا من شريكك',
  'sealed.requestHint': 'أرسل له هذا الطلب. سيعيد لك سطرًا يبدأ بـ HISN-VK-.',
  'sealed.copyRequest': 'انسخ الطلب',
  'sealed.releaseKey': 'مفتاح الإفراج من شريكك',
  'sealed.releasePlaceholder': 'HISN-VK-…',
  'sealed.openWithKey': 'افتح بهذا المفتاح',
  'sealed.download': 'نزّل ملف الخزنة',
  'sealed.forget': 'انسَ هذا الجهاز',
  'sealed.forgetHint': 'يحذف النسخة المحلية. ملفك المنزّل ما زال يفتحها.',
  'sealed.forgetConfirm': 'أتريد حذف هذه الخزنة من هذا المتصفح؟ ملفك المنزّل هو النسخة الأخرى الوحيدة.',

  'interrupted.title': 'لم يُحفظ الرمز',
  'interrupted.body': 'أُعيد تحميل الصفحة أثناء مهلة الإعداد. لم يُكتب الرمز على القرص، لذا لا يمكن عرضه مجددًا. إن لم تُكمل ضبطه، احذف هذه الخزنة وأنشئ غيرها — لم يُقفل شيء بعد.',
  'interrupted.discard': 'احذف هذه الخزنة',
  'interrupted.keep': 'أبقها — تُفتح في التاريخ',

  'due.title': 'حلّ التاريخ',
  'due.lede': 'شعاع هذه الخزنة موجود الآن. الفتح يحتاج اتصالًا بـ drand.',
  'due.open': 'افتح الآن',
  'due.offline': 'لا شعاع. تحقّق من الاتصال وحاول مجددًا.',

  'revealed.title': 'مفتوحة',
  'revealed.code': 'الرمز',
  'revealed.close': 'إغلاق',
  'revealed.new': 'أنشئ خزنة جديدة',
  'revealed.newConfirm': 'أتريد البدء من جديد؟ سيستبدل الخزنة على هذا الجهاز. الملف القديم ما زال يعمل.',

  'error.date': 'اختر تاريخًا في المستقبل.',
  'error.partner': 'هذا المفتاح العام غير صالح.',
  'error.file': 'هذا الملف ليس خزنة حصن.',
  'error.notYet': 'لم يحل التاريخ بعد.',
  'error.offline': 'تعذّر الوصول إلى الشعاع. حاول مجددًا مع اتصال.',
  'error.badKey': 'مفتاح الإفراج هذا لا يفتح الخزنة.',
  'error.noPartner': 'لا مسار شريك لهذه الخزنة.',
  'error.noTime': 'لا قفل زمني لهذه الخزنة.',
  'error.generic': 'حدث خطأ. لم يتغيّر شيء.',

  'footer.note': 'لا حساب، لا خادم، لا تتبّع. ملف الخزنة ملكك.',
  'footer.partner': 'مفتاح الشريك',

  'partner.pill': 'الشريك',
  'partner.footer': 'لا حساب، لا خادم، لا تتبّع. احفظ نسخة من مفتاحك.',
  'partner.title': 'مفتاح الشريك',
  'partner.lede': 'أنت تحمل المفتاح الثاني. تستطيع الإفراج عن الخزنة مبكرًا؛ ولا ترى الرمز أبدًا.',
  'partner.empty': 'ليس لديك مفتاح على هذا الجهاز بعد. أنشئ واحدًا، واحفظ نسخة، وأعطِ النصف العام للشخص.',
  'partner.create': 'أنشئ مفتاحي',
  'partner.fingerprint': 'البصمة',
  'partner.publicKey': 'المفتاح العام — أعطه للشخص',
  'partner.copy': 'نسخ',
  'partner.backup': 'احفظ نسخة',
  'partner.restore': 'استعد نسخة',
  'partner.replace': 'استبدل المفتاح',
  'partner.replaceConfirm': 'أتريد استبدال المفتاح؟ الطلبات المصنوعة للمفتاح القديم لن تُفتح بعد الآن.',
  'partner.requestTitle': 'أفرج عن خزنة',
  'partner.requestHint': 'الصق الطلب الذي أرسله لك.',
  'partner.requestButton': 'افتح الطلب',
  'partner.releaseTitle': 'مفتاح الإفراج',
  'partner.releaseHint': 'أعد هذا السطر إليه. لا يُظهر مفتاحك ولا يكشف رمزه لك.',
  'partner.noKey': 'أنشئ مفتاحًا أولًا.',
  'partner.badRequest': 'هذا ليس طلبًا لهذا المفتاح.',
  'partner.created': 'أُنشئ المفتاح. احفظ نسخة قبل إغلاق الصفحة.',
  'partner.restored': 'استُعيد المفتاح.',
  'partner.copied': 'تم النسخ',
};

export const DICT = { en, ar };
export const LANGS = ['en', 'ar'];

const STORE_KEY = 'hisn.lang';

export function detectLang() {
  try {
    const saved = localStorage.getItem(STORE_KEY);
    if (saved && DICT[saved]) return saved;
  } catch { /* storage may be off */ }
  const nav = (navigator.language || 'en').toLowerCase();
  return nav.startsWith('ar') ? 'ar' : 'en';
}

let current = 'en';

export function setLang(lang) {
  current = DICT[lang] ? lang : 'en';
  try { localStorage.setItem(STORE_KEY, current); } catch { /* ignore */ }
  document.documentElement.lang = current;
  document.documentElement.dir = current === 'ar' ? 'rtl' : 'ltr';
  const button = document.querySelector('[data-lang-button]');
  if (button) button.textContent = t('lang');
  return current;
}

export function lang() {
  return current;
}

export function t(key, vars) {
  const table = DICT[current] || DICT.en;
  let text = table[key] ?? DICT.en[key] ?? key;
  if (vars) {
    for (const [name, value] of Object.entries(vars)) {
      text = text.split(`{${name}}`).join(String(value));
    }
  }
  return text;
}

/** Format a date in the chosen language. */
export function formatDate(ms) {
  return new Intl.DateTimeFormat(current === 'ar' ? 'ar' : 'en-GB', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(ms));
}

export function formatCountdown(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const two = (n) => String(n).padStart(2, '0');
  if (current === 'ar') return `${two(d)}:${two(h)}:${two(m)}:${two(s)}`;
  return `${two(d)}:${two(h)}:${two(m)}:${two(s)}`;
}
