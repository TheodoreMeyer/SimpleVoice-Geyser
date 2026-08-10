import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("C:/Users/lowry/AppData/Local/Temp/svg-pw/node_modules/playwright");

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "core", "web");
const outDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "verify-screenshots");
fs.mkdirSync(outDir, { recursive: true });

const mime = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml"
};

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent((req.url || "/").split("?")[0]);
  if (urlPath === "/") urlPath = "/index.html";
  const filePath = path.normalize(path.join(root, urlPath.replace(/^\//, "")));
  if (!filePath.startsWith(root) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    res.writeHead(404);
    res.end("not found");
    return;
  }
  res.writeHead(200, { "Content-Type": mime[path.extname(filePath)] || "application/octet-stream", "Cache-Control": "no-store" });
  fs.createReadStream(filePath).pipe(res);
});

await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const base = `http://127.0.0.1:${server.address().port}/`;

const chromePath = [
  process.env.CHROME_PATH,
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
].find((p) => p && fs.existsSync(p));

const browser = await chromium.launch({ executablePath: chromePath, headless: true });

async function shot(name, width, height, setup) {
  const page = await browser.newPage({ viewport: { width, height } });
  const errors = [];
  page.on("pageerror", (err) => errors.push(err.message));
  await page.goto(base, { waitUntil: "networkidle" });
  await page.evaluate(() => {
    for (const c of [...document.body.classList]) {
      if (c.startsWith("preset-")) document.body.classList.remove(c);
    }
    document.body.classList.add("preset-default");
  });
  if (setup) await setup(page);
  await page.screenshot({ path: path.join(outDir, `${name}.png`) });
  if (errors.length) {
    console.error(name, "pageerrors", errors);
  } else {
    console.log("ok", name);
  }
  await page.close();
  return errors;
}

await shot("desktop-login-1440x900", 1440, 900);
await shot("mobile-login-390x844", 390, 844);

await shot("desktop-dashboard-1440x900", 1440, 900, async (page) => {
  await page.evaluate(() => {
    document.getElementById("login-view").hidden = true;
    const dash = document.getElementById("dashboard-view");
    dash.hidden = false;
    dash.setAttribute("aria-hidden", "false");
    document.querySelector('[data-svg="dash.player"]').textContent = "Steve";
    const ws = document.querySelector('[data-svg="dash.ws-status"]');
    ws.textContent = "Connected";
    ws.dataset.state = "connected";
    document.querySelector('[data-svg="dash.audio-mode"]').textContent = "WEB_VOICE";
    document.querySelector('[data-svg="groups.current"]').textContent = "Builders";
    document.querySelector('[data-svg="groups.create"]').disabled = false;
    document.querySelector('[data-svg="groups.leave"]').disabled = false;
    const list = document.querySelector('[data-svg="groups.list"]');
    list.innerHTML = "";
    for (const [name, meta, joined] of [
      ["Builders", "ISOLATED · 2 members · Joined", true],
      ["Staff", "NORMAL · 1 member", false],
      ["Town Square", "OPEN · 4 members", false]
    ]) {
      const article = document.createElement("article");
      article.className = "group-card group-row" + (joined ? " is-joined" : "");
      article.innerHTML =
        `<div class="group-row-main"><h3 class="group-name">${name}${name === "Staff" ? ' <span class="group-lock">· locked</span>' : ""}</h3>` +
        `<p class="group-meta">${meta}</p></div>` +
        `<div class="group-row-actions"><button type="button" class="btn btn-secondary">${joined ? "Joined" : "Join"}</button></div>`;
      list.appendChild(article);
    }
  });
});

await shot("desktop-create-modal-1440x900", 1440, 900, async (page) => {
  await page.evaluate(() => {
    document.getElementById("login-view").hidden = true;
    const dash = document.getElementById("dashboard-view");
    dash.hidden = false;
    document.querySelector('[data-svg="dash.player"]').textContent = "Dave";
    const ws = document.querySelector('[data-svg="dash.ws-status"]');
    ws.textContent = "Connected";
    ws.dataset.state = "connected";
    document.querySelector('[data-svg="dash.audio-mode"]').textContent = "WEB_VOICE";
    document.querySelector('[data-svg="groups.current"]').textContent = "Builders";
    document.getElementById("create-group-dialog").showModal();
  });
});

await shot("desktop-after-create-1440x900", 1440, 900, async (page) => {
  await page.evaluate(() => {
    document.getElementById("login-view").hidden = true;
    document.getElementById("dashboard-view").hidden = false;
    document.querySelector('[data-svg="dash.player"]').textContent = "Steve";
    const ws = document.querySelector('[data-svg="dash.ws-status"]');
    ws.textContent = "Connected";
    ws.dataset.state = "connected";
    document.querySelector('[data-svg="dash.audio-mode"]').textContent = "WEB_VOICE";
    document.querySelector('[data-svg="groups.current"]').textContent = "Night Watch";
    document.querySelector('[data-svg="groups.create"]').disabled = false;
    const list = document.querySelector('[data-svg="groups.list"]');
    list.innerHTML = "";
    const article = document.createElement("article");
    article.className = "group-card group-row is-joined";
    article.innerHTML =
      `<div class="group-row-main"><h3 class="group-name">Night Watch</h3>` +
      `<p class="group-meta">ISOLATED · 1 member · <span class="group-joined-badge">Joined</span></p></div>` +
      `<div class="group-row-actions"><button type="button" class="btn btn-secondary">Joined</button></div>`;
    list.appendChild(article);
  });
});

const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(base, { waitUntil: "networkidle" });
const checks = await page.evaluate(async () => {
  const login = document.getElementById("login-view");
  const dash = document.getElementById("dashboard-view");
  const create = document.getElementById("create-group-dialog");
  const protectedDlg = document.getElementById("protected-group-dialog");
  const initially = {
    loginHidden: login.hidden,
    dashHidden: dash.hidden,
    createOpen: create.open,
    protectedOpen: protectedDlg.open
  };

  let copied = "";
  const original = navigator.clipboard.writeText.bind(navigator.clipboard);
  navigator.clipboard.writeText = async (text) => {
    copied = text;
  };
  document.getElementById("copy-svg-command").click();
  await new Promise((r) => setTimeout(r, 30));
  navigator.clipboard.writeText = original;

  login.hidden = true;
  dash.hidden = false;
  create.showModal();

  return {
    initially,
    openDialogs: [...document.querySelectorAll("dialog[open]")].map((d) => d.id),
    footerCredits: [...document.querySelectorAll(".footer-credits span")].map((s) => s.textContent.trim()),
    copied,
    createFields: {
      name: !!document.getElementById("create-group-name"),
      password: !!document.getElementById("create-group-password"),
      type: !!document.getElementById("create-group-type"),
      error: !!document.getElementById("create-group-error"),
      help: !!document.getElementById("group-type-help")
    }
  };
});
console.log("CHECKS", JSON.stringify(checks, null, 2));
await page.close();
await browser.close();
server.close();
console.log("Wrote screenshots to", outDir);
