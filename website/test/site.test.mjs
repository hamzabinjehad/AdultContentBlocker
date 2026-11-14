import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync } from 'node:fs';

import { ar } from '../lib/site.js';

const read = (name) => readFileSync(new URL(`../${name}`, import.meta.url), 'utf8');
const release = JSON.parse(read('release.json'));
const index = read('index.html');
const privacy = read('privacy.html');
const vault = read('vault.html');
const partner = read('partner.html');
const script = read('lib/site.js');
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
});

test('the roadmap names every other version and labels it honestly', () => {
  for (const text of ['Android', 'Mac', 'iPhone', 'Browser extension', 'Hisn Vault']) {
    assert.ok(index.includes(text), `missing roadmap entry ${text}`);
  }
  assert.ok(index.includes('Other versions are coming.'));
  assert.ok(index.includes('data-i18n="status.soon"'));
  assert.ok(index.includes('data-i18n="road.androidNext.body"'));
});

test('every string on the landing and privacy pages has Arabic', () => {
  const missing = [];
  for (const [page, html] of [['index.html', index], ['privacy.html', privacy]]) {
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
  assert.ok(!/\bfetch\s*\(/.test(script), 'site.js makes a request');
});

test('the animation library is vendored, licensed and wired in', () => {
  assert.ok(existsSync(new URL('../lib/vendor/anime.min.js', import.meta.url)));
  assert.ok(existsSync(new URL('../lib/vendor/LICENSE.anime', import.meta.url)));
  assert.ok(index.includes('lib/vendor/anime.min.js'));
  assert.ok(index.includes('lib/animations.js'));
  assert.ok(index.includes('lib/motion-flag.js'));
  assert.match(animations, /anime/i);
  assert.match(animations, /prefers-reduced-motion/);
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
