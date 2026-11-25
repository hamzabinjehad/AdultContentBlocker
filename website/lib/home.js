import { ar as AR } from './home-copy.js';

// The product page: language switch, the three looks, the live timer on the phone and the
// breathing circle. Nothing here makes a request.
const EN = {};
document.querySelectorAll('[data-i]').forEach(el => { EN[el.dataset.i] = el.hasAttribute('data-html') ? el.innerHTML : el.textContent; });
const store = { get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }, set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} } };
const root = document.documentElement;
let lang = store.get('hisn-lang') || 'en';
function setLang(l) {
  lang = l; root.lang = l; root.dir = l === 'ar' ? 'rtl' : 'ltr';
  const d = l === 'ar' ? AR : EN;
  document.querySelectorAll('[data-i]').forEach(el => {
    const v = d[el.dataset.i] ?? EN[el.dataset.i];
    el.hidden = v === '';
    if (el.hasAttribute('data-html')) el.innerHTML = v; else el.textContent = v;
  });
  document.getElementById('lang').textContent = l === 'ar' ? 'English' : 'العربية';
  store.set('hisn-lang', l);
}
document.getElementById('lang').addEventListener('click', () => setLang(lang === 'ar' ? 'en' : 'ar'));

function currentLook() {
  if (root.dataset.look) return root.dataset.look;
  const dark = root.dataset.theme === 'dark' || (root.dataset.theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  return dark ? 'layl' : 'fajr';
}
function markLook() { const c = currentLook(); document.querySelectorAll('.look').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.look === c))); }
document.querySelectorAll('.look').forEach(b => b.addEventListener('click', () => { root.dataset.look = b.dataset.look; store.set('hisn-look', b.dataset.look); markLook(); }));
const saved = store.get('hisn-look'); if (saved) root.dataset.look = saved;
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', markLook);
new MutationObserver(markLook).observe(root, { attributes: true, attributeFilter: ['data-theme'] });
markLook();
setLang(lang);

// live "nights of light" timer, as in the app
const start = Date.now() - ((15 * 3600) + 28 * 60 + 4) * 1000;
const tick = document.getElementById('tick');
setInterval(() => { const s = Math.floor((Date.now() - start) / 1000) % 86400; tick.textContent = [s / 3600 | 0, (s / 60 | 0) % 60, s % 60].map(n => String(n).padStart(2, '0')).join(':'); }, 1000);

// breathing circle
const circle = document.getElementById('circle'), bword = document.getElementById('bword');
if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  let inhale = true;
  const breathe = () => { circle.style.transform = inhale ? 'scale(1)' : 'scale(.7)'; bword.textContent = inhale ? (lang === 'ar' ? 'شهيق' : 'Breathe in') : (lang === 'ar' ? 'زفير' : 'Breathe out'); inhale = !inhale; };
  breathe(); setInterval(breathe, 4000);
}


// Show the visitor's own platform first: Android on an Android phone, Mac on a Mac, and so on.
// Read from the browser only; nothing is sent. iPadOS reports itself as a Mac, so touch decides.
function visitorOS() {
  const ua = navigator.userAgent || '';
  const plat = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || '';
  if (/android/i.test(ua)) return 'android';
  if (/iphone|ipad|ipod/i.test(ua) || (/mac/i.test(plat) && navigator.maxTouchPoints > 1)) return 'ios';
  if (/mac/i.test(plat)) return 'mac';
  if (/win/i.test(plat)) return 'windows';
  return 'web';
}
function putFirst(os) {
  for (const box of document.querySelectorAll('.stores, .plats')) {
    const item = box.querySelector(':scope > [data-os="' + os + '"]');
    const items = [...box.querySelectorAll(':scope > [data-os]')];
    const order = ['android', 'ios', 'mac', 'windows', 'web'];
    items.sort((a, b) => order.indexOf(a.dataset.os) - order.indexOf(b.dataset.os)).forEach((el) => box.appendChild(el));
    if (item) box.prepend(item);
  }
  document.documentElement.dataset.os = os;
}
putFirst(visitorOS());
window.hisnShowFor = putFirst;

const topBar = document.getElementById('top');
addEventListener('scroll', () => topBar.classList.toggle('scrolled', scrollY > 8), { passive: true });
