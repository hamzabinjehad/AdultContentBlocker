import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync } from 'node:fs';

import { ar } from '../lib/site.js';
import { ar as homeAr } from '../lib/home-copy.js';

const read = (name) => readFileSync(new URL(`../${name}`, import.meta.url), 'utf8');
const release = JSON.parse(read('release.json'));
const index = read('index.html');
const privacy = read('privacy.html');
const limits = read('limits.html');
const vault = read('vault.html');
const partner = read('partner.html');
const script = read('lib/site.js');
const home = read('lib/home.js');
const animations = read('lib/animations.js');
const app = release.android;

function keys(html) {
  return new Set([...html.matchAll(/data-i18n(?:-aria)?="([^"]+)"/g)].map((m) => m[1]));
}

test('the release manifest is the signed 1.5.3 direct build', () => {
  assert.equal(app.version, '1.5.3');
  assert.equal(app.versionCode, 41);
  assert.equal(app.channel, 'direct');
  assert.equal(app.signed, true);
  assert.match(app.sha256, /^[0-9a-f]{64}$/);
  assert.match(app.certificateSha256, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/);
  assert.equal(app.minSdk, 23);
  assert.equal(release.next.version, '1.6.0');
  assert.equal(release.next.state, 'development');
});

test('the download buttons point at the manifest file', () => {
  const href = `downloads/${app.downloadName}`;
  const count = index.split(`href="${href}"`).length - 1;
  assert.ok(count >= 3, `expected hero, download panel and final CTA links, got ${count}`);
  assert.ok(!index.includes('downloads/SHA256SUMS'), 'the checksum file is internal, not a customer link');
});

test('the public page carries no release-developer details', () => {
  const visible = index
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .toLowerCase();
  for (const term of ['sha-256', 'certificate', 'version code', 'versioncode', 'signed',
    'github', 'source &', 'artifact', 'acceptance', 'release.json', 'play store',
    'checksum', 'apk']) {
    assert.ok(!visible.includes(term), `public page still says ${term}`);
  }
  assert.ok(!index.includes('openai') && !index.includes('github.com'));
  const limitsVisible = limits.replace(/<script[\s\S]*?<\/script>/g, ' ').replace(/<[^>]+>/g, ' ').toLowerCase();
  for (const term of ['sha-256', 'version code', 'github', 'checksum', 'release.json']) {
    assert.ok(!limitsVisible.includes(term), `limits page still says ${term}`);
  }
});

test('the platforms row names every platform and labels it honestly', () => {
  const get = index.slice(index.indexOf('id="get"'), index.indexOf('id="faq"'));
  for (const text of ['Android', 'iPhone and iPad', 'Mac', 'Windows', 'Browser extension']) {
    assert.ok(get.includes(text), `missing platform ${text}`);
  }
  assert.equal(get.split('data-i="soon"').length - 1, 4, 'iPhone, Mac, Windows and the extension say Coming');
  assert.ok(get.includes('data-i="now"'), 'Android says Available');
});

test('every store button and platform card says which device it is for', () => {
  const stores = [...index.matchAll(/<a class="store[^"]*"([^>]*)>/g)].map((m) => m[1]);
  const plats = [...index.matchAll(/<div class="plat"([^>]*)>/g)].map((m) => m[1]);
  for (const attrs of [...stores, ...plats]) assert.match(attrs, /data-os="(android|ios|mac|windows|web)"/);
  assert.equal(stores.length, 5);
  assert.equal(plats.length, 5);
  assert.match(home, /putFirst\(visitorOS\(\)\)/, 'the visitor\'s platform is not put first');
});

test('every string on the product page has Arabic, and it is Arabic', () => {
  const keysOnPage = new Set([...index.matchAll(/data-i="([^"]+)"/g)].map((m) => m[1]));
  const missing = [...keysOnPage].filter((key) => !(key in homeAr));
  assert.deepEqual(missing, []);
  const unused = Object.keys(homeAr).filter((key) => !keysOnPage.has(key));
  assert.deepEqual(unused, []);
  // An empty string hides an English-only line (a Qur'an translation) in Arabic.
  const notArabic = Object.entries(homeAr).filter(([, v]) => v !== '' && !/[\u0600-\u06FF]/.test(v)).map(([k]) => k);
  assert.deepEqual(notArabic, []);
  for (const [key, value] of Object.entries(homeAr)) {
    assert.ok(!/[\u0660-\u0669]/.test(value), `${key} uses ١٢٣ digits; the owner wants 1 2 3`);
    assert.ok(!value.includes('الإباحي'), `${key} says الإباحي; the owner wants محتوى الزنا`);
  }
});

test('every string on the landing and privacy pages has Arabic', () => {
  const missing = [];
  for (const [page, html] of [['index.html', index], ['privacy.html', privacy], ['limits.html', limits]]) {
    for (const key of keys(html)) if (!(key in ar)) missing.push(`${page}: ${key}`);
  }
  assert.deepEqual(missing, []);
});

test('the Arabic copy is actually Arabic', () => {
  const latinOnly = new Set(['card.sha', 'lang.toArabic', 'lang.toEnglish']);
  const problems = [];
  for (const [key, value] of Object.entries(ar)) {
    if (latinOnly.has(key)) continue;
    if (!/[\u0600-\u06FF]/.test(value)) problems.push(key);
  }
  assert.deepEqual(problems, []);
});

test('the pages do not load a third-party resource', () => {
  for (const [name, html] of [['index.html', index], ['privacy.html', privacy], ['vault.html', vault]]) {
    for (const match of html.matchAll(/(?:src|href)="([^"]+)"/g)) {
      const url = match[1];
      if (url.startsWith('#') || url.startsWith('data:')) continue;
      if (/^[a-z]+:/i.test(url)) {
        assert.ok(url.startsWith('https://github.com/hamzabinjehad/AdultContentBlocker'), `${name}: external resource ${url}`);
        continue;
      }
      assert.ok(!url.startsWith('//'), `${name}: protocol-relative ${url}`);
    }
  }
});

test('the CSP blocks connections and inline script', () => {
  for (const html of [index, privacy]) {
    assert.match(html, /http-equiv="Content-Security-Policy"/);
    assert.match(html, /connect-src 'none'/);
    assert.match(html, /script-src 'self'/);
    assert.ok(!html.includes("'unsafe-inline'"));
    assert.ok(!/<script(?![^>]*src=)[^>]*>/.test(html), 'inline script found');
  }
  assert.ok(!/\sstyle="/.test(index), 'the product page has an inline style the CSP would block');
  assert.ok(!/\bfetch\s*\(/.test(script), 'site.js makes a request');
  assert.ok(!/\bfetch\s*\(|XMLHttpRequest|WebSocket/.test(home), 'home.js makes a request');
});

test('the animation library is vendored, licensed and wired into the limits page', () => {
  assert.ok(existsSync(new URL('../lib/vendor/anime.min.js', import.meta.url)));
  assert.ok(existsSync(new URL('../lib/vendor/LICENSE.anime', import.meta.url)));
  assert.ok(limits.includes('lib/vendor/anime.min.js'));
  assert.match(animations, /anime/i);
  assert.match(animations, /prefers-reduced-motion/);
});

test('the product page art and styles are on this site', () => {
  for (const src of [...index.matchAll(/src="(assets\/[^"]+)"/g)].map((m) => m[1])) {
    assert.ok(existsSync(new URL(`../${src}`, import.meta.url)), `missing ${src}`);
  }
  const css = read('assets/home.css');
  for (const url of [...css.matchAll(/url\(([^)]+)\)/g)].map((m) => m[1])) {
    assert.ok(existsSync(new URL(`../assets/${url}`, import.meta.url)), `missing ${url}`);
  }
  assert.ok(!css.includes('fonts.googleapis') && !index.includes('fonts.googleapis'));
});

test('the limits page is separate from the sales page', () => {
  assert.ok(limits.includes('What Hisn will not do.'));
  assert.ok(limits.includes('href="privacy.html"'));
  assert.ok(limits.includes('href="index.html"'));
  assert.ok(limits.includes('data-i18n="limits.back"'));
  assert.ok(!index.includes('What Hisn will not do'), 'the sales page repeats the limits wall');
});

test('the Vault moved to vault.html and both pages link correctly', () => {
  assert.ok(vault.includes('Hisn Vault'));
  assert.ok(vault.includes('href="partner.html"'));
  assert.ok(partner.includes('href="vault.html"'));
  assert.ok(vault.includes('href="index.html"'));
  assert.ok(index.includes('href="vault.html"'));
  assert.ok(existsSync(new URL('../assets/phone-checks.png', import.meta.url)));
});

test('the privacy page is written for a person, not a release engineer', () => {
  const visible = privacy
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .toLowerCase();
  for (const term of ['github', 'sha-256', 'version code', 'signed list', 'drand', 'localstorage', 'csp']) {
    assert.ok(!visible.includes(term), `privacy page still says ${term}`);
  }
});

test('the privacy page covers the four outbound requests', () => {
  for (const key of ['privacy.leaves.update', 'privacy.leaves.dns', 'privacy.leaves.time', 'privacy.leaves.partner']) {
    assert.ok(key in ar, `missing ${key}`);
    assert.ok(privacy.includes(`data-i18n="${key}"`), `missing ${key} in privacy.html`);
  }
  assert.ok(!privacy.includes('github.com'), 'privacy page links a developer repository');
});
