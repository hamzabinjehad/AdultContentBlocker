/** Presentation only: a missing engine reply is not evidence of protection. */
import { t } from "./i18n.js";
import { describeEnforcement } from "./status.js";

export function describeVisual(status, state) {
  if (!status || status.ok === false) return { level: "unknown", text: t("visual.status.checking"), available: false };
  if (status.available !== true || status.state === "unavailable") return { level: "unknown", text: t("visual.status.unavailable"), available: false };
  if (!state || state.ok === false) return { level: "unknown", text: t("visual.status.checking"), available: true };
  const enabled = state?.inspectVisual === true || ["all", "female", "male"].includes(state?.personBlur);
  if (!enabled) return { level: "unknown", text: t("visual.status.disabled"), available: true };
  // A warm model is not proof that this browser installed its scanner. Keep
  // model availability independent so a registration failure never disables
  // the controls needed to repair it, and respect independent layer receipts.
  const enforcement = describeEnforcement(state);
  const visual = enforcement.layers.visual;
  if (visual.level !== "active") return { level: enforcement.status === "failed" ? "partial" : "unknown",
    text: visual.text, available: true };
  if (status.state === "failed") return { level: "partial", text: t("visual.status.failed"), available: true };
  if (status.state === "loading") return { level: "partial", text: t("visual.status.loading"), available: true };
  if (status.state === "waiting") return { level: "partial", text: t("visual.status.waiting"), available: true };
  if (status.state === "ready") return { level: "active", text: t("visual.status.ready"), available: true };
  return { level: "unknown", text: t("visual.status.checking"), available: true };
}
