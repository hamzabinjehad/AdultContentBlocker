import { createVisualEngine } from './engine.bundle.js';
const instance = createVisualEngine({ baseURL: chrome.runtime.getURL('visual/') });
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (message?.target !== 'hisn-visual-engine') return false;
  // A webpage content script cannot directly choose model preferences or
  // cancel another frame's work. Only the extension worker owns this channel.
  if (sender.id !== chrome.runtime.id || sender.tab || sender.url !== chrome.runtime.getURL('background.js')) return false;
  (async () => {
    if (message.operation === 'inspect') return instance.inspect(message.key, message.capture, message.preferences);
    if (message.operation === 'cancel') { instance.cancel(message.key); return { state: 'unknown', reason: 'cancelled' }; }
    if (message.operation === 'cancelPrefix') { instance.cancelPrefix(message.prefix); return { state: 'unknown', reason: 'cancelled' }; }
    if (message.operation === 'status') return instance.status(message.preferences);
    return { state: 'unknown', reason: 'invalid-operation' };
  })().then(respond).catch(() => respond({ state: 'failed', reason: 'inference-failed' }));
  return true;
});
