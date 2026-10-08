const response = await chrome.runtime.sendMessage({ type: "update", patch: { customTerms: ["gardenfixture"] } });
document.title = response.ok ? "Fixture policy applied" : "Fixture policy failed";
