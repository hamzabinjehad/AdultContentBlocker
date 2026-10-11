(() => {
  if (globalThis.HisnVisualCapture) return;
  const edge = 384;
  function capture(media, doc = document) {
    const width = media.videoWidth || media.naturalWidth;
    const height = media.videoHeight || media.naturalHeight;
    if (!width || !height) return { state: 'unknown', reason: 'media-not-ready' };
    const scale = Math.min(1, edge / Math.max(width, height));
    const canvas = doc.createElement('canvas');
    canvas.width = Math.max(1, Math.round(width * scale));
    canvas.height = Math.max(1, Math.round(height * scale));
    try {
      const context = canvas.getContext('2d', { willReadFrequently: true });
      if (!context) return { state: 'unknown', reason: 'canvas-unavailable' };
      context.drawImage(media, 0, 0, canvas.width, canvas.height);
      const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
      const chunks = [];
      for (let offset = 0; offset < pixels.length; offset += 8192) chunks.push(String.fromCharCode(...pixels.subarray(offset, offset + 8192)));
      const rgba = btoa(chunks.join(''));
      pixels.fill(0);
      return { width: canvas.width, height: canvas.height, rgba };
    } catch { return { state: 'unknown', reason: 'inaccessible-pixels' }; }
    finally { canvas.width = canvas.height = 0; }
  }
  globalThis.HisnVisualCapture = { capture };
})();
