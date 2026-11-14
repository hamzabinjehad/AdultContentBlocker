/* Adds the initial hidden state only when the browser can animate and the
   reader has not asked for reduced motion. If the animation script never
   starts, the timeout removes it again so no content can stay hidden. */
(function () {
  try {
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    document.documentElement.classList.add('js-motion');
    window.setTimeout(function () {
      if (!window.__hisnMotion) document.documentElement.classList.remove('js-motion');
    }, 2600);
  } catch (error) {
    /* A browser without matchMedia simply sees the finished page. */
  }
})();
