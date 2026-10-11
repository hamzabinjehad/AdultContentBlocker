/** Presentation only: a missing engine reply is not evidence of protection. */
import { t } from "./i18n.js";

export function describeVisual(status, state) {
  if (!status || status.ok === false) return { level: "unknown", text: t("visual.status.checking"), available: false };
  if (status.available !== true || status.state === "unavailable") return { level: "unknown", text: t("visual.status.unavailable"), available: false };
  if (status.state === "failed") return { level: "partial", text: t("visual.status.failed"), available: true };
  if (status.state === "loading") return { level: "partial", text: t("visual.status.loading"), available: true };
  if (status.state === "waiting") return { level: "partial", text: t("visual.status.waiting"), available: true };
  const enabled = state?.inspectVisual === true || ["all", "female", "male"].includes(state?.personBlur);
  if (status.state === "ready" && enabled) return { level: "active", text: t("visual.status.ready"), available: true };
  return { level: "unknown", text: t("visual.status.disabled"), available: true };
}
