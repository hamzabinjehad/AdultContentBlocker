// Runs in an extension page. The status request goes through the real service
// worker and the real offscreen document; a rejected worker sender returns
// state=failed, which is the regression this harness exists to catch.
(async () => {
  const out = document.getElementById("status");
  let last = "no response";
  const state = await new Promise((resolve) => {
    chrome.runtime.sendMessage({ type: "getState" }, (reply) => {
      resolve({ reply, error: chrome.runtime.lastError?.message || null });
    });
  });
  if (!state.reply) {
    out.textContent = JSON.stringify(state, null, 2);
    document.title = "HISN VISUAL FAIL state " + JSON.stringify(state);
    return;
  }
  for (let attempt = 0; attempt < 100; attempt++) {
    try {
      const status = await chrome.runtime.sendMessage({ type: "getVisualStatus" });
      last = JSON.stringify(status);
      if (status && status.available === true && status.state !== "failed"
          && status.state !== "unavailable") {
        out.textContent = JSON.stringify(status, null, 2);
        document.title = "HISN VISUAL PASS " + status.state;
        return;
      }
      if (status && (status.state === "failed" || status.state === "unavailable")) {
        out.textContent = JSON.stringify(status, null, 2);
        document.title = "HISN VISUAL FAIL " + JSON.stringify(status);
        return;
      }
      out.textContent = last;
    } catch (error) {
      out.textContent = String(error);
    }
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  document.title = "HISN VISUAL FAIL timeout " + last;
})();
