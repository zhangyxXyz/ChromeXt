const $ = (selector) => document.querySelector(selector);

const state = {
  ids: [],
  metas: new Map(),
  selectedId: "",
  source: "",
  dirty: false,
  sourceVisible: false,
  skipNextMetaRefresh: false,
};

const els = {
  back: $("#back"),
  batchReinstall: $("#batch-reinstall"),
  delete: $("#delete"),
  details: $("#details"),
  enabledToggle: $("#enabled-toggle"),
  excludeEditor: $("#exclude-editor"),
  exportScripts: $("#export-scripts"),
  grantCount: $("#grant-count"),
  grantList: $("#grant-list"),
  includeEditor: $("#include-editor"),
  includeList: $("#include-list"),
  importFile: $("#import-file"),
  importScripts: $("#import-scripts"),
  list: $("#script-list"),
  matchCount: $("#match-count"),
  matchEditor: $("#match-editor"),
  matchList: $("#match-list"),
  newScript: $("#new-script"),
  reinstall: $("#reinstall"),
  save: $("#save"),
  search: $("#search"),
  source: $("#source"),
  status: $("#status"),
  excludeList: $("#exclude-list"),
  scriptId: $("#script-id"),
  scriptName: $("#script-name"),
  viewSource: $("#view-source"),
};

const fallbackMessages = {
  connecting: "Connecting to ChromeXt...",
  waiting: "Waiting for ChromeXt injection...",
  newScript: "New Script",
};

let messages = fallbackMessages;
let languageSetting = globalThis.__ChromeXtLanguage || "system";
let activeLanguage = resolveLanguage(languageSetting);

function resolveLanguage(value = "system") {
  if (value === "zh" || value === "en") return value;
  return navigator.language.toLowerCase().startsWith("zh") ? "zh" : "en";
}

async function loadMessages() {
  activeLanguage = resolveLanguage(languageSetting);
  if (globalThis.__ChromeXtI18n) {
    const base = globalThis.__ChromeXtI18n.en || {};
    const localized = activeLanguage === "en" ? {} : globalThis.__ChromeXtI18n[activeLanguage] || {};
    messages = { ...fallbackMessages, ...base, ...localized };
    return;
  }
  const load = async (lang) => {
    const response = await fetch(`/i18n/${lang}.json`, { cache: "no-store" });
    if (!response.ok) throw new Error(`Failed to load i18n: ${lang}`);
    return response.json();
  };
  try {
    const base = await load("en");
    const localized = activeLanguage === "en" ? {} : await load(activeLanguage);
    messages = { ...fallbackMessages, ...base, ...localized };
  } catch (error) {
    console.warn(error);
    messages = fallbackMessages;
  }
}

function t(key, values = {}) {
  const template = messages[key] || fallbackMessages[key] || key;
  return template.replace(/\{(\w+)\}/g, (_, name) => values[name] ?? "");
}

function applyLocale() {
  document.documentElement.lang = activeLanguage === "zh" ? "zh-CN" : "en";
  document.querySelectorAll("[data-i18n]").forEach((node) => {
    node.textContent = t(node.dataset.i18n);
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((node) => {
    node.placeholder = t(node.dataset.i18nPlaceholder);
  });
  els.newScript.setAttribute("aria-label", t("newScript"));
}

await loadMessages();
applyLocale();
function waitForChromeXt() {
  return new Promise((resolve) => {
    const started = Date.now();
    let slow = false;
    const timer = setInterval(() => {
      if (globalThis.ChromeXt?.dispatch) {
        clearInterval(timer);
        resolve(globalThis.ChromeXt);
      } else if (!slow && Date.now() - started > 5000) {
        slow = true;
        els.status.textContent = t("waiting");
      }
    }, 50);
  });
}

const ChromeXt = await waitForChromeXt();

function send(action, payload = "") {
  ChromeXt.dispatch(action, payload);
}

const scrollbarTimers = new WeakMap();

function bindAutoScrollbar(node) {
  if (!node || node.dataset.cxScrollbar === "1") return;
  node.dataset.cxScrollbar = "1";
  node.addEventListener(
    "scroll",
    () => {
      node.classList.add("scrolling");
      clearTimeout(scrollbarTimers.get(node));
      scrollbarTimers.set(node, setTimeout(() => node.classList.remove("scrolling"), 700));
    },
    { passive: true }
  );
}

[els.details, els.source, els.matchEditor, els.includeEditor, els.excludeEditor].forEach(bindAutoScrollbar);

ChromeXt.addEventListener("settings", async (event) => {
  languageSetting = event.detail?.language || "system";
  await loadMessages();
  applyLocale();
  if (state.selectedId && state.metas.has(state.selectedId)) renderDetails(state.metas.get(state.selectedId));
  renderList();
});
send("settings", "");

function metaValue(meta = "", key) {
  return meta.match(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "m"))?.[1]?.trim() || "";
}

function metaValues(meta = "", key) {
  return Array.from(meta.matchAll(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "gm"))).map((m) =>
    m[1].trim()
  );
}

function isScriptSourceUrl(value = "") {
  return /^https?:\/\/.+\.user\.js(?:[?#].*)?$/i.test(value);
}

function installUrlFromMeta(meta = "") {
  const keys = ["downloadURL", "installURL", "sourceURL", "url"];
  for (const key of keys) {
    const value = metaValue(meta, key);
    if (/^https?:\/\//i.test(value)) return value;
  }
  const updateUrl = metaValue(meta, "updateURL");
  if (isScriptSourceUrl(updateUrl)) return updateUrl;
  const namespace = metaValue(meta, "namespace");
  if (isScriptSourceUrl(namespace)) return namespace;
  return "";
}

function reinstallableIds(ids = state.ids) {
  return ids.filter((id) => installUrlFromMeta(state.metas.get(id) || ""));
}

function copyInstallUrl(meta, label) {
  const url = installUrlFromMeta(meta);
  if (!url) {
    send("toast", { message: t("noInstallLink") });
    return;
  }
  send("copy", {
    type: "text",
    text: url,
    label: `ChromeXt install link: ${label || "UserScript"}`,
    toast: t("copiedInstallLink"),
  });
}

function addLongPress(target, action) {
  let timer = 0;
  let startX = 0;
  let startY = 0;

  const clear = () => {
    clearTimeout(timer);
    timer = 0;
  };

  target.addEventListener("pointerdown", (event) => {
    if (event.button !== undefined && event.button !== 0) return;
    startX = event.clientX;
    startY = event.clientY;
    clear();
    timer = setTimeout(() => {
      target.dataset.longPressed = "1";
      action(event);
    }, 600);
  });
  target.addEventListener("pointermove", (event) => {
    if (Math.abs(event.clientX - startX) > 10 || Math.abs(event.clientY - startY) > 10) clear();
  });
  ["pointerup", "pointercancel", "pointerleave"].forEach((type) => {
    target.addEventListener(type, clear);
  });
}

function isScriptEnabled(meta = "") {
  return !/^\/\/\s+@(disable|disabled)(\s+.*)?$/m.test(meta);
}

function decodeUnicodeEscapes(value = "") {
  return value.replace(/\\u\{([0-9a-fA-F]+)\}|\\u([0-9a-fA-F]{4})/g, (match, point, bmp) => {
    const codePoint = Number.parseInt(point || bmp, 16);
    if (!Number.isFinite(codePoint)) return match;
    try {
      return String.fromCodePoint(codePoint);
    } catch {
      return match;
    }
  });
}

function scriptNameFromMeta(meta = "") {
  return metaValue(meta, "name") || t("unnamedScript");
}

function renderRuleList(node, values, emptyText) {
  node.textContent = "";
  if (values.length === 0) {
    const empty = document.createElement("span");
    empty.className = "empty-rule";
    empty.textContent = emptyText;
    node.append(empty);
    return;
  }
  values.forEach((value) => {
    const item = document.createElement("code");
    item.textContent = value;
    node.append(item);
  });
}

function renderDetails(meta = "") {
  const matches = metaValues(meta, "match");
  const includes = metaValues(meta, "include");
  const excludes = metaValues(meta, "exclude");
  const grants = metaValues(meta, "grant");
  els.matchCount.textContent = t("matches", { count: matches.length + includes.length });
  els.grantCount.textContent = t("grants", { count: grants.length });
  renderRuleList(els.matchList, matches, t("noMatch"));
  renderRuleList(els.includeList, includes, t("noInclude"));
  renderRuleList(els.excludeList, excludes, t("noExclude"));
  renderRuleList(els.grantList, grants, t("noGrant"));
  els.matchEditor.value = matches.join("\n");
  els.includeEditor.value = includes.join("\n");
  els.excludeEditor.value = excludes.join("\n");
}

function renderList() {
  const needle = els.search.value.trim().toLowerCase();
  els.list.textContent = "";
  const ids = state.ids.filter((id) => {
    const meta = state.metas.get(id) || "";
    return `${id}\n${meta}`.toLowerCase().includes(needle);
  });

  els.batchReinstall.disabled = reinstallableIds(ids).length === 0;
  els.exportScripts.disabled = state.ids.length === 0;

  if (ids.length === 0) {
    els.status.textContent = needle ? t("noSearchResults") : t("noScripts");
    return;
  }

  els.status.textContent = t("scriptCount", { count: ids.length });
  ids.forEach((id) => {
    const meta = state.metas.get(id) || "";
    const row = document.createElement("div");
    row.className = "script-item" + (id === state.selectedId ? " active" : "");
    const button = document.createElement("button");
    button.className = "script-open";
    button.type = "button";
    button.innerHTML = `<strong></strong><span></span>`;
    const name = scriptNameFromMeta(meta);
    button.querySelector("strong").textContent = name;
    button.querySelector("span").textContent = id;
    button.addEventListener("click", (event) => {
      if (button.dataset.longPressed === "1") {
        delete button.dataset.longPressed;
        event.preventDefault();
        return;
      }
      openOverview(id);
    });
    addLongPress(button, () => copyInstallUrl(meta, name));
    row.append(button, createSwitch(isScriptEnabled(meta), (enabled) => toggleScript(id, enabled)));
    els.list.append(row);
  });
}

function createSwitch(checked, onChange) {
  const label = document.createElement("label");
  label.className = "switch";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.checked = checked;
  const track = document.createElement("span");
  input.addEventListener("click", (event) => event.stopPropagation());
  input.addEventListener("change", () => onChange(input.checked));
  label.append(input, track);
  return label;
}

function selectedIdFromHash() {
  const params = new URLSearchParams(location.hash.replace(/^#/, ""));
  return params.get("source") || params.get("script") || "";
}

function setViewHash(kind, id, replace = false) {
  if (!id) return;
  const next = `#${kind}=${encodeURIComponent(id)}`;
  if (location.hash === next) return;
  const stateData = { chromeXtManager: true, kind, id };
  if (replace) history.replaceState(stateData, "", next);
  else history.pushState(stateData, "", next);
}

function showListView(replace = true) {
  setSourceVisible(false);
  document.body.classList.remove("editing");
  if (replace) history.replaceState({ chromeXtManager: true, kind: "list" }, "", location.pathname);
}

function setSourceVisible(visible) {
  state.sourceVisible = visible;
  document.body.classList.toggle("show-source", visible);
  els.viewSource.textContent = visible ? t("details") : t("viewSource");
}

function normalizeRuleEditorValue(value) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function replaceMetaRules(meta, replacements) {
  const consumed = new Set();
  const lines = meta.replace(/\r\n/g, "\n").split("\n");
  const output = [];

  for (const line of lines) {
    const match = line.match(/^\/\/\s+@(match|include|exclude)(\s+.*)?$/);
    if (!match) {
      output.push(line);
      continue;
    }
    const key = match[1];
    if (!consumed.has(key)) {
      replacements[key].forEach((value) => output.push(`// @${key} ${value}`));
      consumed.add(key);
    }
  }

  const endIndex = output.findIndex((line) => line.trim() === "// ==/UserScript==");
  if (endIndex >= 0) {
    ["match", "include", "exclude"].forEach((key) => {
      if (consumed.has(key)) return;
      output.splice(endIndex, 0, ...replacements[key].map((value) => `// @${key} ${value}`));
      consumed.add(key);
    });
  }

  return output.join("\n").replace(/\n*$/, "\n");
}

function setMetaEnabled(meta, enabled) {
  const lines = meta.replace(/\r\n/g, "\n").split("\n");
  const output = lines.filter((line) => !/^\/\/\s+@(disable|disabled)(\s+.*)?$/.test(line));
  if (!enabled) {
    const endIndex = output.findIndex((line) => line.trim() === "// ==/UserScript==");
    if (endIndex >= 0) output.splice(endIndex, 0, "// @disable");
    else output.unshift("// @disable");
  }
  return output.join("\n").replace(/\n*$/, "\n");
}

function saveMeta(id, meta) {
  send("userscript", JSON.stringify({ id, meta }));
}

function requestReinstall(ids) {
  const candidates = reinstallableIds(ids);
  if (candidates.length === 0) {
    els.status.textContent = t("noReinstallableScripts");
    return;
  }
  if (!confirm(t("reinstallConfirm", { count: candidates.length }))) return;
  els.status.textContent = t("reinstallingCount", { count: candidates.length });
  els.batchReinstall.disabled = true;
  els.reinstall.disabled = true;
  send("userscript", JSON.stringify({ reinstall: candidates }));
}

function exportScripts() {
  if (state.ids.length === 0) return;
  els.status.textContent = t("exportingScripts");
  send("userscript", JSON.stringify({ export: true }));
}

function saveExportFile(detail = {}) {
  els.status.textContent = t("exportedScripts", {
    count: detail.count || 0,
    path: detail.path || "",
  });
}

function sourcesFromImportText(text) {
  const trimmed = text.trim();
  if (!trimmed) return [];
  try {
    const data = JSON.parse(trimmed);
    const scripts = Array.isArray(data) ? data : data.scripts;
    if (Array.isArray(scripts)) {
      return scripts
        .map((item) => (typeof item === "string" ? item : item?.source))
        .filter((source) => typeof source === "string" && source.trim());
    }
  } catch {}
  return trimmed.includes("// ==UserScript==") ? [text] : [];
}

async function importSelectedFiles() {
  const files = Array.from(els.importFile.files || []);
  els.importFile.value = "";
  if (files.length === 0) return;
  const sources = [];
  for (const file of files) {
    sources.push(...sourcesFromImportText(await file.text()));
  }
  if (sources.length === 0) {
    els.status.textContent = t("importNoScripts");
    return;
  }
  els.status.textContent = t("importingScripts", { count: sources.length });
  send("userscript", JSON.stringify({ import: sources }));
}

function toggleScript(id, enabled) {
  const meta = state.metas.get(id) || "";
  const nextMeta = setMetaEnabled(meta, enabled);
  state.metas.set(id, nextMeta);
  state.skipNextMetaRefresh = true;
  if (id === state.selectedId) {
    els.enabledToggle.checked = enabled;
    renderDetails(nextMeta);
  }
  saveMeta(id, nextMeta);
}

function markMetaDirty() {
  state.dirty = true;
  els.save.disabled = false;
}

function setOverview(id, updateHistory = true) {
  const meta = state.metas.get(id) || "";
  state.selectedId = id;
  state.source = "";
  state.dirty = false;
  setSourceVisible(false);
  els.source.value = "";
  els.scriptId.textContent = id;
  els.scriptName.textContent = scriptNameFromMeta(meta);
  els.enabledToggle.checked = isScriptEnabled(meta);
  els.enabledToggle.disabled = id.length === 0;
  els.save.disabled = true;
  els.delete.disabled = id.length === 0;
  els.reinstall.disabled = !installUrlFromMeta(meta);
  els.viewSource.disabled = id.length === 0;
  renderDetails(meta);
  document.body.classList.add("editing");
  if (updateHistory) setViewHash("script", id);
  renderList();
}

function setEditor({ id = "", source = "" }, updateHistory = true) {
  source = decodeUnicodeEscapes(source);
  state.selectedId = id;
  state.source = source;
  state.dirty = false;
  const meta = source.match(/^[\s\S]*?\/\/ ==\/UserScript==\s+/)?.[0] || "";
  if (meta) state.metas.set(id, meta);
  els.source.value = source;
  els.scriptId.textContent = id || t("newScript");
  els.scriptName.textContent = scriptNameFromMeta(meta);
  els.enabledToggle.checked = isScriptEnabled(meta);
  els.enabledToggle.disabled = id.length === 0;
  els.save.disabled = true;
  els.delete.disabled = id.length === 0;
  els.reinstall.disabled = !installUrlFromMeta(meta);
  els.viewSource.disabled = false;
  renderDetails(meta);
  setSourceVisible(true);
  document.body.classList.add("editing");
  if (id && updateHistory) setViewHash("source", id);
  renderList();
}

function openOverview(id) {
  setOverview(id);
}

function openSource() {
  if (!state.selectedId && !state.source) return;
  if (state.sourceVisible) {
    setSourceVisible(false);
    setViewHash("script", state.selectedId);
  } else if (state.source) {
    setSourceVisible(true);
    setViewHash("source", state.selectedId);
  } else {
    els.source.value = t("loadingSource");
    setSourceVisible(true);
    setViewHash("source", state.selectedId);
    send("userscript", JSON.stringify({ read: state.selectedId }));
  }
}

function newScript() {
  setEditor({
    source: `// ==UserScript==\n// @name New Script\n// @namespace ChromeXt\n// @match https://example.com/*\n// @grant none\n// ==/UserScript==\n\n(function () {\n  "use strict";\n\n})();\n`,
  });
  els.save.disabled = false;
  els.source.focus();
}

function saveScript() {
  els.save.disabled = true;
  if (!state.sourceVisible && state.selectedId) {
    const meta = state.metas.get(state.selectedId) || "";
    const nextMeta = replaceMetaRules(meta, {
      match: normalizeRuleEditorValue(els.matchEditor.value),
      include: normalizeRuleEditorValue(els.includeEditor.value),
      exclude: normalizeRuleEditorValue(els.excludeEditor.value),
    });
    saveMeta(state.selectedId, nextMeta);
    return;
  }
  send(
    "userscript",
    JSON.stringify({
      previousId: state.selectedId,
      source: els.source.value,
    })
  );
}

function deleteScript() {
  if (!state.selectedId || !confirm(t("deleteConfirm"))) return;
  send("userscript", JSON.stringify({ ids: [state.selectedId], delete: true }));
  state.ids = state.ids.filter((id) => id !== state.selectedId);
  state.metas.delete(state.selectedId);
  state.selectedId = "";
  state.source = "";
  history.replaceState(null, "", location.pathname);
  document.body.classList.remove("editing");
  renderList();
}

function handleUserscriptInit(event) {
  const detail = event.detail || {};
  if (!Array.isArray(detail.ids)) return;
  state.ids = detail.ids;
  if (state.ids.length > 0) {
    send("userscript", JSON.stringify({ ids: state.ids }));
  } else {
    renderList();
  }
}

ChromeXt?.addEventListener("userscript", handleUserscriptInit);
ChromeXt?.addEventListener("script_meta", (event) => {
  (event.detail || []).forEach((meta, index) => {
    if (state.ids[index]) state.metas.set(state.ids[index], meta);
  });
  const wantsSource = location.hash.startsWith("#source=");
  const hashedId = selectedIdFromHash();
  if (hashedId && state.ids.includes(hashedId) && !state.selectedId) {
    setOverview(hashedId, false);
    if (wantsSource) openSource();
    return;
  }
  if (state.selectedId && state.metas.has(state.selectedId) && !state.sourceVisible) {
    const meta = state.metas.get(state.selectedId);
    renderDetails(meta);
    els.reinstall.disabled = !installUrlFromMeta(meta);
  }
  renderList();
});
ChromeXt?.addEventListener("script_detail", (event) => setEditor(event.detail || {}, false));
ChromeXt?.addEventListener("script_saved", (event) => {
  const id = event.detail?.id;
  if (id && !state.ids.includes(id)) state.ids.push(id);
  state.selectedId = id || state.selectedId;
  state.dirty = false;
  els.save.disabled = false;
  send("userscript", "");
  if (id) send("userscript", JSON.stringify({ read: id }));
});
ChromeXt?.addEventListener("script_export", (event) => saveExportFile(event.detail || {}));
ChromeXt?.addEventListener("script_imported", (event) => {
  const detail = event.detail || {};
  els.status.textContent = t("importedScripts", {
    count: detail.imported || 0,
    failed: detail.failed || 0,
  });
  send("userscript", "");
});
ChromeXt?.addEventListener("script_reinstalled", (event) => {
  const detail = event.detail || {};
  els.status.textContent = t("reinstalledScripts", {
    count: detail.updated || 0,
    failed: detail.failed || 0,
  });
  els.batchReinstall.disabled = false;
  els.reinstall.disabled = !installUrlFromMeta(state.metas.get(state.selectedId) || "");
  send("userscript", "");
});
ChromeXt?.addEventListener("script_meta_saved", (event) => {
  const id = event.detail?.id || state.selectedId;
  if (id) state.selectedId = id;
  state.dirty = false;
  els.save.disabled = true;
  if (state.skipNextMetaRefresh) {
    state.skipNextMetaRefresh = false;
    return;
  }
  send("userscript", "");
});
ChromeXt?.addEventListener("script_error", (event) => {
  els.status.textContent = event.detail?.message || t("scriptActionFailed");
  els.save.disabled = false;
});

els.back.addEventListener("click", () => {
  showListView();
});
els.batchReinstall.addEventListener("click", () => requestReinstall(state.ids));
els.delete.addEventListener("click", deleteScript);
els.exportScripts.addEventListener("click", exportScripts);
els.importFile.addEventListener("change", importSelectedFiles);
els.importScripts.addEventListener("click", () => els.importFile.click());
els.newScript.addEventListener("click", newScript);
els.reinstall.addEventListener("click", () => requestReinstall([state.selectedId]));
els.save.addEventListener("click", saveScript);
els.search.addEventListener("input", renderList);
els.viewSource.addEventListener("click", openSource);
els.enabledToggle.addEventListener("change", () => {
  if (!state.selectedId) return;
  toggleScript(state.selectedId, els.enabledToggle.checked);
});
[els.matchEditor, els.includeEditor, els.excludeEditor].forEach((editor) => {
  editor.addEventListener("input", markMetaDirty);
});
els.source.addEventListener("input", () => {
  state.dirty = true;
  state.source = els.source.value;
  els.save.disabled = els.source.value.trim().length === 0;
});
els.source.addEventListener("keydown", (event) => {
  if (event.key !== "Tab") return;
  event.preventDefault();
  const start = els.source.selectionStart;
  const end = els.source.selectionEnd;
  els.source.setRangeText("  ", start, end, "end");
});

window.addEventListener("popstate", () => {
  const id = selectedIdFromHash();
  if (!id || !state.ids.includes(id)) {
    state.selectedId = "";
    state.source = "";
    showListView(false);
    renderList();
    return;
  }
  setOverview(id, false);
  if (location.hash.startsWith("#source=")) openSource();
});

if (ChromeXt) send("userscript", "");
