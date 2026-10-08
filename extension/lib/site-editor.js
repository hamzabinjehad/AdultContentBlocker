import { canonicalHost } from "./policy.js";
import { parseDomains, restrictionsActive } from "./settings.js";
import { renderIcons } from "./icons.js";
import { t } from "./i18n.js";

// The bulk field is the draft source, including invalid lines awaiting repair.
export function siteEditor(field, key, getState) {
  const root = document.createElement("div");
  root.className = "site-editor";
  const entry = document.createElement("form");
  entry.className = "site-entry";
  const input = document.createElement("input");
  input.type = "text"; input.dir = "ltr"; input.spellcheck = false;
  input.autocomplete = "off"; input.id = `${field.id}Entry`;
  const add = document.createElement("button");
  add.type = "submit";
  add.innerHTML = '<span data-icon="plus" aria-hidden="true"></span><span></span>';
  entry.append(input, add);
  const feedback = document.createElement("p");
  feedback.className = "msg"; feedback.id = `${field.id}Feedback`;
  feedback.setAttribute("role", "status");
  input.setAttribute("aria-describedby", feedback.id);
  const toolbar = document.createElement("div");
  toolbar.className = "site-toolbar";
  const search = document.createElement("input");
  search.type = "search"; search.dir = "ltr"; search.id = `${field.id}Search`;
  const count = document.createElement("span");
  toolbar.append(search, count);
  const list = document.createElement("ul");
  list.className = "site-list";
  const bulk = document.createElement("details");
  const summary = document.createElement("summary");
  bulk.className = "bulk-editor";
  field.before(root);
  bulk.append(summary, field);
  root.append(entry, feedback, toolbar, list, bulk);
  renderIcons(root);
  function changed() {
    field.dispatchEvent(new Event("input", { bubbles: true }));
  }
  function editable() { return !!getState() && !getState().appPresent && !field.disabled; }
  entry.onsubmit = (event) => {
    event.preventDefault();
    if (add.disabled) return;
    const host = canonicalHost(input.value.trim());
    if (!host) {
      input.setAttribute("aria-invalid", "true");
      feedback.className = "msg error";
      feedback.textContent = t("opt.sites.invalid"); input.focus(); return;
    }
    const duplicate = parseDomains(field.value).domains.includes(host);
    if (!duplicate) { field.value = [field.value.trim(), host].filter(Boolean).join("\n"); changed(); }
    input.value = ""; input.removeAttribute("aria-invalid");
    feedback.className = "msg";
    feedback.textContent = t(duplicate ? "opt.sites.duplicate" : "opt.sites.added", host);
    input.focus();
  };
  input.oninput = () => { input.removeAttribute("aria-invalid"); feedback.textContent = ""; };
  search.oninput = refresh;
  field.addEventListener("input", refresh);
  function refresh() {
    const state = getState();
    const locked = state && restrictionsActive(state);
    input.disabled = add.disabled = !editable() || (key === "allowlist" && locked);
    input.placeholder = "example.com";
    input.setAttribute("aria-label", t(key === "allowlist" ? "opt.sites.addAllowed" : "opt.sites.addBlocked"));
    add.lastElementChild.textContent = t("opt.sites.add");
    search.placeholder = t("opt.sites.search"); search.setAttribute("aria-label", t("opt.sites.search"));
    summary.textContent = t("opt.sites.bulk");
    const parsed = parseDomains(field.value);
    count.textContent = t("opt.sites.count", parsed.domains.length);
    const matches = parsed.domains.filter(host => host.includes(search.value.trim().toLowerCase()));
    list.replaceChildren();
    for (const host of matches) {
      const row = document.createElement("li");
      const label = document.createElement("span");
      label.textContent = host; label.dir = "ltr";
      const remove = document.createElement("button");
      remove.type = "button"; remove.className = "icon-button";
      remove.title = t("opt.removeAria", host); remove.setAttribute("aria-label", remove.title);
      remove.innerHTML = '<span data-icon="trash-2" aria-hidden="true"></span>';
      remove.disabled = !editable() || (key === "customBlocks" && locked && (state.customBlocks || []).includes(host));
      remove.onclick = () => {
        // Keep malformed bulk entries visible, rather than silently discarding them.
        field.value = field.value.split(/\r?\n/).filter(line => canonicalHost(line.trim()) !== host).join("\n");
        changed();
        const next = list.querySelector("button:not(:disabled)");
        (next || input).focus();
      };
      row.append(label, remove); list.append(row);
    }
    if (!matches.length) {
      const empty = document.createElement("li");
      empty.className = "empty-note";
      empty.textContent = t(search.value ? "opt.sites.noMatches" : "opt.sites.empty"); list.append(empty);
    }
    if (parsed.invalid.length) {
      bulk.open = true;
      field.setAttribute("aria-invalid", "true");
    } else field.removeAttribute("aria-invalid");
    renderIcons(list);
  }
  return { refresh, reveal() { bulk.open = true; field.focus(); } };
}
