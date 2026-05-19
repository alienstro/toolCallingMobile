const iconMap = {
  chat: '<path d="M4 5.5h16v10.5H9l-5 4v-14.5Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M8 10h8M8 13h5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
  models: '<path d="M5 7h14M5 12h14M5 17h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="9" cy="7" r="2" fill="currentColor"/><circle cx="15" cy="12" r="2" fill="currentColor"/><circle cx="11" cy="17" r="2" fill="currentColor"/>',
  settings: '<path d="M5 8h14M5 16h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="10" cy="8" r="3" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="15" cy="16" r="3" fill="none" stroke="currentColor" stroke-width="2"/>',
  diag: '<path d="M5 19V5M5 19h15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="m8 15 3-4 3 2 4-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  camera: '<path d="M7 8h2l1.5-2h3L15 8h2a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="13" r="3" fill="none" stroke="currentColor" stroke-width="2"/>',
  image: '<rect x="4" y="5" width="16" height="14" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="m7 16 4-4 3 3 2-2 2 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><circle cx="9" cy="9" r="1.5" fill="currentColor"/>',
  send: '<path d="M4 11.5 20 4l-7.5 16-2-6.5L4 11.5Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>',
  stop: '<rect x="7" y="7" width="10" height="10" rx="2" fill="currentColor"/>',
  plus: '<path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
  trash: '<path d="M6 7h12M10 7V5h4v2M8 10v8M12 10v8M16 10v8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M8 7h8l-1 13H9L8 7Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>',
  download: '<path d="M12 4v10m0 0 4-4m-4 4-4-4M5 19h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  file: '<path d="M7 4h7l4 4v12H7V4Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M14 4v5h4" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/>'
};

function svgIcon(name) {
  return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${iconMap[name] || ""}</svg>`;
}

function nav(active) {
  const items = [
    ["chat", "Chat", "chat.html"],
    ["models", "Models", "models.html"],
    ["settings", "Settings", "settings.html"],
    ["diag", "Diag", "diagnostics.html"]
  ];
  return `<nav class="bottom-nav" aria-label="Primary">${items.map(([key, label, href]) => `<a class="nav-item ${active === key ? "active" : ""}" href="${href}">${svgIcon(key)}<span>${label}</span></a>`).join("")}</nav>`;
}

function chrome(active, title, model, statusClass, statusText, content, composer = "") {
  document.body.classList.add("screen");
  document.body.innerHTML = `
    <main class="android-app">
      <div>
        <div class="statusbar"><span>9:41</span><span class="status-icons"><span class="dot-bars"></span><span>5G</span><span class="battery"></span></span></div>
        <header class="topbar">
          <div class="screen-title">
            <h1>${title}</h1>
            <span class="runtime-pill"><span class="status-dot ${statusClass}"></span>${statusText}</span>
          </div>
          <div class="top-status">
            <span class="model-line">${model}</span>
            <span class="mini-chip">GPU requested</span>
          </div>
        </header>
      </div>
      <section class="content">${content}</section>
      ${composer}
      ${nav(active)}
    </main>`;
  hydrateControls();
}

function hydrateControls() {
  document.querySelectorAll("[data-icon]").forEach((node) => {
    node.insertAdjacentHTML("afterbegin", svgIcon(node.dataset.icon));
  });
  document.querySelectorAll(".switch").forEach((node) => {
    node.addEventListener("click", () => {
      if (!node.classList.contains("disabled")) node.classList.toggle("on");
    });
  });
  document.querySelectorAll("[data-fill-input]").forEach((node) => {
    node.addEventListener("click", () => {
      const target = document.querySelector(node.dataset.fillInput);
      if (target) target.value = node.dataset.value || "";
    });
  });
  document.querySelectorAll("[data-toast]").forEach((node) => {
    node.addEventListener("click", () => showToast(node.dataset.toast));
  });
  hydrateAttachMenus();
}

function hydrateAttachMenus() {
  document.querySelectorAll("[data-attach-toggle]").forEach((toggle) => {
    toggle.addEventListener("click", (event) => {
      event.stopPropagation();
      const menu = toggle.closest(".attach-menu");
      const isOpen = menu.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(isOpen));
    });
  });
  document.querySelectorAll(".attach-popover button").forEach((option) => {
    option.addEventListener("click", () => {
      const menu = option.closest(".attach-menu");
      const toggle = menu && menu.querySelector("[data-attach-toggle]");
      if (menu) menu.classList.remove("open");
      if (toggle) toggle.setAttribute("aria-expanded", "false");
    });
  });
  document.addEventListener("click", () => closeAttachMenus());
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeAttachMenus();
  });
}

function closeAttachMenus() {
  document.querySelectorAll(".attach-menu.open").forEach((menu) => {
    menu.classList.remove("open");
    const toggle = menu.querySelector("[data-attach-toggle]");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
  });
}

function showToast(message) {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();
  const warningPattern = /unavailable|blocked|error|fail|remove|delete|running/i;
  const toast = document.createElement("div");
  toast.className = `toast ${warningPattern.test(message) ? "is-warning" : "is-info"}`;
  toast.setAttribute("role", warningPattern.test(message) ? "alert" : "status");
  toast.setAttribute("aria-live", warningPattern.test(message) ? "assertive" : "polite");
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 2600);
}

window.LiteRT = { chrome, svgIcon };
