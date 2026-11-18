/* The site's motion layer, powered by the vendored anime.js (MIT).
   It only adds movement: every element is visible without JavaScript, and a
   reader who asks for reduced motion gets the finished page. */
(function () {
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce || typeof window.anime !== 'function') {
    document.documentElement.classList.remove('js-motion');
    return;
  }
  window.__hisnMotion = true;
  var anime = window.anime;
  var ease = 'easeOutCubic';

  // The hero arrives as one gesture, then the phone and its card.
  anime({
    targets: '.hero3-copy > *',
    translateY: [24, 0],
    opacity: [0, 1],
    duration: 820,
    delay: anime.stagger(80, { start: 120 }),
    easing: ease
  });
  anime({
    targets: '.hero3-phone',
    translateY: [42, 0],
    opacity: [0, 1],
    scale: [0.96, 1],
    duration: 1000,
    delay: 260,
    easing: ease
  });
  // Sections arrive once, when they first enter the viewport.
  var items = document.querySelectorAll(
    '.section-head, .band-line, .feature-panel, .spotlight > *, .how-copy, .how-visual, .trust-card, .road-card, .faq details, .final-cta, .limits-list li'
  );
  if (!('IntersectionObserver' in window)) {
    items.forEach(function (element) { element.style.opacity = '1'; });
    return;
  }
  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      observer.unobserve(entry.target);
      anime({
        targets: entry.target,
        translateY: [22, 0],
        opacity: [0, 1],
        duration: 700,
        easing: ease
      });
    });
  }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
  items.forEach(function (element) {
    if (element.closest('.hero3')) return;
    observer.observe(element);
  });
})();
