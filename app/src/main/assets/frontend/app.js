const $ = (selector) => document.querySelector(selector);

const rootStyle = document.documentElement.style;
const paletteVariables = {
  primary: "--accent",
  primaryContainer: "--primary-container",
  background: "--bg",
  surface: "--panel",
  surfaceContainer: "--panel-soft",
  onSurface: "--text",
  onSurfaceVariant: "--muted",
  outline: "--line",
};

function applyAppearance(appearance = {}) {
  globalThis.__ChromeXtAppearance = appearance;
  const palette = appearance.palette || {};
  if (appearance.seed) rootStyle.setProperty("--accent", appearance.seed);
  Object.entries(paletteVariables).forEach(([name, variable]) => {
    if (palette[name]) rootStyle.setProperty(variable, palette[name]);
  });
  if (typeof appearance.dark === "boolean") {
    document.documentElement.dataset.theme = appearance.dark ? "dark" : "light";
  } else if (appearance.themeMode === "Dark") {
    document.documentElement.dataset.theme = "dark";
  } else if (appearance.themeMode === "Light") {
    document.documentElement.dataset.theme = "light";
  } else {
    delete document.documentElement.dataset.theme;
  }
  if (appearance.liquidGlass) document.documentElement.dataset.glass = "true";
  else delete document.documentElement.dataset.glass;
}

applyAppearance(globalThis.__ChromeXtAppearance || {});

const state = {
  ids: [],
  metas: new Map(),
  selectedId: "",
  source: "",
  dirty: false,
  sourceVisible: false,
  syntaxHighlight: false,
  lineNumbers: true,
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
  installUrl: $("#install-url"),
  installUrlCancel: $("#install-url-cancel"),
  installUrlDialog: $("#install-url-dialog"),
  installUrlForm: $("#install-url-form"),
  installUrlInput: $("#install-url-input"),
  list: $("#script-list"),
  matchCount: $("#match-count"),
  matchEditor: $("#match-editor"),
  matchList: $("#match-list"),
  newScript: $("#new-script"),
  reinstall: $("#reinstall"),
  save: $("#save"),
  search: $("#search"),
  source: $("#source"),
  sourceHighlight: $("#source-highlight"),
  sourceLines: $("#source-lines"),
  sourceWorkspace: $("#source-workspace"),
  syntaxToggle: $("#syntax-toggle"),
  linesToggle: $("#lines-toggle"),
  formatSource: $("#format-source"),
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
  const requested = value === "system" ? navigator.language : value;
  const tag = requested.toLowerCase();
  if (tag.startsWith("zh-tw") || tag.startsWith("zh-hk") || tag.startsWith("zh-mo") || tag.includes("hant")) return "zh-TW";
  if (tag.startsWith("zh")) return "zh";
  return "en";
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
  document.documentElement.lang = activeLanguage === "zh-TW" ? "zh-TW" : activeLanguage === "zh" ? "zh-CN" : "en";
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
  if (event.detail?.appearance) applyAppearance(event.detail.appearance);
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
    const icon = document.createElement("span");
    icon.className = "script-icon";
    icon.textContent = "</>";
    icon.setAttribute("aria-hidden", "true");
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
    row.append(icon, button, createSwitch(isScriptEnabled(meta), (enabled) => toggleScript(id, enabled)));
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
  if (visible) requestAnimationFrame(updateSourceDecorations);
}

function showToast(message) {
  if (!message) return;
  send("toast", JSON.stringify({ message }));
}

const EDITOR_RISK_WARNING_LENGTH = 32 * 1024;
const MAX_WEB_HIGHLIGHT_LENGTH = 96 * 1024;

function escapeHtml(value) {
  return value.replace(/[&<>]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[character]);
}

function highlightJavaScript(source) {
  const pattern = /(^\s*\/\/\s*(?:==|@)[^\n]*|\/\/[^\n]*|\/\*[\s\S]*?\*\/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b(?:break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|static|super|switch|this|throw|try|typeof|var|void|while|with|yield|async|await|true|false|null|undefined)\b|\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?)\b)/gm;
  let cursor = 0;
  let output = "";
  for (const match of source.matchAll(pattern)) {
    const token = match[0];
    output += escapeHtml(source.slice(cursor, match.index));
    const trimmed = token.trimStart();
    const kind =
      /^\/\/\s*(?:==|@)/.test(trimmed) ? "meta" :
      trimmed.startsWith("//") || trimmed.startsWith("/*") ? "comment" :
      /^["'`]/.test(trimmed) ? "string" :
      /^\d|^0[xX]/.test(trimmed) ? "number" : "keyword";
    output += `<span class="token-${kind}">${escapeHtml(token)}</span>`;
    cursor = match.index + token.length;
  }
  return output + escapeHtml(source.slice(cursor)) + "\n";
}

function updateToggle(button, active) {
  button.classList.toggle("active", active);
  button.setAttribute("aria-pressed", String(active));
}

function updateSourceDecorations() {
  const source = els.source.value;
  const lineCount = Math.max(1, source.split("\n").length);
  els.sourceLines.textContent = Array.from({ length: lineCount }, (_, index) => index + 1).join("\n") + "\n";
  els.sourceWorkspace.classList.toggle("with-lines", state.lineNumbers);
  const highlightActive = state.syntaxHighlight && source.length <= MAX_WEB_HIGHLIGHT_LENGTH;
  els.sourceWorkspace.classList.toggle("highlighted", highlightActive);
  if (highlightActive) els.sourceHighlight.innerHTML = highlightJavaScript(source);
  updateToggle(els.syntaxToggle, state.syntaxHighlight);
  updateToggle(els.linesToggle, state.lineNumbers);
  syncSourceScroll();
}

function syncSourceScroll() {
  els.sourceLines.style.transform = `translateY(${-els.source.scrollTop}px)`;
  els.sourceHighlight.style.transform = `translate(${-els.source.scrollLeft}px, ${-els.source.scrollTop}px)`;
}

function confirmEditorRisk() {
  if (els.source.value.length <= EDITOR_RISK_WARNING_LENGTH) return true;
  return confirm(t("largeScriptWarning", { size: Math.max(1, Math.round(els.source.value.length / 1024)) }));
}

function toggleSyntaxHighlight() {
  if (state.syntaxHighlight) {
    state.syntaxHighlight = false;
  } else {
    if (els.source.value.length > MAX_WEB_HIGHLIGHT_LENGTH) {
      alert(t("highlightUnavailable", { size: Math.max(1, Math.round(els.source.value.length / 1024)) }));
      return;
    }
    if (!confirmEditorRisk()) return;
    state.syntaxHighlight = true;
  }
  updateSourceDecorations();
}

function toggleLineNumbers() {
  if (!state.lineNumbers && !confirmEditorRisk()) return;
  state.lineNumbers = !state.lineNumbers;
  updateSourceDecorations();
}

function formatJavaScript(source) {
  let indent = 0;
  return source.replace(/\r\n?/g, "\n").split("\n").map((rawLine) => {
    const line = rawLine.trim();
    if (!line) return "";
    const preDedent = /^[}\])]/.test(line) ? 1 : 0;
    const opening = (line.match(/[{[(]/g) || []).length;
    const closing = (line.match(/[}\])]/g) || []).length;
    const formatted = `${"  ".repeat(Math.max(0, indent - preDedent))}${line}`;
    indent = Math.max(0, indent + opening - closing);
    return formatted;
  }).join("\n");
}

function formatCurrentSource() {
  if (!confirmEditorRisk()) return;
  const formatted = formatJavaScript(els.source.value);
  if (formatted !== els.source.value) {
    els.source.value = formatted;
    state.source = formatted;
    state.dirty = true;
    els.save.disabled = formatted.trim().length === 0;
  }
  els.formatSource.classList.add("active");
  setTimeout(() => els.formatSource.classList.remove("active"), 220);
  updateSourceDecorations();
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
  send("userscript", JSON.stringify({ appTransfer: "export" }));
}

function importScripts() {
  els.status.textContent = t("importingLatestScripts");
  send("userscript", JSON.stringify({ appTransfer: "import" }));
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

function openUrlInstaller() {
  els.installUrlInput.value = "";
  els.installUrlInput.setCustomValidity("");
  els.installUrlDialog.showModal();
  requestAnimationFrame(() => els.installUrlInput.focus());
}

function submitUrlInstaller(event) {
  event.preventDefault();
  const value = els.installUrlInput.value.trim();
  let url;
  try {
    url = new URL(value);
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error('unsupported protocol');
  } catch {
    els.installUrlInput.setCustomValidity(t("invalidUserscriptUrl"));
    els.installUrlInput.reportValidity();
    return;
  }
  els.installUrlInput.setCustomValidity("");
  location.assign(url.href);
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
  state.syntaxHighlight = false;
  state.lineNumbers = true;
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
  state.syntaxHighlight = false;
  state.lineNumbers = true;
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
  showToast(t("scriptSavedToast"));
});
ChromeXt?.addEventListener("script_export", (event) => saveExportFile(event.detail || {}));
let transferPollTimer = null;
ChromeXt?.addEventListener("script_transfer_picker", () => {
  els.status.textContent = t("selectTransferDirectory");
});
ChromeXt?.addEventListener("script_transfer_started", () => {
  clearInterval(transferPollTimer);
  let attempts = 0;
  transferPollTimer = setInterval(() => {
    send("userscript", JSON.stringify({ transferStatus: true }));
    attempts += 1;
    if (attempts >= 120) clearInterval(transferPollTimer);
  }, 500);
});
ChromeXt?.addEventListener("script_transfer_complete", (event) => {
  clearInterval(transferPollTimer);
  transferPollTimer = null;
  const detail = event.detail || {};
  if (detail.status === "cancelled") {
    els.status.textContent = detail.message || t("transferCancelled");
    return;
  }
  if (detail.status === "error") {
    els.status.textContent = detail.message || t("scriptActionFailed");
    return;
  }
  if (detail.action === "import") {
    const message = t("importedScripts", {
      count: detail.imported || 0,
      failed: detail.failed || 0,
    });
    els.status.textContent = message;
    showToast(message);
    send("userscript", "");
  } else {
    const message = t("exportedToConfiguredDirectory", {
      count: detail.count || 0,
    });
    els.status.textContent = message;
    showToast(message);
  }
});
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
  const message = t("reinstalledScripts", {
    count: detail.updated || 0,
    failed: detail.failed || 0,
  });
  els.status.textContent = message;
  showToast(message);
  els.batchReinstall.disabled = false;
  els.reinstall.disabled = !installUrlFromMeta(state.metas.get(state.selectedId) || "");
  send("userscript", "");
});
ChromeXt?.addEventListener("script_meta_saved", (event) => {
  const id = event.detail?.id || state.selectedId;
  if (id) state.selectedId = id;
  state.dirty = false;
  els.save.disabled = true;
  showToast(t("scriptSavedToast"));
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
els.importScripts.addEventListener("click", importScripts);
els.installUrl.addEventListener("click", openUrlInstaller);
els.installUrlCancel.addEventListener("click", () => els.installUrlDialog.close());
els.installUrlForm.addEventListener("submit", submitUrlInstaller);
els.newScript.addEventListener("click", newScript);
els.reinstall.addEventListener("click", () => requestReinstall([state.selectedId]));
els.save.addEventListener("click", saveScript);
els.search.addEventListener("input", renderList);
els.viewSource.addEventListener("click", openSource);
els.syntaxToggle.addEventListener("click", toggleSyntaxHighlight);
els.linesToggle.addEventListener("click", toggleLineNumbers);
els.formatSource.addEventListener("click", formatCurrentSource);
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
  updateSourceDecorations();
});

window.addEventListener("focus", () => {
  send("userscript", JSON.stringify({ transferStatus: true }));
});
els.source.addEventListener("scroll", syncSourceScroll, { passive: true });
els.source.addEventListener("keydown", (event) => {
  if (event.key !== "Tab") return;
  event.preventDefault();
  const start = els.source.selectionStart;
  const end = els.source.selectionEnd;
  els.source.setRangeText("  ", start, end, "end");
  state.source = els.source.value;
  state.dirty = true;
  els.save.disabled = false;
  updateSourceDecorations();
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
