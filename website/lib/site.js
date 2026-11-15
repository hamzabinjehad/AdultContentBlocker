// The Hisn website's language layer.
//
// English is the text in index.html, so it stays readable without JavaScript and
// there is only one English copy to maintain. This module carries the Arabic
// translation for every data-i18n key and swaps the page between the two.
// There is no request, no storage beyond the language choice, and no analytics.

export const ar = {
  'meta.title': 'حصن — حماية من المحتوى الإباحي تبقى على هاتفك',
  'meta.description': 'حماية من المحتوى الإباحي لأندرويد، تدرك العربية. فلترة على الجهاز، وحجب تطبيقات، وفحوص شاشة. لا حساب، ولا سحابة، ولا اشتراك.',
  'skip': 'تخطَّ إلى المحتوى',

  'nav.label': 'التنقل الرئيسي',
  'nav.download': 'التنزيل',
  'nav.how': 'كيف يعمل',
  'nav.limits': 'الحدود',
  'nav.roadmap': 'خارطة الطريق',
  'nav.vault': 'الخزنة',
  'nav.privacy': 'الخصوصية',

  'hero.eyebrow': 'مجاني لأندرويد',
  'hero.title': 'المحتوى الإباحي، محجوب. بلا اشتراك.',
  'hero.lede': 'حصن تطبيق أندرويد مجاني يحجب المواقع الإباحية والتطبيقات التي تختارها وما يظهر على الشاشة. كل شيء يعمل على هاتفك.',
  'hero.download': 'احصل على حصن لأندرويد',
  'hero.limits': 'اقرأ الحدود أولًا',
  'hero.meta': 'مجاني · أندرويد 6.0+ · العربية والإنجليزية',
  'trust.account': 'لا حساب لإنشائه',
  'trust.cloud': 'لا تُرسل لقطات شاشة إلى سحابة',
  'trust.price': 'لا اشتراك ولا مؤقت تجربة',
  'trust.device': 'المطابقة تبقى على هذا الهاتف',

  'status.available': 'متاح',
  'status.dev': 'قيد التطوير',
  'card.signed': 'نسخة مباشرة موقّعة',
  'card.sub': 'رمز الإصدار 41 · 31.3 ميغابايت · أندرويد 6.0+',
  'card.size': 'الحجم',
  'card.android': 'أندرويد',
  'card.androidValue': '6.0 وما بعده (API 23+)',
  'card.sign': 'التوقيع',
  'card.signValue': 'مفتاح إصدار حصن',
  'card.download': 'نزّل ملف APK',
  'card.sha': 'SHA-256',
  'card.cert': 'بصمة الشهادة SHA-256',
  'card.sums': 'ملف المجاميع',
  'card.verify': 'كيف تتحقق',
  'card.play': 'ليس على Google Play بعد. النسخة المباشرة هي التي تُثبّت؛ نسخة Play غير مرفوعة.',
  'card.next': 'مصدر 1.6.0 قيد التطوير. النسخة الموقّعة 1.5.3 أعلاه هي أحدث إصدار قابل للتنزيل.',

  'how.eyebrow': 'ما الذي يعمل فعلًا',
  'how.title': 'أربع طبقات، وأربع حدود صادقة.',
  'how.lede': 'كل طبقة مستقلة ولها إذن أندرويد خاص. يُظهر حصن أيّها يعمل، وأيّها توقف، وما لا تستطيع رؤيته.',
  'how.note': 'لا تُباع أي طبقة كحماية كاملة. التطبيق وهذه الصفحة يذكران ما لا تستطيع كل طبقة فعله.',
  'layer.dns.title': 'فلترة المواقع',
  'layer.dns.body': 'شبكة VPN خاصة بـ DNS تحجب النطاقات الإباحية قبل تحميل الصفحة، في كل متصفح وتطبيق يستخدم DNS الهاتف.',
  'layer.dns.scope': 'النطاق: عمليات بحث الأسماء، لا الاتصال المباشر بعنوان IP، ولا الإجابات المخزّنة، ولا التطبيقات التي تستخدم DNS مشفّرًا خاصًا بها.',
  'layer.apps.title': 'حماية التطبيقات',
  'layer.apps.body': 'أغلق التطبيقات التي تختارها واقرأ النص الظاهر فيها على هذا الهاتف عبر خدمة تسهيلات الاستخدام في أندرويد.',
  'layer.apps.scope': 'النطاق: التطبيقات التي تختارها. تُستثنى شاشات النظام ولوحات المفاتيح وحقول كلمات المرور.',
  'layer.words.title': 'الكلمات والصور الإباحية',
  'layer.words.body': 'كلمات عربية وإنجليزية، وقواعدك الحرفية، وفحص صور اختياري على الجهاز لا يرفع أي إطار.',
  'layer.words.scope': 'النطاق: فحص الصور يحتاج أندرويد 14+؛ فحص النص يعمل بعد ظهور المحتوى. لا تُضمن كل مطابقة.',
  'layer.focus.title': 'الجداول والحدود',
  'layer.focus.body': 'ساعات هادئة، وحدود يومية لوقت التطبيقات، ووقفة تنفّس بدل شاشة عتاب. يُقرأ الاستخدام من سجل أندرويد.',
  'layer.focus.scope': 'النطاق: يُطبَّق ما دامت خدمة تسهيلات الاستخدام متصلة؛ ويستطيع أندرويد إيقاف أي تطبيق في الخلفية.',

  'phone.eyebrow': 'مصمّم لهاتفك',
  'phone.title': 'سامسونج وشاومي وناثينغ وأندرويد القياسي ليست الهاتف نفسه.',
  'phone.lede': 'تُنيم سامسونج التطبيقات في «العناية بالجهاز». وتخفي شاومي مفتاح البدء التلقائي. ويتبع Nothing OS أندرويد القياسي. يقرأ حصن 1.6.0 الشركة المصنّعة، ويفحص الإعدادات التي تحدّد بقاء الحماية بعد إطفاء الشاشة، ويفتح الشاشة الصحيحة بدل نصيحة بطارية عامة.',
  'phone.samsung': 'بطارية غير مقيّدة، وخروج من قائمة التطبيقات النائمة، وملاحظة «الحظر التلقائي» للتحديث اليدوي.',
  'phone.xiaomiTitle': 'شاومي / ريدمي / بوكو',
  'phone.xiaomi': 'البدء التلقائي، والبطارية، وقفل التطبيقات الأخيرة، مع شاشة MIUI المحددة أو الرجوع الآمن إلى معلومات التطبيق.',
  'phone.nothingTitle': 'ناثينغ / أندرويد القياسي',
  'phone.nothing': 'مفاتيح البطارية والخلفية في أندرويد فقط؛ لا خطوات مصنّعة.',
  'phone.note': 'فحص الهاتف هذا يأتي في نسخة 1.6.0 التطويرية، لا في تنزيل 1.5.3. تطبيق 1.5.3 يعمل مع ذلك على كل هذه الهواتف.',
  'phone.caption': 'نسخة 1.6.0 التطويرية: فحص الهاتف على محاكي أندرويد بهوية شاومي. لم تكن شاشات MIUI موجودة، لذلك ما عمل هو مسار الرجوع.',

  'diff.eyebrow': 'مختلف بالتصميم',
  'diff.title': 'غياب الحساب هو الميزة.',
  'diff.lede': 'معظم الحاجبات تطلب حسابًا أو اشتراكًا أو شخصًا يراجع لقطات الشاشة. حصن لا يطلب أيًا منها. هذا خيار خصوصية، وهو أيضًا سبب وجود الحدود على هذه الصفحة.',
  'diff.account.title': 'لا حساب',
  'diff.account.body': 'لا تسجيل دخول، ولا مزامنة، ولا بريد أو رقم هاتف قبل أول إعداد.',
  'diff.cloud.title': 'لا لقطات سحابية',
  'diff.cloud.body': 'لا صندوق لشخص آخر، ولا طابور مراجعة، ولا لقطات شاشة تترك الهاتف.',
  'diff.price.title': 'لا اشتراك',
  'diff.price.body': 'لا مؤقت تجربة ولا جدار دفع بعد الإعداد. حصن مجاني.',
  'diff.arabic.title': 'العربية أولًا',
  'diff.arabic.body': 'الواجهة ومطابق الكلمات الإباحية والموقع ثنائية اللغة، لا تطبيق إنجليزي بطبقة ترجمة.',
  'diff.limits.title': 'الحدود منشورة',
  'diff.limits.body': 'تُسمّى طرق التجاوز على هذه الصفحة وفي التطبيق، قبل أن تثق به في أي شيء.',
  'diff.hashes.title': 'تنزيل قابل للتحقق',
  'diff.hashes.body': 'SHA-256 وشهادة الإصدار مكتوبان بجانب APK، لا مخفيان في صفحة متجر.',

  'limits.eyebrow': 'الحدود',
  'limits.title': 'ما لا يفعله حصن.',
  'limits.lede': 'الحاجب الذي يخفي ثغراته وعد كاذب. هذه ثغرات هذه النسخة، وتُكرّرها صفحة داخل التطبيق.',
  'limits.force.title': 'الصمود أمام الإيقاف القسري',
  'limits.force.body': 'الإيقاف القسري من إعدادات أندرويد يغلب. يستطيع المراقب ملاحظة توقف طبقة؛ ولا يستطيع تطبيق عادي إعادة تشغيل نفسه من هناك.',
  'limits.space.title': 'تغطية المساحة الثانية',
  'limits.space.body': 'المساحة الثانية أو المجلد الآمن أو التطبيق المزدوج ملف تعريف أندرويد منفصل. يجب تثبيت حصن فيه أيضًا.',
  'limits.vpn.title': 'التغلب على VPN آخر',
  'limits.vpn.body': 'يسمح أندرويد بـ VPN واحد في الوقت نفسه. شبكة VPN أخرى أو Private DNS أو تطبيق يستخدم DNS مشفّرًا خاصًا به قد يتجاوز طبقة DNS.',
  'limits.ip.title': 'رؤية ما بعد عنوان IP المباشر',
  'limits.ip.body': 'فلترة DNS ترى الأسماء، لا الاتصال المباشر بعنوان IP، ولا الإجابات المخزّنة، ولا المحتوى داخل صفحة مشفّرة.',
  'limits.os.title': 'استبدال نظام التشغيل',
  'limits.os.body': 'قفل الحماية يجمّد القواعد داخل حصن. لا يوقف إعدادات أندرويد، ولا محو البيانات، ولا سياسة جهاز مُدار تُضبط على حدة.',
  'limits.guarantee.title': 'ضمان كل صورة',
  'limits.guarantee.body': 'فحص الصور لأندرويد 14+ ويعمل بعد ظهور المحتوى. لا مصنّف يلتقط كل إطار، ولا يجوز بيعه كيقين.',
  'limits.note': 'اقرأ صفحة الخصوصية لمعرفة ما يبقى على الهاتف وما يغادره.',

  'install.eyebrow': 'ابدأ',
  'install.title': 'من التنزيل إلى الحماية في أربع خطوات.',
  'install.lede': 'ثبّته من هذه الصفحة. سيطلب أندرويد إذن تثبيت واحدًا وحمايتين؛ لا شيء هنا يحتاج حاسوبًا.',
  'install.s1.title': 'تحقّق من الملف',
  'install.s1.body': 'قارن SHA-256 أعلاه بالملف قبل التثبيت. على حاسوب:',
  'install.s2.title': 'اسمح بهذا التثبيت فقط',
  'install.s2.body': 'افتح APK واسمح بالتثبيت من المتصفح أو مدير الملفات الذي استخدمته. أعد إيقاف هذا الإذن بعد ذلك.',
  'install.s3.title': 'شغّل الطبقتين الأساسيتين',
  'install.s3.body': 'حماية التطبيقات تفتح شاشة تسهيلات الاستخدام في أندرويد؛ فلترة المواقع تعرض إفصاح DNS ثم مطالبة VPN.',
  'install.s4.title': 'شغّل فحص الهاتف',
  'install.s4.body': 'في 1.6.0، افتح الإعدادات ← إبقاء الحماية تعمل حتى لا ينيم أندرويد حصن. في 1.5.3، اجعل استخدام البطارية «غير مقيّد» من إعدادات أندرويد.',
  'install.samsung': 'إذا منع «الحظر التلقائي» التثبيت، أوقفه من الإعدادات ← الأمان والخصوصية، وثبّت، ثم أعد تشغيله.',
  'install.xiaomiTitle': 'شاومي / ريدمي / بوكو',
  'install.xiaomi': 'اسمح بالتثبيت من مدير الملفات أو المتصفح، ثم فعّل البدء التلقائي لحصن ليبقى يعمل بعد إغلاقه.',
  'install.nothingTitle': 'ناثينغ / أندرويد القياسي',
  'install.nothing': 'لا شاشة مصنّعة إضافية. اجعل بطارية حصن «غير مقيّدة» ليبقى يفحص في الخلفية.',
  'install.note': 'يمكنك إزالة حصن مثل أي تطبيق آخر. إذا كان قفل الحماية مفعّلًا، أوقفه أولًا.',

  'road.eyebrow': 'خارطة الطريق',
  'road.title': 'المزيد من المنصات قادمة.',
  'road.lede': 'حصن متاح على أندرويد اليوم. التطبيقات الأخرى قيد العمل؛ سنعلن النسخة «قريبًا» حتى تصبح قابلة للتثبيت.',
  'road.androidNow.body': 'التطبيق الذي يمكنك تثبيته اليوم.',
  'road.androidNow.meta': 'مجاني · أندرويد 6.0 وما بعده',
  'road.androidNext.title': 'تحديث جديد لأندرويد',
  'road.androidNext.body': 'فحص الهاتف ومظهر جديد.',
  'road.androidNext.meta': 'لا تاريخ بعد',
  'road.mac.body': 'فلتر محتوى وحارس متصفح لسفاري وكروم وبرايف.',
  'road.mac.meta': 'لا تاريخ بعد',
  'road.ios.title': 'آيفون وآيباد',
  'road.ios.body': 'فلتر DNS على مستوى الجهاز وحاجبات سفاري، مع القفل في وقت الشاشة.',
  'road.ios.meta': 'لا تاريخ بعد',
  'road.ext.title': 'إضافة المتصفح',
  'road.ext.body': 'حماية في متصفحات Chromium.',
  'road.ext.meta': 'لا تاريخ بعد',
  'road.vault.title': 'خزنة حصن',
  'road.vault.body': 'قفل خارج الجهاز.',
  'road.vault.link': 'افتح الخزنة',
  'road.note': 'لا تواريخ لا نستطيع الوفاء بها. تبقى النسخة «قريبًا» حتى يوجد شيء قابل للتثبيت.',

  'faq.eyebrow': 'أسئلة',
  'faq.title': 'معلومات مفيدة.',
  'faq.q1': 'هل حصن مجاني؟',
  'faq.a1': 'نعم. لا حساب، ولا اشتراك، ولا شراء داخل التطبيق.',
  'faq.q2': 'ما الذي يغادر هاتفي؟',
  'faq.a2': 'تحديث قائمة الحظر وأسماء DNS التي يبحث عنها هاتفك أصلًا. لا شيء آخر. صفحة الخصوصية تسرد كل طلب.',
  'faq.q3': 'هل يعمل على سامسونج وشاومي وناثينغ؟',
  'faq.a3': 'نعم. يعمل على الثلاثة ويوضح إعدادات الهاتف التي تحتاج تغييرًا.',
  'faq.q4': 'هل يحجب كل شيء؟',
  'faq.a4': 'لا حاجب يحجب كل شيء.',
  'faq.q5': 'هل أستطيع إزالته؟',
  'faq.a5': 'نعم، مثل أي تطبيق آخر. إذا كان قفل الحماية مفعّلًا، أوقفه أولًا. الإيقاف القسري أو المساحة الثانية أو VPN آخر قد يتجاوز الطبقات؛ لهذا الحدود على هذه الصفحة.',
  'faq.q6': 'هل يرسل لقطات شاشة لأحد؟',
  'faq.a6': 'لا. لا صندوق مساءلة ولا مراجعة سحابية. الربط الاختياري بولي أمر يضيف قواعد، لكنه لا يستقبل شاشتك أو سجل تصفحك.',

  'footer.label': 'تذييل',
  'footer.note': 'لا حساب، ولا خادم، ولا تحليلات. المطابقة تبقى على الهاتف.',
  'footer.source': 'المصدر والسجلات',

  'privacy.eyebrow': 'الخصوصية',
  'privacy.title': 'ما يبقى على الهاتف.',
  'privacy.lede': 'بُني حصن بحيث تكون البيانات الروتينية الوحيدة التي تغادر هاتفك هي تحديث قائمة الحظر وعمليات DNS التي يجريها الهاتف أصلًا. لا حساب، ولا حزمة تحليلات، ولا إعلانات، ولا قياس عن بُعد.',
  'privacy.stays.title': 'يبقى على الهاتف',
  'privacy.stays.body': 'القواعد والتطبيقات المختارة والجدول والحدود وموعد الحماية ونسخة القائمة المثبّتة وحالة الصحة المحلية وعدد الوقفات تعيش في تخزين حصن الخاص. وعلى أندرويد، يبقى الاسم والسبب الاختياريان اللذان تكتبهما لنفسك هناك أيضًا. لا شيء من ذلك يُرفع أو يُصدّر مع قواعدك أو يُدرج في التشخيصات.',
  'privacy.stays.screen': 'نص الشاشة والصور وإجابات DNS تُعالج على الجهاز ولا تُحفظ أو تُرسل.',
  'privacy.leaves.title': 'يغادر الهاتف',
  'privacy.leaves.update': 'تحديث قائمة الحظر — يطلب نسخة أحدث من القائمة. يرى الخادم الطلب ووقته، ولا يعرف ما تتصفحه.',
  'privacy.leaves.dns': 'عمليات DNS، إذا فعّلت الفلترة — يرى الخادم الذي ضبطته الأسماء التي يحلّها هاتفك، كما كان سيرى بدون حصن.',
  'privacy.leaves.time': 'وقت آبل — على آيفون/آيباد فقط، لقفل الوقت وكشف الخزنة. يقرأ حصن التاريخ فقط.',
  'privacy.leaves.partner': 'طلب مساءلة — فقط إذا ربطت شريكًا؛ يقول إن طلبًا جرى ومتى، ولا يقول أبدًا أي موقع زرته.',
  'privacy.leaves.note': 'قد يجمع مزوّد الإنترنت أو الشبكة أو جهة العمل أو المنصة بيانات وفق سياساتها. لا يستطيع حصن تغيير ذلك.',
  'privacy.permissions.title': 'الصلاحيات، ولماذا',
  'privacy.permissions.body': 'فلترة المواقع تستخدم خدمة VPN في أندرويد لـ DNS فقط؛ وحماية التطبيقات تستخدم خدمة تسهيلات الاستخدام لقراءة النص الظاهر وإغلاق التطبيقات التي اخترتها؛ وحدود الوقت اليومية تقرأ سجل الاستخدام في أندرويد؛ وشاشة الوقفة ودرع الإقلاع الاختياري يستخدمان إذن العرض فوق التطبيقات. التنبيهات لإشعارات صحة الطبقات. كل طبقة اختيارية إلا التي تختار تشغيلها.',
  'privacy.site.title': 'هذا الموقع',
  'privacy.site.body': 'لا تحليلات، ولا ملفات تعريف ارتباط، ولا سكربت من طرف ثالث. يبقى اختيار اللغة في هذا المتصفح. الخزنة تحفظ ملفها عندك؛ ومسار قفل الوقت يقرأ خدمة وقت عامة.',
  'privacy.control.title': 'تحكّمك',
  'privacy.control.body': 'إزالة تثبيت حصن تحذف قواعده وإعداداته وطبقتك الشخصية المحلية. ومسح بيانات التطبيق في أندرويد يفعل الشيء نفسه. حارس الحذف الاختياري موجود فقط أثناء قفل حماية نشط، ولا يمنع محو البيانات أو سياسة جهاز مُدار.',
  'privacy.contact.title': 'أسئلة',
  'privacy.contact.body': 'تغطي هذه الصفحة الموقع والتطبيق. تعيد صفحة الإعدادات ← الخصوصية في التطبيق النسخة المختصرة، ولكل إذن سطر واحد يشرح لماذا يُطلب قبل تشغيله.',
  'privacy.full': 'اقرأ سياسة الخصوصية الكاملة',
  'privacy.back': 'العودة إلى صفحة التنزيل',
  'proof.account': 'لا حساب',
  'proof.cloud': 'لا سحابة',
  'proof.price': 'لا اشتراك',
  'proof.domains': 'أكثر من 236,000 نطاق',
  'hero.secondary': 'شاهد كيف يعمل',
  'hero.platformsLabel': 'منصات أخرى',
  'status.soon': 'قريبًا',
  'platform.iphone': 'آيفون',
  'platform.browser': 'المتصفح',
  'nav.get': 'احصل على التطبيق',
  'float.tag': '‏236,000+ موقع محجوب',
  'float.title': 'تطبيق واحد. أربع حمايات.',
  'float.dns': 'المواقع',
  'float.apps': 'التطبيقات والنص',
  'float.checks': 'الصور',
  'float.note': 'كل طبقة لها إذن أندرويد وحدود خاصة.',
  'stats.label': 'حصن بالأرقام',
  'stats.domains': 'مواقع تُحجب قبل تحميلها',
  'stats.layers': 'طبقات حماية مستقلة',
  'stats.accounts': 'حسابات لإنشائها',
  'stats.subs': 'اشتراكات أو تجارب',
  'benefit.eyebrow': 'ما الذي يتغيّر',
  'benefit.title': 'حماية حيث يحدث الأمر.',
  'benefit.lede': 'ليست مفتاحًا واحدًا يدّعي كل شيء. أربع طبقات تلتقط أشياء مختلفة، وفحص هاتف يُبقيها حيّة على الهاتف الذي تملكه فعلًا.',
  'benefit.one.title': 'قبل تحميل الصفحة',
  'benefit.one.body': 'فلتر DNS يحجب المواقع الإباحية في كل متصفح قبل رسم أول بكسل.',
  'benefit.one.scope': 'كل متصفح · كل تطبيق يستخدم DNS الهاتف',
  'benefit.two.title': 'داخل التطبيقات التي تختارها',
  'benefit.two.body': 'احجب تطبيقًا كاملًا، أو اقرأ نصه الظاهر وطابق الكلمات الإباحية العربية والإنجليزية على الهاتف.',
  'benefit.two.scope': 'التطبيقات المختارة · الفحوص تعمل بعد ظهور المحتوى',
  'benefit.three.title': 'بعد إطفاء الشاشة',
  'benefit.three.body': 'سامسونج وشاومي وناثينغ تنيم التطبيقات بطرق مختلفة. يجد حصن إعدادات البطارية والخلفية والبدء التلقائي التي تُبقيه حيًّا.',
  'benefit.three.scope': 'فحص الهاتف · قريبًا',
  'card.latest': 'أحدث إصدار موقّع',
  'step.one.title': 'نزّل',
  'step.one.body': 'افتح الملف.',
  'step.two.title': 'فعّل',
  'step.two.body': 'الحماية والفلترة.',
  'step.three.title': 'خصّصه',
  'step.three.body': 'تطبيقات ومواقع وأوقات.',
  'step.four.title': 'شغّل فحص الهاتف',
  'step.four.body': 'على سامسونج وشاومي وناثينغ، يجد حصن إعدادات البطارية والبدء التلقائي التي تُبقيه يعمل.',
  'final.title': 'ابدأ بتطبيق أندرويد المجاني.',
  'final.body': 'لا حساب. لا سحابة. لا اشتراك.',
  'final.note': 'مجاني · أندرويد 6.0 وما بعده.',
  'download.title': 'احصل على حصن.',
  'download.meta': 'أندرويد 6.0 وما بعده · العربية والإنجليزية',
  'download.button': 'نزّل حصن',
  'download.note': 'إذا طلب أندرويد، اسمح بالتثبيت من هذا المتصفح مرة واحدة.',
  'download.includes': 'ما تحصل عليه',
  'download.one': 'فلترة المواقع في كل متصفح',
  'download.two': 'حجب التطبيقات كاملة وفحص النص الظاهر',
  'download.three': 'جداول وحدود يومية ووقفة تنفّس',
  'download.four': 'العربية والإنجليزية، على هذا الهاتف',
  'features.eyebrow': 'الميزات',
  'features.title': 'ماذا يفعل حصن.',
  'features.lede': 'أربع حمايات، كلها على هاتفك.',
  'feature.one.title': 'يحجب المواقع الإباحية',
  'feature.one.body': 'في كل متصفح، قبل تحميل الصفحة.',
  'feature.two.title': 'يحجب التطبيقات والنص',
  'feature.two.body': 'اختر التطبيقات؛ يطابق كلمات عربية وإنجليزية.',
  'feature.three.title': 'يفحص الصور',
  'feature.three.body': 'على الجهاز، في أندرويد 14 وما بعده.',
  'feature.four.title': 'يضبط حدودك',
  'feature.four.body': 'جداول وحدود يومية ووقفة تنفّس.',
  'gallery.eyebrow': 'شاهده',
  'gallery.title': 'ثلاث شاشات، تطبيق واحد.',
  'gallery.one': 'الحماية',
  'gallery.two': 'القواعد',
  'gallery.three': 'فحص الهاتف',
  'float.limits': 'الوقت والحدود',
  'nav.features': 'الميزات',
  'download.eyebrow': 'مجاني لأندرويد',
  'download.lede': 'أندرويد 6.0 وما بعده. العربية والإنجليزية.',
  'phone.line': 'يعمل على سامسونج وشاومي وناثينغ وأندرويد القياسي.',
  'private.label': 'الخصوصية',
  'private.account.title': 'بلا حساب',
  'private.account.body': 'لا شيء لتسجيل الدخول إليه.',
  'private.cloud.title': 'بلا لقطات في السحابة',
  'private.cloud.body': 'لا شيء يُرسل إلى شخص غريب.',
  'private.price.title': 'بلا اشتراك',
  'private.price.body': 'مجاني، بلا مؤقت تجربة.',
  'road.apple.title': 'ماك وآيفون',
  'road.apple.body': 'فلتر محتوى وحارس متصفح وDNS.',
  'faq.limits': 'اقرأ الحدود',
  'faq.a4end': 'قبل التثبيت.',
  'limits.back': 'العودة إلى التطبيق',
  'fact.list.label': 'في قائمة الحظر',
  'fact.list.value': '‏236,000+ موقع',
  'fact.private.label': 'على هاتفك',
  'fact.private.value': 'بلا حساب ولا سحابة',
  'platform.mac': 'ماك',
  'platform.linux': 'لينكس',
  'platform.windows': 'ويندوز',
  'road.desktop.title': 'لينكس وويندوز',
  'road.desktop.body': 'حماية لحواسيب سطح المكتب.',
  'band.lead': 'يحجب حصن ',
  'band.mark': 'أكثر من 236,000 موقع إباحي',
  'band.tail': ' قبل تحميل الصفحة، في كل متصفح على هاتفك.',
  'works.title': 'كيف يعمل حصن',
  'works.one.title': 'شغّل الحماية',
  'works.one.body': 'فعّل فلترة المواقع وحماية التطبيقات. يطلب أندرويد إذنين.',
  'works.two.title': 'اختر ما تحجبه',
  'works.two.body': 'أضف تطبيقات ومواقع وكلمات، ثم اضبط جدولًا أو حدًّا يوميًا.',
  'works.three.title': 'تنفّس بدل التصفح',
  'works.three.body': 'عند الحجب تظهر شاشة هادئة واحدة، ثم تعود إلى الشاشة الرئيسية.',
  'works.four.title': 'أبقِه يعمل',
  'works.four.body': 'يوضح حصن الإعدادات التي تُبقيه يعمل على هواتف سامسونج وشاومي وناثينغ.',
  'trust.title': 'الخصوصية هي الأصل.',
  'trust.card.title': 'كل فحص يجري على هاتفك.',
  'trust.card.link': 'اقرأ ما يغادره',
  'faq.heading': 'الأسئلة الشائعة',
  'faq.install.q': 'كيف أثبّته؟',
  'faq.install.a': 'نزّل الملف وافتحه. إذا طلب أندرويد، اسمح بالتثبيت من هذا المتصفح مرة واحدة.',
  'lang.toArabic': 'التبديل إلى العربية',
  'lang.toEnglish': 'التبديل إلى الإنجليزية',
};

const STORAGE_KEY = 'hisn-site-lang';
const english = new Map();
const englishAria = new Map();
let initialTitle = '';
let initialDescription = '';
let initialOgTitle = '';
let initialOgDescription = '';

function remember() {
  for (const element of document.querySelectorAll('[data-i18n]')) {
    english.set(element.dataset.i18n, element.textContent);
  }
  for (const element of document.querySelectorAll('[data-i18n-aria]')) {
    englishAria.set(element.dataset.i18nAria, element.getAttribute('aria-label') || '');
  }
  initialTitle = document.title;
  const description = document.querySelector('meta[name="description"]');
  initialDescription = description ? description.getAttribute('content') : '';
  const ogTitle = document.querySelector('meta[property="og:title"]');
  initialOgTitle = ogTitle ? ogTitle.getAttribute('content') : '';
  const ogDescription = document.querySelector('meta[property="og:description"]');
  initialOgDescription = ogDescription ? ogDescription.getAttribute('content') : '';
}

function setMeta(selector, value) {
  const element = document.querySelector(selector);
  if (element && value) element.setAttribute('content', value);
}

function apply(language) {
  const arabic = language === 'ar';
  const onArabic = (key) => (arabic ? (ar[key] ?? english.get(key) ?? '') : (english.get(key) ?? ''));
  document.documentElement.lang = arabic ? 'ar' : 'en';
  document.documentElement.dir = arabic ? 'rtl' : 'ltr';
  for (const element of document.querySelectorAll('[data-i18n]')) {
    element.textContent = onArabic(element.dataset.i18n);
  }
  for (const element of document.querySelectorAll('[data-i18n-aria]')) {
    const key = element.dataset.i18nAria;
    element.setAttribute('aria-label', arabic ? (ar[key] ?? englishAria.get(key) ?? '') : (englishAria.get(key) ?? ''));
  }
  document.title = arabic ? ar['meta.title'] : initialTitle;
  setMeta('meta[name="description"]', arabic ? ar['meta.description'] : initialDescription);
  setMeta('meta[property="og:title"]', arabic ? ar['meta.title'] : initialOgTitle);
  setMeta('meta[property="og:description"]', arabic ? ar['meta.description'] : initialOgDescription);
  const button = document.querySelector('[data-lang-button]');
  if (button) {
    button.textContent = arabic ? 'English' : 'العربية';
    button.setAttribute('aria-label', arabic ? ar['lang.toEnglish'] : ar['lang.toArabic']);
    button.setAttribute('lang', arabic ? 'en' : 'ar');
  }
  try {
    localStorage.setItem(STORAGE_KEY, arabic ? 'ar' : 'en');
  } catch {
    // A browser that blocks storage still gets the switch for this page view.
  }
}

function preferred() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'ar' || saved === 'en') return saved;
  } catch {
    // fall through to the browser language
  }
  return String(navigator.language || '').toLowerCase().startsWith('ar') ? 'ar' : 'en';
}

function init() {
  remember();
  apply(preferred());
  const button = document.querySelector('[data-lang-button]');
  if (button) {
    button.addEventListener('click', () => {
      apply(document.documentElement.lang === 'ar' ? 'en' : 'ar');
    });
  }
}

if (typeof document !== 'undefined') init();
