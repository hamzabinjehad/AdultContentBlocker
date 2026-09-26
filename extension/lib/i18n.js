/**
 * The extension's pages in Arabic or English.
 *
 * Hisn exists first for Arabic readers, and its pages were English-only. The
 * language follows the browser's own UI language unless the person picks one
 * in Settings or with the popup's switch — many Arabic readers run an English
 * browser — and the choice is kept in `chrome.storage.local` under
 * `uiLanguage`, beside (not inside) the protection state.
 *
 * `t()` works before `initLanguage()` has run, in English, which is what the
 * jsc suites see: they assert the English wording, and the Arabic table is
 * held to exactly the same keys and placeholders by test/i18n.test.js.
 */
import en from "./locale/en.js";
import ar from "./locale/ar.js";

const TABLES = { en, ar };
let current = "en";

export const LANGUAGES = Object.freeze(Object.keys(TABLES));

/** The phrase for `key`, with {0}, {1}… filled from `args`. A key missing
 *  from the current table falls back to English, then to the key itself —
 *  never to nothing. */
export function t(key, ...args) {
  const phrase = TABLES[current][key] ?? en[key] ?? key;
  return phrase.replace(/\{(\d)\}/g, (_, i) => String(args[Number(i)] ?? ""));
}

export function language() { return current; }

export function setLanguage(lang) {
  current = TABLES[lang] ? lang : "en";
  return current;
}

/** The locale for dates and numbers: Arabic with Latin digits, as everywhere
 *  else in the product. */
export function locale() {
  return current === "ar" ? "ar-u-nu-latn" : undefined;
}

/** `auto`: Arabic when the browser's UI is Arabic. */
/**
 * Which table: a choice made in these pages first; on Automatic, the Hisn
 * app's language when the app has sent one — so an app switched to Arabic
 * brings its extension along — and otherwise the browser's.
 */
export function resolveLanguage(preference, uiLanguage = "", appLanguage = "") {
  if (TABLES[preference]) return preference;
  if (TABLES[appLanguage]) return appLanguage;
  return String(uiLanguage).toLowerCase().startsWith("ar") ? "ar" : "en";
}

/** The Mac app's language as the worker last heard it, or "". */
export async function appLanguage() {
  try {
    return (await chrome.storage.local.get("appLanguage")).appLanguage ?? "";
  } catch { return ""; }
}

/** Read the preference, set the language, and translate the static page. */
export async function initLanguage(doc = globalThis.document) {
  let preference = "auto";
  try {
    preference = (await chrome.storage.local.get("uiLanguage")).uiLanguage ?? "auto";
  } catch { /* storage unavailable: follow the browser */ }
  const ui = globalThis.chrome?.i18n?.getUILanguage?.() ?? globalThis.navigator?.language ?? "en";
  setLanguage(resolveLanguage(preference, ui, await appLanguage()));
  if (doc) translatePage(doc);
  return { language: current, preference };
}

export async function savePreference(preference) {
  await chrome.storage.local.set({ uiLanguage: preference });
}

/**
 * Fill every marked element: `data-i18n` → text, and `data-i18n-<attr>` →
 * that attribute (placeholder, aria-label, title). Direction and `lang` go on
 * the root so the whole page mirrors for Arabic.
 */
export function translatePage(doc) {
  doc.documentElement.lang = current;
  doc.documentElement.dir = current === "ar" ? "rtl" : "ltr";
  for (const el of doc.querySelectorAll("[data-i18n]")) el.textContent = t(el.dataset.i18n);
  for (const attr of ["placeholder", "aria-label", "title"]) {
    const data = `data-i18n-${attr}`;
    for (const el of doc.querySelectorAll(`[${data}]`)) el.setAttribute(attr, t(el.getAttribute(data)));
  }
  const title = doc.querySelector("title[data-i18n]");
  if (title) doc.title = t(title.dataset.i18n);
}
