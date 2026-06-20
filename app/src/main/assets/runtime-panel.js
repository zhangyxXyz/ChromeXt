(() => {
  const rootId = "__chromext_runtime_panel__";
  const existing = document.getElementById(rootId);
  if (existing) {
    existing.remove();
    return;
  }

  let ChromeXt;
  try {
    ChromeXt = Symbol.ChromeXtRuntimeName.unlock(ChromeXtRuntimeKey, false);
  } catch (error) {
    console.error("Failed to open ChromeXt runtime panel", error);
    return;
  }

  const host = document.createElement("div");
  host.id = rootId;
  host.style.all = "initial";
  document.documentElement.appendChild(host);

  const shadow = host.attachShadow({ mode: "closed" });
  const state = {
    view: "scripts",
    scriptId: null,
    signature: "",
    openedAt: Date.now(),
    message: "",
    confirm: null,
  };

  const style = document.createElement("style");
  style.textContent = `
    :host {
      all: initial;
      color-scheme: light dark;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    .backdrop {
      position: fixed;
      inset: 0;
      z-index: 2147483647;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      box-sizing: border-box;
      background: rgba(12, 18, 28, .42);
    }
    .panel {
      width: 100%;
      max-width: 560px;
      max-height: min(84vh, 720px);
      overflow: hidden;
      border-radius: 18px 18px 0 0;
      background: #fff;
      color: #16181d;
      box-shadow: 0 -18px 56px rgba(20, 24, 32, .24);
    }
    .header {
      display: flex;
      align-items: center;
      gap: 12px;
      min-height: 58px;
      box-sizing: border-box;
      padding: 10px 16px 8px;
      border-bottom: 1px solid #edf0f4;
    }
    .icon-btn {
      flex: 0 0 auto;
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border: 0;
      border-radius: 18px;
      background: #eef2f7;
      color: #1f2937;
      font: 700 18px/1 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      padding: 0;
    }
    .icon-btn::before {
      content: attr(data-icon);
      display: block;
      transform: translateY(-1px);
    }
    .title-wrap {
      min-width: 0;
      flex: 1;
    }
    .eyebrow {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #637083;
      font-size: 11px;
      line-height: 15px;
      font-weight: 750;
      text-transform: uppercase;
    }
    .title {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-top: 1px;
      color: #111827;
      font-size: 18px;
      line-height: 24px;
      font-weight: 760;
    }
    .title.script-title {
      color: #0f766e;
    }
    .content {
      overflow: auto;
      max-height: calc(min(84vh, 720px) - 58px);
      padding: 4px 0 14px;
      -webkit-overflow-scrolling: touch;
      scrollbar-width: thin;
      scrollbar-color: transparent transparent;
      transition: scrollbar-color .18s ease;
    }
    .content.scrolling {
      scrollbar-color: rgba(102, 112, 133, .5) transparent;
    }
    .content::-webkit-scrollbar {
      width: 4px;
      height: 4px;
    }
    .content::-webkit-scrollbar-track {
      background: transparent;
    }
    .content::-webkit-scrollbar-thumb {
      border-radius: 999px;
      background: transparent;
    }
    .content.scrolling::-webkit-scrollbar-thumb {
      background: rgba(102, 112, 133, .5);
    }
    .row {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;
      min-height: 64px;
      box-sizing: border-box;
      border: 0;
      border-bottom: 1px solid #edf0f4;
      background: transparent;
      color: #16181d;
      padding: 10px 16px;
      text-align: left;
      font: inherit;
    }
    .row:active, .icon-btn:active {
      background: #f2f5f9;
    }
    .avatar {
      position: relative;
      flex: 0 0 auto;
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: #eef2f7;
      color: #344054;
      font-size: 13px;
      font-weight: 760;
      overflow: hidden;
    }
    .avatar.icon-text {
      font-size: 20px;
      line-height: 1;
      font-weight: 650;
    }
    .avatar.icon-ban::before {
      content: "";
      width: 17px;
      height: 17px;
      border: 2px solid currentColor;
      border-radius: 999px;
      background: linear-gradient(45deg, transparent 43%, currentColor 44%, currentColor 56%, transparent 57%);
      box-sizing: border-box;
    }
    .avatar.icon-ban {
      background: #fee2e2;
      color: #dc2626;
    }
    .avatar.icon-command::before {
      content: "";
      position: absolute;
      left: 50%;
      top: 10px;
      width: 18px;
      height: 2px;
      border-radius: 999px;
      background: #475467;
      box-shadow: 0 7px 0 #475467, 0 14px 0 #475467;
      transform: translateX(-50%);
      opacity: .95;
    }
    .avatar.icon-command::after {
      content: "";
      position: absolute;
      left: 10px;
      top: 9px;
      width: 4px;
      height: 4px;
      border-radius: 999px;
      background: #475467;
      box-shadow: 9px 7px 0 #475467, 3px 14px 0 #475467;
    }
    .avatar.icon-manager::before {
      content: "";
      width: 18px;
      height: 16px;
      border: 2px solid currentColor;
      border-radius: 5px;
      box-sizing: border-box;
      background: linear-gradient(currentColor, currentColor) center 5px/10px 2px no-repeat;
    }
    .meta {
      min-width: 0;
      flex: 1;
    }
    .primary {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #111827;
      font-size: 16px;
      line-height: 22px;
      font-weight: 680;
    }
    .secondary {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-top: 3px;
      color: #667085;
      font-size: 13px;
      line-height: 18px;
      font-weight: 500;
    }
    .more {
      flex: 0 0 auto;
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 17px;
      border: 1px solid #d7dee8;
      color: #344054;
      font-size: 18px;
      line-height: 1;
    }
    .utility {
      margin-top: 8px;
      border-top: 8px solid #f2f4f7;
    }
    .empty {
      padding: 18px 16px;
      color: #667085;
      font-size: 14px;
      line-height: 20px;
    }
    .status {
      margin: 10px 16px;
      border-radius: 8px;
      background: #eff6ff;
      color: #1d4ed8;
      padding: 10px 12px;
      font-size: 13px;
      line-height: 18px;
      font-weight: 680;
    }
    .confirm-backdrop {
      position: fixed;
      inset: 0;
      z-index: 2147483647;
      display: none;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
      padding: 18px;
      background: rgba(12, 18, 28, .5);
    }
    .confirm-backdrop.show {
      display: flex;
    }
    .confirm-box {
      width: min(360px, 100%);
      overflow: hidden;
      border-radius: 16px;
      background: #fff;
      color: #111827;
      box-shadow: 0 18px 56px rgba(20, 24, 32, .28);
    }
    .confirm-body {
      padding: 18px 18px 14px;
    }
    .confirm-title {
      display: block;
      color: #111827;
      font-size: 17px;
      line-height: 23px;
      font-weight: 760;
    }
    .confirm-detail {
      display: block;
      margin-top: 7px;
      color: #667085;
      font-size: 13px;
      line-height: 19px;
      word-break: break-word;
    }
    .confirm-actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      border-top: 1px solid #edf0f4;
    }
    .confirm-action {
      min-height: 48px;
      border: 0;
      background: transparent;
      color: #344054;
      font: 700 15px/1 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    .confirm-action + .confirm-action {
      border-left: 1px solid #edf0f4;
    }
    .confirm-action.danger {
      color: #dc2626;
    }
    .confirm-action:active {
      background: #f2f5f9;
    }
    @media (min-width: 720px) {
      .backdrop {
        align-items: center;
      }
      .panel {
        width: min(560px, calc(100vw - 32px));
        border-radius: 18px;
      }
    }
    @media (prefers-color-scheme: dark) {
      .backdrop { background: rgba(0, 0, 0, .58); }
      .panel {
        background: #181c24;
        color: #edf0f6;
        box-shadow: 0 -18px 56px rgba(0, 0, 0, .46);
      }
      .header, .row { border-color: #2a3039; }
      .icon-btn, .avatar {
        background: #2b3340;
        color: #f1f5f9;
      }
      .avatar.icon-command, .avatar.icon-manager {
        background: #253040;
        color: #d8dee8;
      }
      .avatar.icon-command::before {
        background: #e5e7eb;
        box-shadow: 0 7px 0 #e5e7eb, 0 14px 0 #e5e7eb;
      }
      .avatar.icon-command::after {
        background: #e5e7eb;
        box-shadow: 9px 7px 0 #e5e7eb, 3px 14px 0 #e5e7eb;
      }
      .avatar.icon-ban {
        background: rgba(248, 113, 113, .16);
        color: #f87171;
      }
      .eyebrow, .secondary, .empty { color: #9ca8ba; }
      .title, .primary { color: #f8fafc; }
      .title.script-title { color: #5eead4; }
      .status { background: #172554; color: #93c5fd; }
      .row:active, .icon-btn:active { background: #202632; }
      .more { border-color: #3a4453; color: #e5e7eb; }
      .utility { border-color: #202632; }
      .confirm-backdrop { background: rgba(0, 0, 0, .62); }
      .confirm-box {
        background: #181c24;
        color: #f8fafc;
        box-shadow: 0 18px 56px rgba(0, 0, 0, .48);
      }
      .confirm-title { color: #f8fafc; }
      .confirm-detail { color: #9ca8ba; }
      .confirm-actions, .confirm-action + .confirm-action { border-color: #2a3039; }
      .confirm-action { color: #e5e7eb; }
      .confirm-action.danger { color: #f87171; }
      .confirm-action:active { background: #202632; }
      .content.scrolling {
        scrollbar-color: rgba(156, 168, 186, .55) transparent;
      }
      .content.scrolling::-webkit-scrollbar-thumb {
        background: rgba(156, 168, 186, .55);
      }
    }
  `;

  const backdrop = document.createElement("div");
  backdrop.className = "backdrop";
  const panel = document.createElement("div");
  panel.className = "panel";
  const confirmLayer = document.createElement("div");
  confirmLayer.className = "confirm-backdrop";
  const header = document.createElement("div");
  header.className = "header";
  const content = document.createElement("div");
  content.className = "content";
  panel.append(header, content);
  backdrop.appendChild(panel);
  shadow.append(style, backdrop, confirmLayer);
  let scrollHideTimer = 0;

  function enabledCommands() {
    return Array.from(ChromeXt.commands || [])
      .map((command, index) => ({ ...command, index }))
      .filter((command) => command && command.enabled && command.title);
  }

  function runningScripts() {
    return Array.from(ChromeXt.scripts || [])
      .map((info) => {
        const script = info.script || {};
        return {
          id: script.id || "",
          name: script.name || script.id || "UserScript",
          namespace: script.namespace || "",
          version: script.version || "",
        };
      })
      .filter((script) => script.id.length > 0);
  }

  function commandTitle(command) {
    try {
      if (typeof command.title == "function") return String(command.title(command.index));
      return String(command.title);
    } catch {
      return "Command";
    }
  }

  function isIconCodePoint(codePoint) {
    return (
      (codePoint >= 0x1f000 && codePoint <= 0x1faff) ||
      (codePoint >= 0x2600 && codePoint <= 0x27bf) ||
      (codePoint >= 0x2300 && codePoint <= 0x23ff) ||
      (codePoint >= 0x2190 && codePoint <= 0x21ff) ||
      (codePoint >= 0x2b00 && codePoint <= 0x2bff)
    );
  }

  function splitLeadingIcon(title) {
    const text = String(title || "").trimStart();
    if (!text) return { icon: "icon:command", title: "Command" };
    const chars = Array.from(text);
    const firstCodePoint = chars[0].codePointAt(0);
    if (!isIconCodePoint(firstCodePoint)) {
      return { icon: "icon:command", title: text.trim() || "Command" };
    }

    let consumed = chars[0].length;
    let icon = chars[0];
    let index = 1;
    while (index < chars.length) {
      const codePoint = chars[index].codePointAt(0);
      if (codePoint === 0xfe0e || codePoint === 0xfe0f) {
        icon += chars[index];
        consumed += chars[index].length;
        index += 1;
      } else if (codePoint === 0x200d && index + 1 < chars.length) {
        icon += chars[index] + chars[index + 1];
        consumed += chars[index].length + chars[index + 1].length;
        index += 2;
      } else {
        break;
      }
    }

    const rest = text.slice(consumed).trim();
    if (!rest) return { icon: "icon:command", title: text.trim() || "Command" };
    return { icon, title: rest };
  }

  function scriptCommands(scriptId) {
    return enabledCommands().filter((command) => command.id === scriptId);
  }

  function currentHost() {
    try {
      return location.hostname || new URL(location.href).hostname;
    } catch {
      return "";
    }
  }

  function wildcardHost(host) {
    if (!host || /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(":")) return "";
    const parts = host.split(".").filter(Boolean);
    if (parts.length <= 2) return "";
    return `*.${parts.slice(-2).join(".")}`;
  }

  function dataSignature() {
    const scripts = runningScripts()
      .map((script) => `${script.id}:${script.name}`)
      .join("|");
    const commands = enabledCommands()
      .map((command) => `${command.id}:${command.index}:${commandTitle(command)}`)
      .join("|");
    return `${state.view}:${state.scriptId || ""}:${state.message}:${scripts}::${commands}`;
  }

  function clear(node) {
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function makeButton(className, onClick) {
    const el = document.createElement("button");
    el.className = className;
    el.type = "button";
    el.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      onClick();
    });
    return el;
  }

  function setText(className, text) {
    const span = document.createElement("span");
    span.className = className;
    span.textContent = text;
    return span;
  }

  function addHeader(titleText, back, accented = false) {
    clear(header);
    const close = makeButton("icon-btn", () => {
      if (back) {
        state.view = "scripts";
        state.scriptId = null;
        render(true);
      } else {
        host.remove();
      }
    });
    close.dataset.icon = back ? "<" : "x";
    const titleWrap = document.createElement("div");
    titleWrap.className = "title-wrap";
    titleWrap.append(setText("eyebrow", "ChromeXt"), setText(`title${accented ? " script-title" : ""}`, titleText));
    header.append(close, titleWrap);
  }

  function addRow(icon, primary, secondary, onClick, withMore = false, utility = false) {
    const row = makeButton(`row${utility ? " utility" : ""}`, onClick);
    const avatar = setText("avatar", "");
    if (icon.startsWith("icon:")) {
      avatar.classList.add(`icon-${icon.slice(5)}`);
    } else {
      avatar.classList.add("icon-text");
      avatar.textContent = icon;
    }
    row.appendChild(avatar);
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.appendChild(setText("primary", primary));
    if (secondary) meta.appendChild(setText("secondary", secondary));
    row.appendChild(meta);
    if (withMore) row.appendChild(setText("more", "..."));
    content.appendChild(row);
  }

  function addStatus() {
    if (!state.message) return;
    const status = document.createElement("div");
    status.className = "status";
    status.textContent = state.message;
    content.appendChild(status);
  }

  function clearConfirm() {
    state.confirm = null;
    confirmLayer.className = "confirm-backdrop";
    clear(confirmLayer);
  }

  function showConfirm(title, detail, action) {
    state.confirm = { title, detail, action };
    renderConfirm();
  }

  function renderConfirm() {
    clear(confirmLayer);
    if (!state.confirm) {
      confirmLayer.className = "confirm-backdrop";
      return;
    }
    confirmLayer.className = "confirm-backdrop show";
    const box = document.createElement("div");
    box.className = "confirm-box";
    const body = document.createElement("div");
    body.className = "confirm-body";
    body.append(setText("confirm-title", state.confirm.title));
    body.append(setText("confirm-detail", state.confirm.detail));
    const actions = document.createElement("div");
    actions.className = "confirm-actions";
    const cancel = makeButton("confirm-action", () => clearConfirm());
    cancel.textContent = "Cancel";
    const apply = makeButton("confirm-action danger", () => {
      const action = state.confirm?.action;
      clearConfirm();
      if (action) action();
    });
    apply.textContent = "Exclude";
    actions.append(cancel, apply);
    box.append(body, actions);
    confirmLayer.appendChild(box);
  }

  function renderScripts() {
    addHeader("Script panel", false, true);
    clear(content);
    addStatus();
    const scripts = runningScripts();
    if (scripts.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No scripts are running on this page yet.";
      content.appendChild(empty);
    } else {
      scripts.forEach((script) => {
        const commands = scriptCommands(script.id);
        const count = commands.length;
        const detail =
          count > 0
            ? `${count} menu command${count === 1 ? "" : "s"}`
            : "No user menu commands";
        addRow(
          script.name.trim().slice(0, 1).toUpperCase() || "#",
          script.name,
          [detail, script.namespace, script.version].filter(Boolean).join(" · "),
          () => {
            state.view = "commands";
            state.scriptId = script.id;
            render(true);
          },
          true
        );
      });
    }
    addRow("icon:manager", "Script manager", "Open the installed scripts page", () => {
      host.remove();
      location.href = "https://chromext.local/?from=runtime";
    }, false, true);
  }

  function renderCommands() {
    const script = runningScripts().find((item) => item.id === state.scriptId) || {
      id: state.scriptId,
      name: "UserScript",
    };
    addHeader(script.name, true, true);
    clear(content);
    addStatus();
    const commands = scriptCommands(script.id);
    const pageHost = currentHost();
    if (pageHost) {
      addRow("icon:ban", `Exclude ${pageHost}`, `@exclude *://${pageHost}/*`, () => {
        const rule = `*://${pageHost}/*`;
        showConfirm(`Exclude ${pageHost}?`, `Add ${rule} to ${script.name}. Refresh the page for it to take effect.`, () => {
          state.message = `Excluding ${pageHost}...`;
          render(true);
          ChromeXt.dispatch("excludeScript", {
            id: script.id,
            rule,
            label: pageHost,
          });
        });
      });
      const wildcard = wildcardHost(pageHost);
      if (wildcard) {
        addRow("icon:ban", `Exclude ${wildcard}`, `@exclude *://${wildcard}/*`, () => {
          const rule = `*://${wildcard}/*`;
          showConfirm(`Exclude ${wildcard}?`, `Add ${rule} to ${script.name}. Refresh the page for it to take effect.`, () => {
            state.message = `Excluding ${wildcard}...`;
            render(true);
            ChromeXt.dispatch("excludeScript", {
              id: script.id,
              rule,
              label: wildcard,
            });
          });
        });
      }
    }
    if (commands.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No user menu commands";
      content.appendChild(empty);
    } else {
      commands.forEach((command) => {
        const display = splitLeadingIcon(commandTitle(command));
        addRow(display.icon, display.title, "", () => {
          host.remove();
          try {
            const result = command.listener();
            if (result === false) {
              document.documentElement.appendChild(host);
              render(true);
            }
          } catch (error) {
            console.error("UserScript command failed", error);
          }
        });
      });
    }
  }

  function render(force = false) {
    const nextSignature = dataSignature();
    if (!force && state.signature === nextSignature) return;
    state.signature = nextSignature;
    if (state.view === "commands") renderCommands();
    else renderScripts();
  }

  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop) host.remove();
  });
  ChromeXt.addEventListener("runtimeActionResult", (event) => {
    state.message = event.detail?.message || "";
    render(true);
  });
  content.addEventListener(
    "scroll",
    () => {
      content.classList.add("scrolling");
      clearTimeout(scrollHideTimer);
      scrollHideTimer = setTimeout(() => content.classList.remove("scrolling"), 700);
    },
    { passive: true }
  );
  ChromeXt.addEventListener("commandsUpdated", () => render(true));
  render(true);

  const refreshTimer = setInterval(() => {
    if (!document.documentElement.contains(host)) {
      clearInterval(refreshTimer);
      return;
    }
    render(false);
  }, 1000);
  const warmupTimer = setInterval(() => {
    if (!document.documentElement.contains(host) || Date.now() - state.openedAt > 5000) {
      clearInterval(warmupTimer);
      return;
    }
    render(false);
  }, 300);
})();
