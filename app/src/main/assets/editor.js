(function () {
const isSandboxed = [
  "raw.githubusercontent.com",
  "gist.githubusercontent.com",
].includes(location.hostname);

function readScriptText() {
  const meta = document.querySelector("#meta");
  const code = document.querySelector("#code");
  return meta && code ? meta.textContent + code.textContent : document.body.innerText;
}

function currentInstallLabel() {
  return globalThis.__chromextCurrentInstall?.installed ? "重新安装" : "安装";
}

async function installScript(force = false) {
  const dialog = document.querySelector("dialog#confirm");
  if (!force) {
    if (dialog && !dialog.open) dialog.showModal();
    return;
  }
  const confirm = dialog?.querySelector("button.primary");
  if (confirm) {
    confirm.disabled = true;
    confirm.textContent = globalThis.__chromextCurrentInstall?.installed ? "重新安装中" : "安装中";
  }
  setInstallStatus(globalThis.__chromextCurrentInstall?.installed ? "正在重新安装脚本..." : "正在安装脚本...");
  Symbol.ChromeXt.dispatch("installScript", readScriptText());
}

function metaValues(meta, key) {
  return Array.from(meta.matchAll(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "gm"))).map((m) =>
    m[1].trim()
  );
}

function metaValue(meta, key) {
  return meta.match(new RegExp(`^//\\s+@${key}\\s+(.+)$`, "m"))?.[1]?.trim() || "";
}

function createNode(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function renderMetaPillList(parent, title, values, emptyText) {
  const section = createNode("section", "cx-section");
  section.append(createNode("h3", null, title));
  const list = createNode("div", "cx-pill-list");
  const items = values.length > 0 ? values : [emptyText];
  items.forEach((value) => list.append(createNode("code", values.length > 0 ? "" : "muted", value)));
  section.append(list);
  parent.append(section);
}

function setInstallStatus(message, kind = "pending") {
  const status = document.querySelector("#install-status");
  if (!status) return;
  if (!message) {
    status.className = "cx-status";
    status.textContent = "";
    return;
  }
  status.className = `cx-status ${kind}`;
  status.textContent = message;
}

function showInstallResult(detail) {
  const dialog = document.querySelector("dialog#confirm");
  if (!dialog) return;
  const confirm = dialog.querySelector("button.primary");
  const later = dialog.querySelector("button.secondary");
  if (detail?.ok) {
    const message = detail.reinstall
      ? "重新安装成功，可以去脚本管理里查看和编辑。"
      : "安装成功，可以去脚本管理里查看和编辑。";
    setInstallStatus(message, "success");
    if (later) later.textContent = "留在此页";
    if (confirm) {
      confirm.disabled = false;
      confirm.textContent = "打开脚本管理";
      confirm.onclick = () => {
        location.href = "https://chromext.local/";
      };
    }
  } else {
    setInstallStatus(detail?.message || "安装失败，请检查脚本元数据。", "error");
    if (confirm) {
      confirm.disabled = false;
      confirm.textContent = globalThis.__chromextCurrentInstall?.installed ? "重试重新安装" : "重试安装";
      confirm.onclick = () => installScript(true);
    }
  }
  if (!dialog.open) dialog.showModal();
}
globalThis.__chromextInstallResult = showInstallResult;

function scriptIdFromMeta(metaText) {
  const name = (metaValue(metaText, "name") || "sample").replace(/:/g, "");
  const namespace = metaValue(metaText, "namespace") || "ChromeXt";
  return `${namespace}:${name}`;
}

function showInstallStatus(detail) {
  if (!detail?.id) return;
  globalThis.__chromextCurrentInstall = detail;
  const dialog = document.querySelector("dialog#confirm");
  if (!dialog) return;
  const title = dialog.querySelector("h2");
  const confirm = dialog.querySelector("button.primary");
  if (detail.installed) {
    if (title) title.textContent = "重新安装用户脚本";
    if (confirm && !confirm.disabled) confirm.textContent = "重新安装";
    setInstallStatus("检测到已安装此脚本，再次确认将覆盖更新。", "pending");
  } else {
    if (title) title.textContent = "安装用户脚本";
    if (confirm && !confirm.disabled) confirm.textContent = "安装";
    setInstallStatus("");
  }
}
globalThis.__chromextInstallStatus = showInstallStatus;

function splitUserScript(text) {
  const match = text.match(/^[\s\S]*?\/\/ ==\/UserScript==\r?\n/);
  if (!match) return null;
  return {
    metaText: match[0],
    codeText: text.slice(match[0].length),
  };
}

function renderEditor(code, alertEncoding) {
  let scriptMeta = document.querySelector("#meta");
  if (scriptMeta) return;

  const split = splitUserScript(code.textContent);
  if (!split) return;

  scriptMeta = document.createElement("pre");
  scriptMeta.id = "meta";
  scriptMeta.textContent = split.metaText;
  code.textContent = split.codeText;
  code.id = "code";
  code.removeAttribute("style");
  document.body.prepend(scriptMeta);

  if (alertEncoding) {
    createDialog({
      title: "编码可能异常",
      message:
        "当前脚本可能包含编码异常的文本。建议下载脚本后本地打开确认，再决定是否安装。",
      actions: [{ text: "知道了", action: (dialog) => dialog.close() }],
    });
  } else {
    createInstallDialog(split.metaText);
    setTimeout(() => installScript());
  }

  scriptMeta.setAttribute("contenteditable", true);
  code.setAttribute("contenteditable", true);
  scriptMeta.setAttribute("spellcheck", false);
  code.setAttribute("spellcheck", false);
}

function createDialog({ title, message, actions }) {
  const dialog = document.createElement("dialog");
  dialog.id = "confirm";
  const shell = createNode("div", "cx-dialog");
  shell.append(createNode("p", "cx-eyebrow", "ChromeXt"));
  shell.append(createNode("h2", null, title));
  shell.append(createNode("p", "cx-message", message));

  const actionBar = createNode("div", "cx-actions");
  actions.forEach(({ text, kind = "secondary", action }) => {
    const button = createNode("button", kind, text);
    button.type = "button";
    button.addEventListener("click", () => action(dialog));
    actionBar.append(button);
  });
  shell.append(actionBar);
  dialog.append(shell);
  document.body.prepend(dialog);
  dialog.showModal();
  return dialog;
}

function createInstallDialog(metaText) {
  const dialog = document.createElement("dialog");
  dialog.id = "confirm";
  dialog.append(createInstallContent(metaText));
  document.body.prepend(dialog);
  const id = scriptIdFromMeta(metaText);
  globalThis.__chromextCurrentInstall = { id, installed: false };
  Symbol.ChromeXt.dispatch("checkScript", { id });
}

function createInstallContent(metaText) {
  const shell = createNode("div", "cx-dialog");
  const name = metaValue(metaText, "name") || "未命名脚本";
  const namespace = metaValue(metaText, "namespace") || "UserScript";
  const description = metaValue(metaText, "description");
  const version = metaValue(metaText, "version");
  const matches = metaValues(metaText, "match");
  const includes = metaValues(metaText, "include");
  const excludes = metaValues(metaText, "exclude");
  const grants = metaValues(metaText, "grant");
  const asksChromeXt = metaText.includes("GM.ChromeXt");

  shell.append(createNode("p", "cx-eyebrow", "ChromeXt"));
  shell.append(createNode("h2", null, "安装用户脚本"));
  shell.append(createNode("p", "cx-script-name", name));
  shell.append(createNode("p", "cx-namespace", namespace));
  if (description) shell.append(createNode("p", "cx-message", description));

  const stats = createNode("div", "cx-stats");
  stats.append(createNode("span", null, `${matches.length + includes.length} 个匹配规则`));
  stats.append(createNode("span", null, `${excludes.length} 个排除规则`));
  stats.append(createNode("span", null, `${grants.length} 个权限`));
  if (version) stats.append(createNode("span", null, `v${version}`));
  shell.append(stats);

  renderMetaPillList(shell, "Match", matches, "没有 match 规则");
  renderMetaPillList(shell, "Include", includes, "没有 include 规则");
  renderMetaPillList(shell, "Exclude", excludes, "没有排除规则");

  if (asksChromeXt) {
    const warning = createNode("p", "cx-warning", "此脚本声明了 GM.ChromeXt，请确认来源可信。");
    shell.append(warning);
  }
  const status = createNode("p", "cx-status", "");
  status.id = "install-status";
  shell.append(status);

  const actionBar = createNode("div", "cx-actions");
  const later = createNode("button", "secondary", "稍后提醒");
  later.type = "button";
  later.addEventListener("click", () => {
    const dialog = document.querySelector("dialog#confirm");
    dialog?.close();
    setTimeout(() => {
      if (dialog && !dialog.open) dialog.showModal();
    }, 30000);
  });
  const confirm = createNode("button", "primary", currentInstallLabel());
  confirm.type = "button";
  confirm.onclick = () => installScript(true);
  actionBar.append(later);
  actionBar.append(confirm);
  shell.append(actionBar);
  return shell;
}

async function prepareDOM() {
  if (Symbol.ChromeXt == undefined) return;
  if (document.querySelector("script,div,p") != null) return;
  const meta = document.createElement("meta");
  const style = document.createElement("style");

  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = _editor_style;

  const code = document.querySelector("body > pre");
  if (!code) return;
  if (document.readyState == "loading") {
    if (isSandboxed) {
      return prepareDOM();
    } else {
      return document.addEventListener("DOMContentLoaded", prepareDOM);
    }
  }
  Symbol.installScript = installScript;
  if (!globalThis.__chromextInstallResultHooked) {
    globalThis.__chromextInstallResultHooked = true;
    Symbol.ChromeXt.addEventListener("install_result", (event) => showInstallResult(event.detail));
    Symbol.ChromeXt.addEventListener("install_status", (event) => showInstallStatus(event.detail));
  }
  document.head.appendChild(meta);
  document.head.appendChild(style);

  const alertEncoding = !(await fixEncoding(true, true, code));
  renderEditor(code, alertEncoding);
}

prepareDOM();
})();
