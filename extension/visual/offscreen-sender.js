/**
 * Who may drive the offscreen visual engine.
 *
 * The only caller is the extension's own service worker. A content script is
 * rejected by `sender.tab`; an extension page (popup/options) is rejected by
 * its URL. Chromium leaves `sender.url` undefined when the sender is a service
 * worker (w3c/webextensions#851), while Firefox sets the script URL, so both
 * forms are the worker. This was the reason a real Chrome visual layer stayed
 * silent even though the engine and the offscreen document were available.
 */
export function fromBackgroundWorker(sender, extensionId, backgroundUrl) {
  if (!sender || typeof extensionId !== 'string' || !extensionId) return false;
  if (sender.id !== extensionId || sender.tab) return false;
  return sender.url === undefined || sender.url === backgroundUrl;
}
