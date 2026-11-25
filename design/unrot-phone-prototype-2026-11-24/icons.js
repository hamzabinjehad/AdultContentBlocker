/* One drawn icon set for the whole app: 24 grid, 1.8 stroke, round ends, gold ink in a soft gold well.
   Screens still name an icon by a plain symbol; icons() swaps each symbol for its drawing after a screen is rendered. */
const ICONS = {
  shield: '<path d="M12 3l7.5 2.8v5.7c0 4.700-3.200 7.900-7.500 9.200-4.300-1.300-7.500-4.500-7.500-9.200V5.800z"/><path d="M8.700 12l2.400 2.400 4.300-4.800"/>',
  hourglass: '<path d="M7 3h10M7 21h10M8 3c0 5 8 5 8 9s-8 4-8 9M16 3c0 5-8 5-8 9s8 4 8 9"/><path d="M10.500 18.500h3"/>',
  game: '<path d="M7.500 8h9a4.500 4.500 0 0 1 4.400 5.400l-.6 3a2.300 2.300 0 0 1-4 1L14.500 15h-5l-1.800 2.400a2.300 2.300 0 0 1-4-1l-.6-3A4.500 4.500 0 0 1 7.500 8z"/><path d="M8 10.800v2.600M6.700 12.100h2.600"/><circle cx="15.300" cy="11.200" r=".7"/><circle cx="17" cy="13" r=".7"/>',
  laptop: '<rect x="4.500" y="5" width="15" height="10" rx="1.800"/><path d="M2.500 18.500h19"/>',
  moon: '<path d="M19.500 14.200A8 8 0 0 1 9.800 4.500a8 8 0 1 0 9.700 9.700z"/>',
  feed: '<rect x="4" y="4.500" width="16" height="15" rx="2.200"/><path d="M7.500 8.500h4.500v4H7.500zM15 8.500h1.500M15 12.500h1.500M7.500 16h9"/>',
  noentry: '<circle cx="12" cy="12" r="8.500"/><path d="M8 12h8"/>',
  target: '<circle cx="12" cy="12" r="8.500"/><circle cx="12" cy="12" r="4.500"/><circle cx="12" cy="12" r=".8"/>',
  lock: '<rect x="5.500" y="10.500" width="13" height="10" rx="2.400"/><path d="M8.500 10.500V8a3.500 3.500 0 0 1 7 0v2.500M12 14.500v2"/>',
  globe: '<circle cx="12" cy="12" r="8.500"/><path d="M3.500 12h17M12 3.500c3 3 3 14 0 17M12 3.500c-3 3-3 14 0 17"/>',
  eye: '<path d="M2.500 12s3.500-6.500 9.500-6.500 9.500 6.500 9.500 6.500-3.500 6.500-9.500 6.500S2.500 12 2.500 12z"/><circle cx="12" cy="12" r="2.800"/>',
  words: '<path d="M3.500 17.500l4-11 4 11M5 14h5M14.500 17.500v-4a2.500 2.500 0 0 1 5 0v4M14.500 15.500a2.500 2.500 0 0 0 5 0"/>',
  chart: '<path d="M5 20V11M12 20V5M19 20v-6"/>',
  phone: '<rect x="7" y="2.500" width="10" height="19" rx="2.400"/><path d="M10.500 18.500h3"/>',
  compass: '<circle cx="12" cy="12" r="8.500"/><path d="M15.500 8.500l-2 5-5 2 2-5z"/>',
  timer: '<circle cx="12" cy="13.500" r="7.500"/><path d="M12 13.500V9.500M9.500 2.500h5"/>',
  list: '<path d="M8.500 6.500h11M8.500 12h11M8.500 17.500h11"/><circle cx="4.700" cy="6.500" r=".8"/><circle cx="4.700" cy="12" r=".8"/><circle cx="4.700" cy="17.500" r=".8"/>',
  blur: '<circle cx="12" cy="9" r="4"/><path d="M4.500 20c1.200-3.600 4-5.500 7.500-5.500s6.300 1.900 7.500 5.500" stroke-dasharray="1.500 2.600"/>',
  incognito: '<path d="M3 12.500h18M6.500 12.500l1.500-7h8l1.500 7"/><circle cx="8" cy="17" r="2.500"/><circle cx="16" cy="17" r="2.500"/><path d="M10.500 17h3"/>',
  search: '<circle cx="11" cy="11" r="6.500"/><path d="M16 16l4.500 4.500"/>',
  people: '<circle cx="8.500" cy="8.500" r="3"/><circle cx="16.500" cy="9.500" r="2.400"/><path d="M2.800 19c.8-3.200 2.900-5 5.700-5s4.900 1.800 5.700 5M15.500 14.200c2.700-.2 4.700 1.400 5.600 4.300"/>',
  key: '<circle cx="8" cy="15.500" r="4"/><path d="M11 12.500l8.500-8.500M16 7.500l2.500 2.500"/>',
  trash: '<path d="M4.500 6.500h15M9.500 6.500V4h5v2.500M6.500 6.500l1 13.500h9l1-13.500M10 10.500v6M14 10.500v6"/>',
  pin: '<path d="M9 3.500h6l-1 6 3 3.500H7l3-3.500zM12 13v7.500"/>',
  note: '<path d="M4.500 19.500l1-4L16 5a2.100 2.100 0 0 1 3 3L8.500 18.500zM14 7l3 3"/>',
  half: '<circle cx="12" cy="12" r="8.500"/><path d="M12 3.500v17M12 7.500h6.500M12 12h8.500M12 16.500h6.500"/>',
  bell: '<path d="M6 16.500V11a6 6 0 0 1 12 0v5.500l1.500 2h-15zM10 21h4"/>',
  tree: '<path d="M12 21v-6"/><path d="M12 3.500a4.500 4.500 0 0 1 4.400 5.400A4 4 0 0 1 15 16.500H9a4 4 0 0 1-1.400-7.600A4.500 4.500 0 0 1 12 3.500z"/>',
  help: '<circle cx="12" cy="12" r="8.500"/><path d="M9.500 9.500a2.600 2.600 0 1 1 3.700 2.400c-.8.400-1.200 1-1.200 1.900"/><circle cx="12" cy="16.600" r=".7"/>',
  privacy: '<path d="M12 3l7.500 2.800v5.700c0 4.700-3.200 7.900-7.500 9.200-4.300-1.300-7.500-4.500-7.500-9.200V5.800z"/><rect x="9" y="11" width="6" height="4.500" rx="1"/><path d="M10.200 11V9.800a1.800 1.800 0 0 1 3.600 0V11"/>',
  music: '<path d="M9 17.500V6l10-2v11.500"/><circle cx="6.800" cy="17.500" r="2.300"/><circle cx="16.800" cy="15.500" r="2.300"/>',
  map: '<path d="M3.500 6.500l5.500-2 6 2 5.500-2v13l-5.500 2-6-2-5.500 2zM9 4.500v13M15 6.500v13"/>',
  sparkle: '<path d="M12 3.500l1.800 5 5 1.800-5 1.800-1.800 5-1.800-5-5-1.800 5-1.800zM18.500 15.500l.8 2.200 2.200.8-2.200.8-.8 2.200-.8-2.200-2.200-.8 2.200-.8z"/>',
  ruler: '<path d="M4 15.500L15.500 4l4.500 4.500L8.500 20zM8 12l1.500 1.500M10.500 9.500l1.500 1.500M13 7l1.500 1.500"/>',
  leaf: '<path d="M5 19c0-8 5-13 14.500-14C19 14.500 14 19.500 6.500 19zM5 19l7-7"/>',
  smile: '<circle cx="12" cy="12" r="8.500"/><path d="M8.500 14c1 1.500 2.200 2.200 3.500 2.200s2.500-.7 3.500-2.200"/><circle cx="9.200" cy="10" r=".8"/><circle cx="14.800" cy="10" r=".8"/>',
  bulb: '<path d="M9 17.500h6M10 20.500h4M8.500 14.500A6 6 0 1 1 15.500 14.500c-.6.600-.9 1.300-.9 2h-5.200c0-.7-.3-1.400-.9-2z"/>',
  walk: '<circle cx="13" cy="4.500" r="1.800"/><path d="M9 21l2.500-6-2-3 1.500-4 3 1 2 3.500 2.500 1M12 15l3 2.500V21M10.500 8l-3 1.500-1 3"/>',
  book: '<path d="M4 5.500c3-1 5.500-.5 8 1 2.500-1.500 5-2 8-1v13c-3-1-5.500-.5-8 1-2.500-1.500-5-2-8-1zM12 6.500v13"/>',
  call: '<path d="M5 4.500h3.500l1.500 4-2 1.500a11 11 0 0 0 6 6l1.500-2 4 1.500V19a1.800 1.800 0 0 1-2 1.800A16 16 0 0 1 3.200 6.500 1.800 1.800 0 0 1 5 4.500z"/>',
  drop: '<path d="M12 3.500c3.500 4 6 7 6 10.500a6 6 0 0 1-12 0c0-3.500 2.500-6.500 6-10.500z"/>',
  hand: '<path d="M8 13V6.500a1.400 1.400 0 0 1 2.800 0V11M10.800 11V4.800a1.400 1.400 0 0 1 2.800 0V11M13.600 11V5.800a1.400 1.400 0 0 1 2.800 0V12M16.400 11a1.400 1.400 0 0 1 2.800 0v4a6 6 0 0 1-6 6h-1.500a5.500 5.500 0 0 1-4.600-2.500L4.600 15a1.500 1.500 0 0 1 2.300-1.900L8 14.200"/>',
  broken: '<path d="M12 20.500C6 16 3.500 12.800 3.500 9.300A4.300 4.300 0 0 1 12 7.500a4.300 4.300 0 0 1 8.500 1.800c0 3.500-2.500 6.700-8.500 11.200z"/><path d="M12 7.500l-1.500 3.500 3 2-1.500 3.500"/>',
  wave: '<path d="M2.500 16c2 0 2.500-2 4.700-2s2.700 2 4.800 2 2.600-2 4.700-2 2.800 2 4.800 2M2.500 20c2 0 2.500-2 4.700-2s2.700 2 4.800 2 2.600-2 4.700-2 2.800 2 4.800 2M6 11.500c0-4 2.500-6.500 6-6.500 2 0 3.500 1 4 2.500-2-.5-3.500.5-3.500 2"/>',
  door: '<path d="M6 21V4.500a1 1 0 0 1 1-1h8.500a1 1 0 0 1 1 1V21M3.500 21h17"/><circle cx="13.500" cy="12.500" r=".8"/>',
  infinity: '<path d="M12 12c-1.500-2.500-3-4-5-4a4 4 0 0 0 0 8c2 0 3.500-1.500 5-4zm0 0c1.500 2.500 3 4 5 4a4 4 0 0 0 0-8c-2 0-3.500 1.500-5 4z"/>',
  hush: '<circle cx="12" cy="12" r="8.500"/><path d="M12 12.500v4.500"/><circle cx="9" cy="10" r=".8"/><circle cx="15" cy="10" r=".8"/>',
  arm: '<path d="M6 19.500c4 1.500 9 1.500 12-1 2-1.700 2-5-.5-6.500-2-1.200-4-.5-5 1l-1.500-3.500V6a2 2 0 0 0-4 0v4L6 13z"/>',
  wall: '<rect x="3.500" y="5" width="17" height="14" rx="1.500"/><path d="M3.500 9.700h17M3.500 14.300h17M9 5v4.700M15 5v4.700M12 9.700v4.600M9 14.300V19M15 14.300V19"/>',
  sprout: '<path d="M12 21v-9M12 12c0-3.500-2.500-5.500-6.500-5.500C5.500 10 8 12 12 12zM12 14c0-3 2.200-5 6.500-5-.2 3.500-2.500 5-6.500 5z"/>',
  slot: '<rect x="4" y="6" width="13" height="13" rx="2"/><path d="M8.300 6v13M12.700 6v13M17 10.500h2.500V6.500"/><circle cx="19.500" cy="5" r="1.200"/>',
  play: '<path d="M8 5l11 7-11 7z"/>',
  warn: '<path d="M12 4l9 15.500H3zM12 10v4.500"/><circle cx="12" cy="17" r=".7"/>',
  apps: '<rect x="4" y="4" width="6.500" height="6.500" rx="1.600"/><rect x="13.500" y="4" width="6.500" height="6.500" rx="1.600"/><rect x="4" y="13.500" width="6.500" height="6.500" rx="1.600"/><rect x="13.500" y="13.500" width="6.500" height="6.500" rx="1.600"/>',
  breathe: '<path d="M3 8h11a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 16h8"/>',
  more: '<circle cx="6" cy="12" r="1.200"/><circle cx="12" cy="12" r="1.200"/><circle cx="18" cy="12" r="1.200"/>',
  flag: '<path d="M6 21V4M6 5c4-2 7 2 12 0v8c-5 2-8-2-12 0"/>',
  bed: '<path d="M3 18.500V7M3 14.500h18v4M21 14.500V12a3 3 0 0 0-3-3h-7v5.500"/><circle cx="7" cy="11" r="1.800"/>',
  work: '<rect x="3.500" y="7.500" width="17" height="12" rx="2"/><path d="M9 7.500V5.500A1.500 1.500 0 0 1 10.500 4h3A1.500 1.500 0 0 1 15 5.500v2M3.500 13h17"/>',
  yawn: '<circle cx="12" cy="12" r="8.500"/><ellipse cx="12" cy="15" rx="2" ry="2.400"/><path d="M8 10.500h2.200M13.800 10.500H16"/>',
  sleep: '<path d="M19.500 14.200A8 8 0 0 1 9.800 4.500a8 8 0 1 0 9.700 9.700z"/><path d="M14.500 4h4l-4 4.500h4"/>',
  point: '<path d="M4 12h13M12 6.500l5.500 5.500-5.500 5.500"/>',
  check: '<path d="M5 12.500l4.500 4.500L19 7"/>',
  dot: '<circle cx="12" cy="12" r="2"/>',
  star: '<path d="M12 3.500l2.600 5.400 5.900.8-4.300 4.100 1.100 5.900L12 16.900l-5.300 2.800 1.100-5.900-4.300-4.100 5.900-.8z"/>',
  apps2: '<rect x="4" y="4" width="16" height="16" rx="4"/><path d="M9 9h.01M15 9h.01M9 15h.01M15 15h.01"/>',
};
/* The app's own objects: matte black and gold, painted once and shipped in the app (unrot_3d_*). Every icon in a row,
   tile or sheet is one of these. The thin line drawings above stay only as a fallback for a name with no object. */
const OBJ = { shield: "shield", privacy: "lock", lock: "lock", pin: "lock", noentry: "shield", key: "key", door: "door", hand: "hand",
  hourglass: "hourglass", yawn: "hourglass", moon: "moon", sleep: "moon", bed: "moon", target: "target", globe: "globe", map: "globe", compass: "compass",
  eye: "eye", blur: "eye", incognito: "eye", search: "magnifier", chart: "chart", ruler: "chart", phone: "phone", apps: "phone", call: "phone", timer: "watch",
  bell: "bell", music: "bell", note: "pencil", book: "book", words: "book", list: "notebook", work: "notebook", laptop: "laptop", bulb: "bulb",
  people: "people", sprout: "sprout", tree: "sprout", leaf: "leaf", drop: "leaf", sparkle: "star", star: "star", dot: "star", flag: "medal",
  feed: "news", wall: "wall", game: "game", more: "dial", half: "dial", infinity: "infinity", breathe: "pause", play: "pause", broken: "bolt",
  smile: "sun", wave: "wave", warn: "bolt", arm: "bolt", help: "chat", hush: "chat", slot: "gift", walk: "rocket", point: "rocket", check: "tick", trash: "trash" };
const ic = (n, size = "1em") => OBJ[n]
  ? `<img class="uic3" src="ic/${OBJ[n]}.webp" alt="" draggable="false">`
  : `<svg class="uic" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[n] || ICONS.dot}</svg>`;
/* symbol used in the screens -> icon name */
const EM = {
  "\u{1F6E1}": "shield", "⛉": "shield", "⏳": "hourglass", "\u{1F3AE}": "game", "\u{1F4BB}": "laptop", "\u{1F319}": "moon", "☾": "moon",
  "\u{1F4F0}": "feed", "⛔": "noentry", "\u{1F3AF}": "target", "\u{1F512}": "lock", "\u{1F310}": "globe", "\u{1F441}": "eye", "\u{1F524}": "words",
  "\u{1F4CA}": "chart", "▥": "chart", "\u{1F4F1}": "phone", "\u{1F9ED}": "compass", "⏱": "timer", "\u{1F4CB}": "list", "\u{1F648}": "blur",
  "\u{1F576}": "incognito", "\u{1F50D}": "search", "\u{1F91D}": "people", "\u{1F511}": "key", "\u{1F5D1}": "trash", "\u{1F4CC}": "pin", "✎": "note",
  "✏": "note", "◐": "half", "\u{1F514}": "bell", "\u{1F515}": "bell", "\u{1F333}": "tree", "❓": "help", "\u{1F510}": "privacy", "\u{1F3B5}": "music",
  "\u{1F5FA}": "map", "✨": "sparkle", "\u{1F4CF}": "ruler", "\u{1F342}": "leaf", "\u{1F642}": "smile", "☺": "smile", "\u{1F4A1}": "bulb",
  "\u{1F6B6}": "walk", "\u{1F4D6}": "book", "\u{1F4DE}": "call", "\u{1F4A7}": "drop", "\u{1FAF8}": "hand", "\u{1F494}": "broken", "\u{1F30A}": "wave",
  "\u{1F6AA}": "door", "♾": "infinity", "∞": "infinity", "\u{1F92B}": "hush", "\u{1F4AA}": "arm", "\u{1F9F1}": "wall", "\u{1F331}": "sprout",
  "\u{1F33F}": "breathe", "\u{1F3B0}": "slot", "▶": "play", "⚠": "warn", "▦": "apps", "◌": "breathe", "\u{1F3C1}": "flag",
  "\u{1F6CF}": "bed", "\u{1F4BC}": "work", "\u{1F971}": "yawn", "\u{1F634}": "sleep", "\u{1F636}": "hush", "\u{1F449}": "point", "✓": "check",
  "·": "dot", "\u{1F30C}": "star", "⋯": "more", "\u{1F4F2}": "phone", "\u{1F4DD}": "note",
};
const bare = (t) => t.replace(/[︎️]/g, "").trim();
function icons(root) {
  // an element that holds nothing but one known symbol becomes that icon
  root.querySelectorAll(".ph span, .ph div, .ph b").forEach(e => {
    if (e.childElementCount) return; const n = EM[bare(e.textContent)];
    if (n) { e.innerHTML = ic(n); e.classList.add("has-ic"); }
  });
  // a tile or button that starts with a symbol gets the icon in front of its words
  root.querySelectorAll(".ph .tile, .ph .opt").forEach(e => {
    const first = e.firstChild; if (!first || first.nodeType !== 3) return;
    const m = first.textContent.match(/^\s*(\S+)\s+(.*)$/s); if (!m) return; const n = EM[bare(m[1])]; if (!n) return;
    first.textContent = " " + m[2]; e.insertAdjacentHTML("afterbegin", ic(n));
  });
}
