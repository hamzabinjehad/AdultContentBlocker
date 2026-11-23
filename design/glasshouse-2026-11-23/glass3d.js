// The Glasshouse in 3D, cartoon style: toon shading, ink outlines, a glass bell with a bold rim.
// Procedural, no model files. Works with Three.js r150+.
// makeGlasshouse(THREE, { canvas, width, height, OrbitControls }, opts)
function makeGlasshouse(THREE, env, opts) {
  opts = Object.assign({ stage: 3, health: 'ok', species: 'linden', panes: [1, 1, 1, 1, 1], dew: 3, rotate: true, frame: 'view', look: 'day', cy: 262, s: 1.08 }, opts || {});
  const { canvas, width, height } = env;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: !!opts.preserve });
  renderer.setPixelRatio(Math.min(2, (typeof devicePixelRatio !== 'undefined' ? devicePixelRatio : 1)));
  renderer.setSize(width, height, false);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const scene = new THREE.Scene();
  // warm backdrop on a big sphere, so the camera can turn
  const bgTex = (() => { const c = document.createElement('canvas'); c.width = 4; c.height = 256; const g = c.getContext('2d'); const l = g.createLinearGradient(0, 0, 0, 256); l.addColorStop(0, '#0d0905'); l.addColorStop(.42, '#2e1f0c'); l.addColorStop(.55, '#3d2a11'); l.addColorStop(1, '#0d0905'); g.fillStyle = l; g.fillRect(0, 0, 4, 256); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t; })();
  const nightTex = (() => { const c = document.createElement('canvas'); c.width = 4; c.height = 256; const g = c.getContext('2d'); const l = g.createLinearGradient(0, 0, 0, 256); l.addColorStop(0, '#070917'); l.addColorStop(.45, '#151a3a'); l.addColorStop(.55, '#1b2148'); l.addColorStop(1, '#06070f'); g.fillStyle = l; g.fillRect(0, 0, 4, 256); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t; })();
  const dawnTex = (() => { const c = document.createElement('canvas'); c.width = 4; c.height = 256; const g = c.getContext('2d'); const l = g.createLinearGradient(0, 0, 0, 256); l.addColorStop(0, '#f6ead2'); l.addColorStop(.5, '#fbe7bd'); l.addColorStop(1, '#eedcbc'); g.fillStyle = l; g.fillRect(0, 0, 4, 256); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t; })();
  const sky = new THREE.Mesh(new THREE.SphereGeometry(30, 32, 16), new THREE.MeshBasicMaterial({ map: bgTex, side: THREE.BackSide, depthWrite: false }));
  scene.add(sky);

  const camera = new THREE.PerspectiveCamera(34, width / height, 0.1, 100);
  camera.position.set(0, 1.9, 5.6);
  // 'svg': place the dome where the 2D phone screens drew it (cy, s in a 360×780 frame)
  function frameCam(w, h) {
    camera.aspect = w / h;
    if (opts.frame === 'svg') {
      const sc = opts.s || 1.08, zoom = 0.93 * sc, f = ((opts.cy || 262) - 61 * sc) / 780;
      camera.fov = 36; camera.position.set(0, 2.0, 14.5); camera.lookAt(0, 0.8, 0);
      camera.setViewOffset(w * zoom, h * zoom, (w * zoom - w) / 2, h * zoom / 2 - f * h, w, h);
    } else camera.clearViewOffset();
    camera.updateProjectionMatrix();
  }
  frameCam(width, height);

  // ---------- toon helpers ----------
  const grad = new THREE.DataTexture(new Uint8Array([110, 110, 110, 255, 190, 190, 190, 255, 255, 255, 255, 255]), 3, 1, THREE.RGBAFormat);
  grad.minFilter = grad.magFilter = THREE.NearestFilter; grad.needsUpdate = true;
  const toon = (c, extra) => new THREE.MeshToonMaterial(Object.assign({ color: c, gradientMap: grad }, extra || {}));
  const INK_COLOR = new THREE.Color(0x24160a);
  const inkMat = (w) => new THREE.ShaderMaterial({
    uniforms: { w: { value: w }, c: { value: INK_COLOR } }, side: THREE.BackSide,
    vertexShader: 'uniform float w; void main(){ vec3 p = position + normalize(normal) * w; gl_Position = projectionMatrix * modelViewMatrix * vec4(p, 1.0); }',
    fragmentShader: 'uniform vec3 c; void main(){ gl_FragColor = vec4(c, 1.0); }',
  });
  // an outline shell on every mesh under root (thickness in world units)
  function ink(root, w) {
    root.updateMatrixWorld(true);
    const list = []; root.traverse(o => { if (o.isMesh && !o.userData.noInk && !o.userData.isInk) list.push(o); });
    const v = new THREE.Vector3();
    list.forEach(m => { m.getWorldScale(v); const sc = (v.x + v.y + v.z) / 3 || 1; const o = new THREE.Mesh(m.geometry, inkMat(w / sc)); o.userData.isInk = true; m.add(o); });
  }

  // ---------- light ----------
  scene.add(new THREE.HemisphereLight(0xfff1d0, 0x3a2a14, 1.1));
  const key = new THREE.DirectionalLight(0xfff0d0, 2.2);
  key.position.set(2.5, 6, 3.5); key.castShadow = true; key.shadow.mapSize.set(1024, 1024);
  key.shadow.camera.left = key.shadow.camera.bottom = -2.5; key.shadow.camera.right = key.shadow.camera.top = 2.5; key.shadow.bias = -0.001;
  scene.add(key);
  const inner = new THREE.PointLight(0xffc35a, 1.6, 3, 1.6); inner.position.set(0, 0.9, 0.3); scene.add(inner);

  // cartoon sun rays fanning down from above
  const rayMat = new THREE.MeshBasicMaterial({ color: 0xffd36a, transparent: true, opacity: 0.06, depthWrite: false, side: THREE.DoubleSide });
  for (let i = 0; i < 9; i++) { const a = (i - 4) * 0.17; const r = new THREE.Mesh(new THREE.PlaneGeometry(0.36, 9), rayMat); r.position.set(Math.sin(a) * 4.5, 5.2 - 4.5 * Math.cos(a) + 0.6, -2.4); r.rotation.z = -a; scene.add(r); }
  const haloTex = (() => { const c = document.createElement('canvas'); c.width = c.height = 256; const g = c.getContext('2d'); const r = g.createRadialGradient(128, 128, 0, 128, 128, 128); r.addColorStop(0, 'rgba(255,214,110,.75)'); r.addColorStop(.5, 'rgba(255,190,80,.25)'); r.addColorStop(1, 'rgba(255,190,80,0)'); g.fillStyle = r; g.fillRect(0, 0, 256, 256); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t; })();
  const halo = new THREE.Mesh(new THREE.PlaneGeometry(4.6, 4.6), new THREE.MeshBasicMaterial({ map: haloTex, transparent: true, depthWrite: false }));
  halo.position.set(0, 1.2, -1.6); scene.add(halo);

  let glassMat = null;
  const moon = new THREE.Group();
  const moonDisc = new THREE.Mesh(new THREE.CircleGeometry(0.42, 40), new THREE.MeshBasicMaterial({ color: 0xf4f1dc }));
  const moonBite = new THREE.Mesh(new THREE.CircleGeometry(0.4, 40), new THREE.MeshBasicMaterial({ color: 0x151a3a })); moonBite.position.set(0.17, 0.12, 0.01);
  moon.add(moonDisc, moonBite); moon.position.set(1.45, 1.75, -1.2); moon.scale.setScalar(.6); moon.visible = false; scene.add(moon);
  const hemi = scene.children.find(o => o.isHemisphereLight);
  function applyLook() {
    const night = opts.look === 'night', light = opts.look === 'light';
    sky.material.map = night ? nightTex : light ? dawnTex : bgTex; sky.material.needsUpdate = true;
    if (light) rayMat.color.set(0xffffff); else rayMat.color.set(0xffd36a);
    moon.visible = night; rayMat.opacity = night ? 0.025 : light ? 0.25 : 0.06;
    hemi.color.set(night ? 0x9fb0ff : 0xfff1d0); hemi.groundColor.set(night ? 0x141a33 : 0x3a2a14); hemi.intensity = night ? 0.75 : 1.1;
    key.color.set(night ? 0xc8d2ff : 0xfff0d0); key.intensity = night ? 1.2 : 2.2;
    halo.material.color.set(night ? 0x9fb0ff : 0xffffff);
    inner.color.set(night ? 0xb8c4ff : 0xffc35a);
    if (glassMat) { glassMat.uniforms.rimc.value.set(opts.closed ? 0xffd36a : night ? 0xdfe6ff : 0xfff4d6); }
  }
  // ---------- the stone ----------
  const base = new THREE.Group(); scene.add(base);
  const stoneProfile = [[0, -0.5], [1.0, -0.5], [1.08, -0.44], [1.12, -0.12], [1.2, -0.06], [1.22, 0.0], [1.16, 0.05], [0, 0.05]].map(([x, y]) => new THREE.Vector2(x, y));
  const stone = new THREE.Mesh(new THREE.LatheGeometry(stoneProfile, 72), toon(0xa08c72));
  stone.receiveShadow = true; stone.castShadow = true; base.add(stone);
  const band = new THREE.Mesh(new THREE.TorusGeometry(1.125, 0.035, 10, 96), toon(0xf2c14e)); band.rotation.x = Math.PI / 2; band.position.y = -0.3; base.add(band);
  const soil = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, 0.06, 48), toon(0x6b4426)); soil.position.y = 0.07; soil.receiveShadow = true; base.add(soil);
  for (let i = 0; i < 14; i++) { const a = i * 2.39, r = 0.3 + (i * 37 % 45) / 100; const p = new THREE.Mesh(new THREE.SphereGeometry(0.04 + (i % 3) * 0.012, 10, 8), toon(i % 2 ? 0xb9a588 : 0x8e7a62)); p.scale.y = .6; p.position.set(Math.cos(a) * r, 0.1, Math.sin(a) * r); base.add(p); }
  ink(base, 0.018);
  const floor = new THREE.Mesh(new THREE.CircleGeometry(3, 48), new THREE.ShadowMaterial({ opacity: 0.35 }));
  floor.rotation.x = -Math.PI / 2; floor.position.y = -0.5; floor.receiveShadow = true; scene.add(floor);

  // ---------- the glass bell ----------
  const R = 0.88, WALL = 1.0;
  const domePts = [new THREE.Vector2(R, 0.06)];
  for (let i = 0; i <= 8; i++) domePts.push(new THREE.Vector2(R, 0.06 + WALL * i / 8));
  for (let i = 1; i <= 24; i++) { const a = (i / 24) * Math.PI / 2; domePts.push(new THREE.Vector2(Math.cos(a) * R, 0.06 + WALL + Math.sin(a) * R * 0.98)); }
  const domeTop = 0.06 + WALL + R * 0.98;
  // cartoon glass: nearly clear in the middle, a crisp bright rim at the silhouette
  glassMat = new THREE.ShaderMaterial({
    transparent: true, depthWrite: false, side: THREE.DoubleSide,
    uniforms: { tint: { value: new THREE.Color(0xd8f3ff) }, rimc: { value: new THREE.Color(0xfff4d6) } },
    vertexShader: 'varying vec3 vN; varying vec3 vV; void main(){ vec4 mv = modelViewMatrix * vec4(position,1.0); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); gl_Position = projectionMatrix * mv; }',
    fragmentShader: 'uniform vec3 tint; uniform vec3 rimc; varying vec3 vN; varying vec3 vV; void main(){ float f = 1.0 - abs(dot(normalize(vN), normalize(vV))); float rim = smoothstep(0.80, 0.86, f); float mid = smoothstep(0.45, 0.5, f) * 0.5; vec3 col = mix(tint, rimc, rim); gl_FragColor = vec4(col, 0.06 + mid * 0.12 + rim * 0.6); }',
  });
  // two white shine streaks, the classic cartoon glass highlight
  const shine = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.85, depthWrite: false });
  const domeR = (y) => y <= 0.06 + WALL ? R : Math.sqrt(Math.max(0, 1 - Math.pow((y - 0.06 - WALL) / (R * 0.98), 2))) * R;
  const streak = (a0, a1, y0, y1, w) => { const pts = []; for (let i = 0; i <= 14; i++) { const t = i / 14, y = y0 + (y1 - y0) * t, a = a0 + (a1 - a0) * t, rr = domeR(y) * 1.012; pts.push(new THREE.Vector3(Math.sin(a) * rr, y, Math.cos(a) * rr)); } const m = new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 28, w, 6), shine); m.renderOrder = 4; return m; };
  const shines = new THREE.Group(); shines.add(streak(-0.78, -0.6, 0.3, 1.55, 0.024), streak(-0.5, -0.4, 0.5, 1.0, 0.013)); scene.add(shines);
  // five panes: chunky gold seams; an open pane glows amber
  const gold = toon(0xf2c14e);
  const paneGroup = new THREE.Group(); scene.add(paneGroup);
  const seamCurve = (ang) => new THREE.CatmullRomCurve3(domePts.filter((_, i) => i % 2 === 0).map(v => new THREE.Vector3(Math.cos(ang) * v.x * 1.005, v.y, Math.sin(ang) * v.x * 1.005)));
  function buildPanes(panes) {
    while (paneGroup.children.length) paneGroup.remove(paneGroup.children[0]);
    for (let i = 0; i < 5; i++) {
      const a0 = -Math.PI / 2 + i * (2 * Math.PI / 5);
      paneGroup.add(new THREE.Mesh(new THREE.TubeGeometry(seamCurve(a0), 40, 0.013, 6), gold));
      // each pane is its own piece of glass (LatheGeometry phi runs from +z towards +x)
      const phi0 = Math.PI / 2 - a0 - 2 * Math.PI / 5;
      const glass = new THREE.Mesh(new THREE.LatheGeometry(domePts, 20, phi0, 2 * Math.PI / 5), glassMat);
      glass.renderOrder = 3; glass.userData.noInk = true;
      if (panes[i]) paneGroup.add(glass);
      else {
        // an open pane swings out on its seam like a little door, edged in amber
        const hx = Math.cos(a0 + 2 * Math.PI / 5) * R, hz = Math.sin(a0 + 2 * Math.PI / 5) * R;
        const hinge = new THREE.Group(); hinge.position.set(hx, 0, hz); hinge.rotation.y = -1.05;
        const amber = glassMat.clone(); amber.uniforms.tint.value = new THREE.Color(0xffb86a); amber.uniforms.rimc.value = new THREE.Color(0xffa040); glass.material = amber;
        glass.position.set(-hx, 0, -hz); hinge.add(glass);
        const edge = new THREE.Mesh(new THREE.TubeGeometry(seamCurve(a0), 40, 0.016, 6), toon(0xff9a3c)); edge.position.set(-hx, 0, -hz); edge.userData.noInk = true; hinge.add(edge);
        paneGroup.add(hinge);
        const glow = new THREE.PointLight(0xff8a3c, 1.4, 1.6, 2); glow.position.set(Math.cos(a0 + Math.PI / 5) * R * .7, 0.7, Math.sin(a0 + Math.PI / 5) * R * .7); paneGroup.add(glow);
      }
      const mid = a0 + Math.PI / 5;
      const dot = new THREE.Mesh(new THREE.SphereGeometry(0.05, 14, 10), new THREE.MeshBasicMaterial({ color: panes[i] ? 0x8fe39a : 0xffb366 }));
      dot.position.set(Math.cos(mid) * 1.2, -0.15, Math.sin(mid) * 1.2); paneGroup.add(dot);
    }
    ink(paneGroup, 0.008);
  }
  buildPanes(opts.panes);
  const ring = new THREE.Group(); scene.add(ring);
  const baseRing = new THREE.Mesh(new THREE.TorusGeometry(R + 0.01, 0.035, 10, 96), gold); baseRing.rotation.x = Math.PI / 2; baseRing.position.y = 0.07; ring.add(baseRing);
  const knob = new THREE.Mesh(new THREE.SphereGeometry(0.09, 24, 16), gold); knob.position.y = domeTop + 0.07; ring.add(knob);
  const knobStem = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.05, 0.06, 12), gold); knobStem.position.y = domeTop + 0.01; ring.add(knobStem);
  ink(ring, 0.012);

  // ---------- the plant ----------
  const plant = new THREE.Group(); plant.position.y = 0.1; plant.scale.setScalar(1.3); scene.add(plant);
  const leafGreen = [0x8fe06a, 0x6fcf5a];
  const straw = [0xe8d58a, 0xcbb062];
  function leafGeo() {
    const s = new THREE.Shape(); s.moveTo(0, 0); s.bezierCurveTo(0.06, 0.16, 0.32, 0.18, 0.44, 0.03); s.bezierCurveTo(0.32, -0.08, 0.1, -0.08, 0, 0);
    const g = new THREE.ExtrudeGeometry(s, { depth: 0.02, bevelEnabled: true, bevelThickness: 0.014, bevelSize: 0.012, bevelSegments: 3, curveSegments: 18 });
    const p = g.attributes.position; for (let i = 0; i < p.count; i++) { const x = p.getX(i), y = p.getY(i); p.setZ(i, p.getZ(i) + 0.3 * y * y + 0.14 * x * x); }
    g.computeVertexNormals(); return g;
  }
  const LEAF = leafGeo();
  const bark = toon(0x8a5a34);
  function addLeaf(parent, y, ang, size, tilt, cols, k) {
    const l = new THREE.Mesh(LEAF, toon(cols[k % 2], { side: THREE.DoubleSide }));
    l.scale.setScalar(size); l.position.y = y; l.rotation.set(-Math.PI / 2 + 0.75 + tilt, 0, 0); l.castShadow = true;
    const vein = new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3([new THREE.Vector3(0.03, 0.0, 0.05), new THREE.Vector3(0.2, 0.05, 0.075), new THREE.Vector3(0.37, 0.03, 0.08)]), 10, 0.006, 5), new THREE.MeshBasicMaterial({ color: 0xd6f7b8 }));
    vein.userData.noInk = true; l.add(vein);
    const holder = new THREE.Group(); holder.rotation.y = ang; holder.add(l); parent.add(holder); return holder;
  }
  function stem(h, r, bend, color) {
    const c = new THREE.CatmullRomCurve3([new THREE.Vector3(0, 0, 0), new THREE.Vector3(bend * .3, h * .35, 0), new THREE.Vector3(-bend * .2, h * .7, bend * .2), new THREE.Vector3(bend * .1, h, 0)]);
    const m = new THREE.Mesh(new THREE.TubeGeometry(c, 30, r, 10), toon(color || 0x5cbf5a)); m.castShadow = true; return m;
  }
  const blob = (parent, x, y, z, r, c) => { const m = new THREE.Mesh(new THREE.SphereGeometry(r, 28, 20), toon(c)); m.position.set(x, y, z); m.castShadow = true; parent.add(m); return m; };
  const trunk = (parent, h, r) => { const m = new THREE.Mesh(new THREE.CylinderGeometry(r * .65, r, h, 14), bark); m.position.y = h / 2; m.castShadow = true; parent.add(m); };
  function speciesTree(g, sp, H) {
    if (sp === 'winter') { trunk(g, H * .28, H * .05); [[.2, .44], [.4, .35], [.58, .26], [.74, .17]].forEach(([y, w]) => { const c = new THREE.Mesh(new THREE.ConeGeometry(w * H, H * .34, 32, 1), toon(0x3a9a5a)); c.position.y = y * H + H * .17; g.add(c); const snow = new THREE.Mesh(new THREE.ConeGeometry(w * H * .62, H * .16, 32, 1), toon(0xf4f8ff)); snow.position.y = y * H + H * .26; g.add(snow); }); return; }
    if (sp === 'cedar') { trunk(g, H * .7, H * .06); [[.34, .44], [.5, .37], [.65, .29], [.79, .2], [.91, .11]].forEach(([y, w], i) => { const d = new THREE.Mesh(new THREE.CylinderGeometry(w * H * .85, w * H, H * .09, 28), toon(i % 2 ? 0x4f9a86 : 0x3f8a76)); d.position.y = y * H; d.castShadow = true; g.add(d); }); return; }
    if (sp === 'willow') {
      trunk(g, H * .55, H * .075);
      const crown = new THREE.Mesh(new THREE.SphereGeometry(H * .34, 28, 18, 0, Math.PI * 2, 0, Math.PI * .55), toon(0x9cc86a)); crown.scale.y = .65; crown.position.y = H * .66; g.add(crown);
      for (let i = 0; i < 16; i++) { const a = i / 16 * Math.PI * 2, r = H * .3, len = H * (.32 + (i % 3) * .07); const c = new THREE.CatmullRomCurve3([new THREE.Vector3(Math.cos(a) * r * .8, H * .7, Math.sin(a) * r * .8), new THREE.Vector3(Math.cos(a) * r * 1.08, H * .62, Math.sin(a) * r * 1.08), new THREE.Vector3(Math.cos(a) * r * 1.12, H * .7 - len, Math.sin(a) * r * 1.12)]); g.add(new THREE.Mesh(new THREE.TubeGeometry(c, 10, H * .018, 6), toon(i % 2 ? 0xa8d276 : 0x8fbf5e))); }
      return;
    }
    if (sp === 'fern') {
      for (let i = 0; i < 7; i++) { const a = i / 7 * Math.PI * 2, r = H * (.34 + (i % 2) * .08), up = H * (.62 + (i % 3) * .1); const c = new THREE.CatmullRomCurve3([new THREE.Vector3(0, 0, 0), new THREE.Vector3(Math.cos(a) * r * .25, up * .7, Math.sin(a) * r * .25), new THREE.Vector3(Math.cos(a) * r * .8, up, Math.sin(a) * r * .8), new THREE.Vector3(Math.cos(a) * r, up * .82, Math.sin(a) * r)]); g.add(new THREE.Mesh(new THREE.TubeGeometry(c, 20, H * .03, 6), toon(0x8fd3c3))); const tip = new THREE.Mesh(new THREE.SphereGeometry(H * .045, 12, 10), new THREE.MeshBasicMaterial({ color: 0xdff2ff })); tip.position.set(Math.cos(a) * r, up * .82, Math.sin(a) * r); tip.userData.noInk = true; g.add(tip); }
      return;
    }
    if (sp === 'olive') { trunk(g, H * .5, H * .08); [[0, .66, 0, .2], [-.18, .58, .1, .15], [.2, .6, -.06, .15], [.04, .8, .12, .14], [-.08, .78, -.14, .13], [.16, .76, .1, .12]].forEach(([x, y, z, r], i) => blob(g, x * H, y * H, z * H, r * H, i % 2 ? 0xb5c394 : 0x9cad7c)); for (let i = 0; i < 9; i++) { const a = i * 2.4; blob(g, Math.cos(a) * .22 * H, (.6 + (i % 3) * .08) * H, Math.sin(a) * .22 * H, H * .025, 0x4a5a33); } return; }
    if (sp === 'pine') { trunk(g, H * .28, H * .05); [[.2, .44], [.4, .35], [.58, .26], [.74, .17]].forEach(([y, w], i) => { const c = new THREE.Mesh(new THREE.ConeGeometry(w * H, H * .34, 32, 1), toon(i % 2 ? 0x46b866 : 0x3aa35a)); c.position.y = y * H + H * .17; c.castShadow = true; g.add(c); }); return; }
    trunk(g, H * .55, H * .065);
    const col = sp === 'cherry' ? [0xffb8cf, 0xff9dbd] : sp === 'oak' ? [0x6fc45a, 0x5aae4a] : sp === 'lemon' ? [0x78cf62, 0x62bb52] : [0x9fe48a, 0x86d672];
    [[0, .72, 0, .28], [-.2, .6, .08, .2], [.2, .62, -.05, .2], [.02, .62, .21, .19], [0, .92, 0, .17], [-.05, .66, -.21, .18]].forEach(([x, y, z, r], i) => blob(g, x * H, y * H, z * H, r * H * (sp === 'oak' ? 1.12 : 1), col[i % 2]));
    if (sp === 'cherry' || sp === 'lemon') for (let i = 0; i < 14; i++) { const a = i * 2.4, b = (i % 5) / 5; const d = blob(g, Math.cos(a) * .3 * H, (.6 + b * .3) * H, Math.sin(a) * .3 * H, H * (sp === 'lemon' ? .04 : .03), sp === 'cherry' ? 0xffffff : 0xffd84a); d.userData.noInk = sp === 'cherry'; }
  }
  function buildPlant(stage, health, species) {
    while (plant.children.length) plant.remove(plant.children[0]);
    plant.rotation.set(0, 0, 0);
    const cols = health === 'wilt' ? straw : leafGreen;
    if (health === 'dead') {
      // a dry stem, bent over, with curled brown leaves fallen on the soil
      const c = new THREE.CatmullRomCurve3([new THREE.Vector3(0, 0, 0), new THREE.Vector3(0.02, 0.32, 0), new THREE.Vector3(0.14, 0.52, 0.02), new THREE.Vector3(0.32, 0.48, 0.04), new THREE.Vector3(0.38, 0.32, 0.05)]);
      plant.add(new THREE.Mesh(new THREE.TubeGeometry(c, 30, 0.032, 10), toon(0xa27a4c)));
      const tipLeaf = new THREE.Mesh(LEAF, toon(0x9c7444, { side: THREE.DoubleSide })); tipLeaf.scale.setScalar(.7); tipLeaf.rotation.set(0.4, 0.3, -1.9); tipLeaf.position.set(0.38, 0.32, 0.05); plant.add(tipLeaf);
      [[-0.34, 0.14, 0.6], [0.2, -0.22, -1.2]].forEach(([x, z, r]) => { const l = new THREE.Mesh(LEAF, toon(0x8c6438, { side: THREE.DoubleSide })); l.scale.setScalar(.85); l.rotation.set(-Math.PI / 2 + 0.25, 0, r); l.position.set(x, 0.04, z); plant.add(l); });
      ink(plant, 0.01); return;
    }
    if (stage < 0) return;
    if (stage === 0) {
      const seed = new THREE.Mesh(new THREE.SphereGeometry(0.08, 20, 14), toon(0xd4a060)); seed.scale.set(1.25, .8, 1); seed.position.y = 0.03; plant.add(seed);
      const sh = stem(0.14, 0.016, 0.08); sh.position.set(0.04, 0.05, 0); sh.rotation.z = -0.5; plant.add(sh);
      ink(plant, 0.01); return;
    }
    if (stage >= 6) { speciesTree(plant, species, 1.1); ink(plant, 0.012); return; }
    const H = [0, 0.38, 0.62, 0.82, 0.98, 1.06, 1.2][stage];
    plant.add(stem(H, 0.026 + stage * 0.004, 0.06));
    const L = [];
    L.push(addLeaf(plant, H - 0.02, 0.4, 0.95, 0.35, cols, 0));
    if (stage >= 2) L.push(addLeaf(plant, H - 0.1, Math.PI + 0.2, 1.0, 0.3, cols, 1));
    if (stage >= 3) { L.push(addLeaf(plant, H * .55, -0.45, 1.1, 0.1, cols, 1)); L.push(addLeaf(plant, H * .48, Math.PI + 0.5, 1.05, 0.08, cols, 0)); }
    if (stage >= 4) { const bud = new THREE.Mesh(new THREE.SphereGeometry(0.07, 18, 14), toon(0xff9f9f)); bud.scale.y = 1.4; bud.position.y = H + 0.07; plant.add(bud); }
    if (stage >= 5) { for (let i = 0; i < 6; i++) { const p = new THREE.Mesh(new THREE.SphereGeometry(0.07, 14, 10), toon(0xffd36a)); p.scale.set(.7, .3, 1.4); const h = new THREE.Group(); h.rotation.y = i * Math.PI / 3; p.position.set(0, 0, 0.09); h.add(p); h.position.y = H + 0.12; plant.add(h); } const c = new THREE.Mesh(new THREE.SphereGeometry(0.05, 12, 10), toon(0xd0802f)); c.position.y = H + 0.13; plant.add(c); }
    if (health === 'wilt') { plant.rotation.z = -0.55; plant.rotation.x = 0.15; L.forEach(h => h.children[0].rotation.x += 0.7); }
    ink(plant, 0.011);
    // dewdrops: round, blue, with a white dot, no outline
    if (health !== 'wilt') for (let i = 0; i < Math.min(opts.dew, L.length); i++) {
      const d = new THREE.Mesh(new THREE.SphereGeometry(0.035, 16, 12), new THREE.MeshBasicMaterial({ color: 0xa8dcff })); d.position.set(0.22, 0.03, 0.06); d.userData.noInk = true;
      const dot = new THREE.Mesh(new THREE.SphereGeometry(0.011, 8, 6), new THREE.MeshBasicMaterial({ color: 0xffffff })); dot.position.set(-0.012, 0.016, 0.02); d.add(dot);
      L[i].children[0].add(d);
    }
  }
  buildPlant(opts.stage, opts.health, opts.species);

  // ---------- sparkles in the light ----------
  const spTex = (() => { const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d'); g.fillStyle = '#fff'; g.beginPath(); g.moveTo(32, 2); g.quadraticCurveTo(36, 28, 62, 32); g.quadraticCurveTo(36, 36, 32, 62); g.quadraticCurveTo(28, 36, 2, 32); g.quadraticCurveTo(28, 28, 32, 2); g.fill(); return new THREE.CanvasTexture(c); })();
  const N = 50, pos = new Float32Array(N * 3), seeds = [];
  for (let i = 0; i < N; i++) { pos[i * 3] = (Math.random() - .5) * 5; pos[i * 3 + 1] = Math.random() * 3.6 - 0.2; pos[i * 3 + 2] = (Math.random() - .5) * 3 - .8; seeds.push(Math.random() * 6.28); }
  const dustGeo = new THREE.BufferGeometry(); dustGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({ map: spTex, color: 0xffe08a, size: 0.09, transparent: true, opacity: 0.85, depthWrite: false, alphaTest: 0.05 }));
  scene.add(dust);

  // ---------- controls and render ----------
  let controls = null;
  if (env.OrbitControls) { controls = new env.OrbitControls(camera, renderer.domElement); controls.target.set(0, 0.95, 0); controls.enableDamping = true; controls.minDistance = 3; controls.maxDistance = 8; controls.maxPolarAngle = Math.PI * 0.55; controls.autoRotate = opts.rotate; controls.autoRotateSpeed = 0.6; controls.enablePan = false; }
  camera.lookAt(0, 0.95, 0);
  applyLook();
  const clock = new THREE.Clock();
  let alive = !opts.still, bounceAt = -1;
  function frame() {
    if (!alive) return;
    requestAnimationFrame(frame);
    if (opts.paused) return;
    const t = clock.getElapsedTime();
    // growth bounce: a cartoon squash and stretch when a stage arrives
    if (bounceAt >= 0) { const k = t - bounceAt; if (k > 1.2) bounceAt = -1; else { const e = Math.exp(-k * 4) * Math.sin(k * 14); plant.scale.set(1.3 * (1 - e * .12), 1.3 * (1 + e * .18), 1.3 * (1 - e * .12)); } }
    // breathe: in for 4 s, out for 6 s; the glow and the halo follow it
    if (opts.breathe) { const c = t % 10, b = c < 4 ? Math.sin(c / 4 * Math.PI / 2) : Math.cos((c - 4) / 6 * Math.PI / 2); halo.scale.setScalar(1 + b * .22); inner.intensity = 1 + b * 2.2; }
    if (opts.health !== 'dead') { plant.rotation.y = Math.sin(t * .5) * .08; if (opts.health !== 'wilt') { plant.rotation.z = Math.sin(t * .8) * .025; plant.position.y = 0.1 + Math.sin(t * 1.6) * 0.006; } }
    const p = dustGeo.attributes.position;
    for (let i = 0; i < N; i++) { let y = p.getY(i) + 0.0018; if (y > 3.4) y = -0.2; p.setY(i, y); p.setX(i, p.getX(i) + Math.sin(t + seeds[i]) * 0.0009); }
    p.needsUpdate = true;
    if (!opts.breathe) inner.intensity += ((opts.health === 'dead' ? .3 : opts.health === 'wilt' ? .8 : 1.6 + Math.sin(t * 1.3) * .2) - inner.intensity) * .05;
    halo.material.opacity = opts.health === 'dead' ? .35 : opts.health === 'wilt' ? .65 : 1;
    if (controls) controls.update();
    renderer.render(scene, camera);
  }
  if (alive) frame();
  return {
    snap(o, w, h) {
      Object.assign(opts, { health: 'ok', species: 'linden', panes: [1, 1, 1, 1, 1], dew: 0, look: 'day', closed: false, cy: 262, s: 1.08 }, o);
      buildPanes(opts.panes); buildPlant(opts.stage, opts.health, opts.species); applyLook();
      plant.scale.setScalar(1.3); plant.rotation.y = 0; frameCam(w || width, h || height);
      renderer.render(scene, camera); return canvas.toDataURL('image/jpeg', 0.86);
    },
    set(o) { const grew = 'stage' in o && o.stage > opts.stage; Object.assign(opts, o); if (grew) bounceAt = clock.getElapsedTime(); if ('look' in o || 'closed' in o) applyLook(); if ('panes' in o) buildPanes(opts.panes); if ('stage' in o || 'health' in o || 'species' in o || 'dew' in o) buildPlant(opts.stage, opts.health, opts.species); if ('rotate' in o && controls) controls.autoRotate = o.rotate; },
    resize(w, h) { renderer.setSize(w, h, false); frameCam(w, h); },
    stop() { alive = false; },
    pause(v) { opts.paused = v; },
  };
}
if (typeof window !== 'undefined') window.makeGlasshouse = makeGlasshouse;
