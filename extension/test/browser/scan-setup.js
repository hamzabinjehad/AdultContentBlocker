const response = await chrome.runtime.sendMessage({ type: "update", patch: { customTerms: ["gardenfixture"], textSensitivity: 80 } });
document.title = response.ok ? "Fixture policy applied" : "Fixture policy failed";
