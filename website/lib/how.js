/* The "How Hisn works" stepper. Without this script every step shows its
   description and the first screen; with it, choosing a step swaps the screen.
   It advances on its own while in view until the reader touches it, and never
   when they asked for reduced motion. */
(function () {
  var root = document.querySelector('[data-how]');
  if (!root) return;
  var steps = Array.prototype.slice.call(root.querySelectorAll('[data-step]'));
  var shots = Array.prototype.slice.call(root.querySelectorAll('[data-shot]'));
  if (!steps.length || steps.length !== shots.length) return;

  var current = 0;
  var timer = null;
  var visible = false;
  var touched = false;
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function show(index) {
    current = index;
    steps.forEach(function (step, i) {
      step.setAttribute('aria-pressed', i === index ? 'true' : 'false');
    });
    shots.forEach(function (shot, i) {
      shot.classList.toggle('is-active', i === index);
    });
  }

  function stop() {
    if (timer) window.clearInterval(timer);
    timer = null;
  }

  function start() {
    if (timer || touched || reduce || !visible) return;
    timer = window.setInterval(function () {
      show((current + 1) % steps.length);
    }, 4200);
  }

  steps.forEach(function (step, i) {
    step.addEventListener('click', function () {
      touched = true;
      stop();
      show(i);
    });
  });

  // The hidden screens are lazy; fetch them once the first one is on screen.
  root.classList.add('is-live');
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(function (entries) {
      visible = entries[0].isIntersecting;
      if (visible) {
        shots.forEach(function (shot) { shot.loading = 'eager'; });
        start();
      } else {
        stop();
      }
    }, { threshold: 0.35 }).observe(root);
  }
})();
