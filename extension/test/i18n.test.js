// The two languages, held to each other and to every place a key is used.
//
// A key missing from the Arabic table falls back to English — readable, but a
// half-translated page; a key used in a page but missing from BOTH shows the
// raw key. A placeholder dropped from a translation silently loses the host
// or number it was meant to carry. Each of those fails here instead.

import en from "../lib/locale/en.js";
import ar from "../lib/locale/ar.js";
import { t, setLanguage, resolveLanguage, locale } from "../lib/i18n.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }
const placeholders = (s) => [...s.matchAll(/\{(\d)\}/g)].map((m) => m[1]).sort().join(",");

const enKeys = Object.keys(en).sort(), arKeys = Object.keys(ar).sort();
check(JSON.stringify(enKeys) === JSON.stringify(arKeys),
      `same keys in both tables (en-only: ${enKeys.filter((k) => !(k in ar))}; ar-only: ${arKeys.filter((k) => !(k in en))})`);
for (const k of enKeys) {
  if (!(k in ar)) continue;
  check(placeholders(en[k]) === placeholders(ar[k]), `${k}: same placeholders`);
  check(en[k].trim() && ar[k].trim(), `${k}: not empty`);
  if (k !== "lang.switchTo") check(/[؀-ۿ]/.test(ar[k]), `${k}: Arabic entry is Arabic`);
}

// Every key a page or script uses exists.
const used = new Set();
for (const file of ["../popup.html", "../options.html", "../blocked.html"]) {
  for (const m of readFile(file).matchAll(/data-i18n(?:-[a-z-]+)?="([^"]+)"/g)) used.add(m[1]);
}
for (const file of ["../popup.js", "../options.js", "../blocked.js", "../lib/status.js", "../lib/settings.js"]) {
  for (const m of readFile(file).matchAll(/\bt\("([^"]+)"/g)) used.add(m[1]);
}
for (const reason of ["default", "strict", "custom", "terms"]) {           // blocked.js builds these
  used.add(`blocked.${reason}.title`); used.add(`blocked.${reason}.subtitle`);
}
for (const k of used) check(k in en, `used key exists: ${k}`);
check(used.size > 120, `the scan found the keys (${used.size})`);

// The translator itself.
setLanguage("en");
check(t("blocked.open", "example.com") === "Open example.com", "placeholders fill");
check(t("no.such.key") === "no.such.key", "an unknown key shows itself, never nothing");
setLanguage("ar");
check(t("blocked.open", "example.com") === "افتح example.com", "Arabic fills the same placeholder");
check(locale() === "ar-u-nu-latn", "Arabic dates keep Latin digits");
setLanguage("fr");
check(t("status.active") === en["status.active"], "an unsupported language falls back to English");
check(resolveLanguage("auto", "ar-SA") === "ar" && resolveLanguage("auto", "en-GB") === "en"
      && resolveLanguage("en", "ar") === "en", "automatic follows the browser; a choice overrides it");
setLanguage("en");

print(`  ${checks - failures}/${checks} checks passed`);
if (failures) throw new Error(`${failures} i18n checks failed`);
