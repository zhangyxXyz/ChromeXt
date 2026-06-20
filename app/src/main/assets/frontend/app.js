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
  delete: $("#delete"),
  details: $("#details"),
  enabledToggle: $("#enabled-toggle"),
  excludeEditor: $("#exclude-editor"),
  grantCount: $("#grant-count"),
  grantList: $("#grant-list"),
  includeEditor: $("#include-editor"),
  includeList: $("#include-list"),
  list: $("#script-list"),
  matchCount: $("#match-count"),
  matchEditor: $("#match-editor"),
  matchList: $("#match-list"),
  newScript: $("#new-script"),
  save: $("#save"),
  search: $("#search"),
  source: $("#source"),
  status: $("#status"),
  excludeList: $("#exclude-list"),
  scriptId: $("#script-id"),
  scriptName: $("#script-name"),
  viewSource: $("#view-source"),
};

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
        els.status.textContent = "正在等待 ChromeXt 注入...";
      }
    }, 50);
  });
}

const ChromeXt = await waitForChromeXt();

function send(action, payload = "") {
  ChromeXt.dispatch(action, payload);
}

function metaValue(meta = "", key) {
  return meta.match(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "m"))?.[1]?.trim() || "";
}

function metaValues(meta = "", key) {
  return Array.from(meta.matchAll(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "gm"))).map((m) =>
    m[1].trim()
  );
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
  return metaValue(meta, "name") || "未命名脚本";
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
  els.matchCount.textContent = `${matches.length + includes.length} 匹配`;
  els.grantCount.textContent = `${grants.length} 权限`;
  renderRuleList(els.matchList, matches, "没有 @match");
  renderRuleList(els.includeList, includes, "没有 @include");
  renderRuleList(els.excludeList, excludes, "没有 @exclude");
  renderRuleList(els.grantList, grants, "没有 @grant");
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

  if (ids.length === 0) {
    els.status.textContent = needle ? "没有匹配的脚本" : "还没有安装脚本";
    return;
  }

  els.status.textContent = `${ids.length} 个脚本`;
  ids.forEach((id) => {
    const meta = state.metas.get(id) || "";
    const row = document.createElement("div");
    row.className = "script-item" + (id === state.selectedId ? " active" : "");
    const button = document.createElement("button");
    button.className = "script-open";
    button.type = "button";
    button.innerHTML = `<strong></strong><span></span>`;
    button.querySelector("strong").textContent = scriptNameFromMeta(meta);
    button.querySelector("span").textContent = id;
    button.addEventListener("click", () => openOverview(id));
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
  els.viewSource.textContent = visible ? "详情" : "查看源码";
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
  els.scriptId.textContent = id || "新脚本";
  els.scriptName.textContent = scriptNameFromMeta(meta);
  els.enabledToggle.checked = isScriptEnabled(meta);
  els.enabledToggle.disabled = id.length === 0;
  els.save.disabled = true;
  els.delete.disabled = id.length === 0;
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
    els.source.value = "正在加载源码...";
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
  if (!state.selectedId || !confirm("删除这个脚本？")) return;
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
    renderDetails(state.metas.get(state.selectedId));
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
  els.status.textContent = event.detail?.message || "脚本操作失败";
  els.save.disabled = false;
});

els.back.addEventListener("click", () => {
  showListView();
});
els.delete.addEventListener("click", deleteScript);
els.newScript.addEventListener("click", newScript);
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
