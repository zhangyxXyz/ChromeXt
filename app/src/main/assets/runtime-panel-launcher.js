(() => {
  if (window.top !== window) return;
  const id = "__chromext_runtime_launcher__";
  if (document.getElementById(id)) return;
  const size = 44;
  const peek = 28;
  let ChromeXt = null;
  try {
    ChromeXt = Symbol.ChromeXtRuntimeName.unlock(ChromeXtRuntimeKey, false);
  } catch {}

  const button = document.createElement("button");
  button.id = id;
  button.type = "button";
  button.title = "ChromeXt";
  button.style.cssText = [
    "all:initial",
    "position:fixed",
    "left:0px",
    "top:58px",
    "z-index:2147483647",
    "display:grid",
    "place-items:center",
    "width:44px",
    "height:44px",
    "border-radius:22px",
    "background:rgba(20,24,31,.72)",
    "color:#fff",
    "border:1px solid rgba(255,255,255,.18)",
    "border-left:0",
    "box-shadow:0 8px 22px rgba(0,0,0,.28)",
    "font:800 13px ui-monospace,SFMono-Regular,Consolas,'Liberation Mono',monospace",
    "letter-spacing:0",
    "touch-action:none",
    "user-select:none",
    "-webkit-user-select:none",
    "transition:left .18s ease, top .18s ease, opacity .18s ease, background .18s ease",
    "opacity:.76",
  ].join(";");
  button.innerHTML = '<span style="display:block;position:relative;width:18px;height:14px;"><span style="position:absolute;left:0;top:3px;width:7px;height:7px;border-left:2px solid currentColor;border-bottom:2px solid currentColor;transform:rotate(45deg);"></span><span style="position:absolute;right:0;top:3px;width:7px;height:7px;border-right:2px solid currentColor;border-top:2px solid currentColor;transform:rotate(45deg);"></span><span style="position:absolute;left:8px;top:0;width:2px;height:14px;background:currentColor;transform:rotate(14deg);border-radius:2px;"></span></span>';

  let startX = 0;
  let startY = 0;
  let dragOffsetX = 0;
  let dragOffsetY = 0;
  let moved = false;
  let openedAt = 0;
  let panelWatchTimer = 0;
  let side = "left";
  let topPx = 58;

  function savePosition() {
    if (ChromeXt) ChromeXt.dispatch("runtimeLauncher", { side, top: topPx });
  }

  function clampTop(y) {
    return Math.max(0, Math.min(innerHeight - size, y));
  }

  function icon() {
    return button.firstElementChild;
  }

  function applySideStyle(expanded = false) {
    const iconEl = icon();
    if (side === "right") {
      button.style.left = `${expanded ? innerWidth - size : innerWidth - peek}px`;
      button.style.borderRadius = "22px 0 0 22px";
      button.style.borderLeft = "1px solid rgba(255,255,255,.18)";
      button.style.borderRight = "0";
      if (iconEl) iconEl.style.transform = "translateX(-3px)";
    } else {
      button.style.left = `${expanded ? 0 : peek - size}px`;
      button.style.borderRadius = "0 22px 22px 0";
      button.style.borderLeft = "0";
      button.style.borderRight = "1px solid rgba(255,255,255,.18)";
      if (iconEl) iconEl.style.transform = "translateX(3px)";
    }
  }

  function dock() {
    topPx = clampTop(topPx);
    button.style.top = `${topPx}px`;
    applySideStyle(false);
    button.style.opacity = ".76";
  }

  function expand() {
    topPx = clampTop(topPx);
    button.style.top = `${topPx}px`;
    applySideStyle(true);
    button.style.opacity = ".96";
  }

  function floatAt(x, y) {
    button.style.transition = "none";
    button.style.left = `${Math.max(0, Math.min(innerWidth - size, x))}px`;
    button.style.top = `${clampTop(y)}px`;
    button.style.borderRadius = "22px";
    button.style.borderLeft = "1px solid rgba(255,255,255,.18)";
    button.style.borderRight = "1px solid rgba(255,255,255,.18)";
    const iconEl = icon();
    if (iconEl) iconEl.style.transform = "translateX(0)";
  }

  function restoreTransition() {
    button.style.transition = "left .18s ease, top .18s ease, opacity .18s ease, background .18s ease";
  }

  function watchPanel() {
    clearInterval(panelWatchTimer);
    panelWatchTimer = setInterval(() => {
      if (!document.getElementById("__chromext_runtime_panel__")) {
        clearInterval(panelWatchTimer);
        dock();
      }
    }, 400);
  }

  function openPanel(event) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    if (Date.now() - openedAt < 350) return;
    openedAt = Date.now();
    expand();
    globalThis.__ChromeXtOpenRuntimePanel__();
    watchPanel();
  }

  button.addEventListener(
    "pointerdown",
    (event) => {
      startX = event.clientX;
      startY = event.clientY;
      const rect = button.getBoundingClientRect();
      dragOffsetX = event.clientX - rect.left;
      dragOffsetY = event.clientY - rect.top;
      moved = false;
      button.setPointerCapture(event.pointerId);
    },
    true
  );

  button.addEventListener(
    "pointermove",
    (event) => {
      if (!button.hasPointerCapture(event.pointerId)) return;
      const dx = event.clientX - startX;
      const dy = event.clientY - startY;
      if (Math.abs(dx) + Math.abs(dy) > 8) moved = true;
      floatAt(event.clientX - dragOffsetX, event.clientY - dragOffsetY);
      button.style.right = "auto";
      button.style.bottom = "auto";
    },
    true
  );

  button.addEventListener(
    "pointerup",
    (event) => {
      if (button.hasPointerCapture(event.pointerId)) {
        button.releasePointerCapture(event.pointerId);
      }
      if (!moved) {
        openPanel(event);
      } else if (!document.getElementById("__chromext_runtime_panel__")) {
        const rect = button.getBoundingClientRect();
        side = rect.left + rect.width / 2 > innerWidth / 2 ? "right" : "left";
        topPx = clampTop(rect.top);
        savePosition();
        restoreTransition();
        dock();
      } else {
        restoreTransition();
        expand();
      }
    },
    true
  );

  button.addEventListener("click", openPanel, true);
  button.addEventListener(
    "touchend",
    (event) => {
      if (!moved) openPanel(event);
    },
    true
  );

  function mount() {
    try {
      dock();
      (document.body || document.documentElement).appendChild(button);
    } catch {
      setTimeout(mount, 100);
    }
  }

  function requestSettings() {
    if (ChromeXt) ChromeXt.dispatch("runtimeLauncher", { read: true });
  }

  addEventListener(
    "resize",
    () => {
      topPx = clampTop(topPx);
      if (document.getElementById("__chromext_runtime_panel__")) expand();
      else dock();
    },
    { passive: true }
  );

  if (ChromeXt) {
    ChromeXt.addEventListener("runtimeLauncherPosition", (event) => {
      const data = event.detail || {};
      if (data.enabled === false) {
        document.getElementById("__chromext_runtime_panel__")?.remove();
        button.remove();
        return;
      }
      globalThis.__ChromeXtLanguage = data.language || "system";
      side = data.side === "right" ? "right" : "left";
      topPx = Number.isFinite(data.top) ? data.top : 58;
      if (!button.isConnected) mount();
      if (document.getElementById("__chromext_runtime_panel__")) expand();
      else dock();
    });
  }

  requestSettings();
  if (!ChromeXt) {
    mount();
  }
})();
