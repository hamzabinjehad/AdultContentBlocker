// Skull-shield logo concepts for Hisn. Each concept is three layers in a
// 108 x 108 Android adaptive-icon canvas (safe zone: circle r=33 at 54,54):
//   base  - shield silhouette (ink colour)
//   cut   - shapes knocked out of the base (paper colour)
//   back  - details drawn back on top of the cut (ink colour)
// The same layers produce the colour icon and a solid monochrome silhouette.

const CONCEPTS = {
  // A: the current Android idea, redrawn. Taller shield with a soft notch at
  // the top, a rounder cranium, wide sockets and three bold teeth that stay
  // readable at 16 px.
  A: {
    name: 'Refined',
    base: 'M54 21 C61 25.5 69 27.5 78 28 Q80 28.2 80 30 L80 53 C80 70.5 69.5 81.5 54 88 C38.5 81.5 28 70.5 28 53 L28 30 Q28 28.2 30 28 C39 27.5 47 25.5 54 21 Z',
    cut: 'M54 35 C64.5 35 72 42 72 52 C72 58 69.5 61.8 66 63.6 L66 72 Q66 74 64 74 L44 74 Q42 74 42 72 L42 63.6 C38.5 61.8 36 58 36 52 C36 42 43.5 35 54 35 Z',
    back: [
      'M47.5 50.5 C47.5 54.5 45 57 42.2 57 C39.6 57 38.6 54.8 39 52.6 C39.6 49.6 42.4 48 44.8 48.2 C46.4 48.4 47.5 49.2 47.5 50.5 Z',
      'M60.5 50.5 C60.5 54.5 63 57 65.8 57 C68.4 57 69.4 54.8 69 52.6 C68.4 49.6 65.6 48 63.2 48.2 C61.6 48.4 60.5 49.2 60.5 50.5 Z',
      'M54 58.5 L56.4 63.4 Q54 64.6 51.6 63.4 Z',
      'M48.6 68 h2.6 v6.4 h-2.6 Z', 'M56.8 68 h2.6 v6.4 h-2.6 Z',
    ],
  },

  // B: the shield is the skull. Sockets, nose and a jaw line are cut straight
  // out of the shield, so the whole mark is one silhouette: the boldest
  // option at small sizes and a natural monochrome icon.
  B: {
    name: 'Shield-skull',
    base: 'M54 20 C62 24.5 71 27 80 27.5 Q82 27.6 82 29.6 L82 52 C82 70 71 81.5 54 88.5 C37 81.5 26 70 26 52 L26 29.6 Q26 27.6 28 27.5 C37 27 46 24.5 54 20 Z',
    cut: [
      // eye sockets, angled inward for a watchful rather than comic look
      'M36.5 47.5 C38.5 45 45 45 49 47.6 C50.8 48.8 50.6 51.6 49.4 53.8 C47.8 56.8 44.4 58.6 41.2 58 C37.4 57.2 35.2 52.6 36.5 47.5 Z',
      'M71.5 47.5 C69.5 45 63 45 59 47.6 C57.2 48.8 57.4 51.6 58.6 53.8 C60.2 56.8 63.6 58.6 66.8 58 C70.6 57.2 72.8 52.6 71.5 47.5 Z',
      'M54 59 L57.2 65.4 Q54 67 50.8 65.4 Z',
      // jaw line with three teeth
      'M38 70 H70 V72.6 H38 Z',
      'M45.4 72.6 h2.6 v7.6 h-2.6 Z', 'M52.7 72.6 h2.6 v8.2 h-2.6 Z', 'M60 72.6 h2.6 v7.6 h-2.6 Z',
    ],
    back: [],
  },

  // C: fortress skull. The shield's top edge becomes battlements, tying the
  // skull back to the name Hisn (fortress), with a compact skull inside.
  C: {
    name: 'Fortress',
    base: 'M28 24 H37 V29 H45.5 V24 H62.5 V29 H71 V24 H80 V53 C80 70.5 69.5 81.5 54 88 C38.5 81.5 28 70.5 28 53 Z',
    cut: 'M54 37 C64 37 71 43.6 71 53 C71 58.6 68.8 62.2 65.4 64 L65.4 72 Q65.4 74 63.4 74 L44.6 74 Q42.6 74 42.6 72 L42.6 64 C39.2 62.2 37 58.6 37 53 C37 43.6 44 37 54 37 Z',
    back: [
      'M40.6 51 C42.2 48.6 46 48.4 48 50.2 C49.4 51.6 48.8 54.6 46.8 56.2 C44.8 57.8 41.8 57.4 40.8 55.2 C40.2 53.8 40 52 40.6 51 Z',
      'M67.4 51 C65.8 48.6 62 48.4 60 50.2 C58.6 51.6 59.2 54.6 61.2 56.2 C63.2 57.8 66.2 57.4 67.2 55.2 C67.8 53.8 68 52 67.4 51 Z',
      'M54 58.6 L56.4 63.4 Q54 64.6 51.6 63.4 Z',
      'M48.8 68 h2.4 v6.2 h-2.4 Z', 'M56.8 68 h2.4 v6.2 h-2.4 Z',
    ],
  },
};

const PALETTES = {
  light: { bg: '#F4F1EA', ink: '#1F3A5F', paper: '#F4F1EA' },
  dark: { bg: '#121722', ink: '#E9E4D8', paper: '#121722' },
  splash: { bg: '#1F3A5F', ink: '#F4F1EA', paper: '#1F3A5F' },
};

let uid = 0;
const list = (v) => (Array.isArray(v) ? v : [v]);
const paths = (ds, fill) => list(ds).map((d) => `<path d="${d}" fill="${fill}"/>`).join('');

// Colour icon: full-bleed background, optional mask shape (circle/squircle).
function iconSVG(c, pal, { size = 108, mask = 'none', bg = true } = {}) {
  const id = `c${uid++}`;
  const clip = {
    none: '',
    circle: `<clipPath id="${id}"><circle cx="54" cy="54" r="54"/></clipPath>`,
    squircle: `<clipPath id="${id}"><path d="M54 0 C96 0 108 12 108 54 C108 96 96 108 54 108 C12 108 0 96 0 54 C0 12 12 0 54 0 Z"/></clipPath>`,
    rounded: `<clipPath id="${id}"><rect width="108" height="108" rx="24"/></clipPath>`,
  }[mask];
  const g = mask === 'none' ? '<g>' : `<g clip-path="url(#${id})">`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="${size}" height="${size}"><defs>${clip}</defs>${g}` +
    (bg ? `<rect width="108" height="108" fill="${pal.bg}"/>` : '') +
    paths(c.base, pal.ink) + paths(c.cut, pal.paper) + paths(c.back, pal.ink) + '</g></svg>';
}

// Monochrome: one solid silhouette, cut-outs are real holes (Android 13
// themed icons and Apple tinted/clear modes both want this).
function monoSVG(c, colour, size = 108) {
  const id = `m${uid++}`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="${size}" height="${size}"><defs><mask id="${id}">` +
    paths(c.base, '#fff') + paths(c.cut, '#000') + paths(c.back, '#fff') +
    `</mask></defs><rect width="108" height="108" fill="${colour}" mask="url(#${id})"/></svg>`;
}

if (typeof module !== 'undefined') module.exports = { CONCEPTS, PALETTES, iconSVG, monoSVG };
