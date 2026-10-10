if (String(navigator.language || "").startsWith("ar")) {
  document.documentElement.lang = "ar";
  document.documentElement.dir = "rtl";
  document.getElementById("heading").textContent = "حظر حصن هذه الصفحة";
  document.getElementById("detail").textContent = "توجد في نص هذه الصفحة إشارات إلى محتوى إباحي. يجري الفحص على الجهاز وقد يخطئ، ولا يصنّف الصور أو الفيديو.";
  document.getElementById("next").textContent = "يمكنك إغلاق علامة التبويب هذه ومتابعة التصفح في مكان آخر.";
}
