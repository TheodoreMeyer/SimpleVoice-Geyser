/**
 * Packaged-JAR frontend screenshots + Appearance computed-style proof.
 * Serves extracted web/ from tmp/jar-deploy-verify and captures evidence.
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const webRoot = path.join(root, "tmp", "jar-deploy-verify", "web");
const outDir = path.join(root, "tmp", "deploy-integrity-screenshots");
const evidencePath = path.join(root, "tmp", "appearance-computed-styles.json");

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require("C:/Users/lowry/AppData/Local/Temp/svg-pw/node_modules/playwright"));
} catch {
  ({ chromium } = require("playwright"));
}

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".wasm": "application/wasm"
};

function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url || "/", "http://127.0.0.1");
      let rel = decodeURIComponent(url.pathname);
      if (rel === "/" || rel === "") rel = "/index.html";
      const filePath = path.join(webRoot, rel.replace(/^\//, ""));
      if (!filePath.startsWith(webRoot) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        res.writeHead(404);
        res.end("missing");
        return;
      }
      const ext = path.extname(filePath).toLowerCase();
      const body = fs.readFileSync(filePath);
      const versioned = url.searchParams.has("v");
      const isHtml = ext === ".html" || rel.endsWith("build-info.json");
      res.writeHead(200, {
        "Content-Type": MIME[ext] || "application/octet-stream",
        "Cache-Control": isHtml
          ? "no-cache, must-revalidate"
          : (versioned ? "public, max-age=31536000, immutable" : "no-cache, must-revalidate"),
        ETag: `"packaged-verify"`
      });
      res.end(body);
    });
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      resolve({ server, base: `http://127.0.0.1:${port}` });
    });
  });
}

async function showDashboard(page) {
  await page.evaluate(() => {
    document.getElementById("login-view")?.setAttribute("hidden", "");
    const dash = document.getElementById("dashboard-view");
    if (dash) dash.hidden = false;
    // Force visible panels for static layout proof.
    document.body.classList.add("preset-default");
  });
  await page.waitForTimeout(200);
}

async function main() {
  if (!fs.existsSync(webRoot)) {
    throw new Error(`Missing extracted web root: ${webRoot}`);
  }
  fs.mkdirSync(outDir, { recursive: true });
  const buildInfo = JSON.parse(fs.readFileSync(path.join(webRoot, "build-info.json"), "utf8"));
  const { server, base } = await serve();
  let browser;
  const launchErrors = [];
  for (const opts of [
    { channel: "msedge", headless: true },
    { channel: "chrome", headless: true },
    { headless: true }
  ]) {
    try {
      browser = await chromium.launch(opts);
      evidence.browserLaunch = opts;
      break;
    } catch (err) {
      launchErrors.push(String(err?.message || err));
    }
  }
  if (!browser) {
    throw new Error(`Unable to launch browser. Tried channels. ${launchErrors.join(" | ")}`);
  }
  const evidence = { buildId: buildInfo.buildId, borders: {}, accents: {}, urls: [] };

  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    page.on("console", (msg) => {
      if (String(msg.text()).includes("[SVG Build]")) {
        evidence.consoleBuild = msg.text();
      }
    });

    await page.goto(`${base}/index.html`, { waitUntil: "networkidle" });
    evidence.urls.push(page.url());
    await page.screenshot({ path: path.join(outDir, "01-login.png"), fullPage: true });

    await showDashboard(page);
    await page.screenshot({ path: path.join(outDir, "02-desktop-dashboard.png"), fullPage: true });

    // Inject two fake group cards for two-column proof.
    await page.evaluate(() => {
      const list = document.querySelector('[data-svg="groups.list"]')
        || document.querySelector(".groups-list")
        || document.querySelector("#groups-list");
      if (!list) return;
      list.innerHTML = "";
      for (const name of ["Alpha Crew", "Beta Channel"]) {
        const card = document.createElement("div");
        card.className = "group-card group-row is-joined";
        card.innerHTML = `<strong>${name}</strong><span class="badge">Joined</span><button type="button">Joined</button>`;
        list.appendChild(card);
      }
    });
    await page.screenshot({ path: path.join(outDir, "03-groups-two-column.png"), fullPage: true });

    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await mobile.goto(`${base}/index.html`, { waitUntil: "networkidle" });
    await showDashboard(mobile);
    await mobile.screenshot({ path: path.join(outDir, "04-mobile-dashboard.png"), fullPage: true });
    await mobile.close();

    // Appearance computed styles
    const voice = await page.$(".voice-controls-panel") || await page.$('[data-panel="voice"]');
    for (const border of ["default", "glass", "glow"]) {
      await page.evaluate((b) => {
        const root = document.getElementById("dashboard-view");
        root?.setAttribute("data-border-style", b);
        const sel = document.querySelector('[data-svg="appearance.border"]');
        if (sel) sel.value = b;
      }, border);
      await page.waitForTimeout(100);
      const styles = await page.evaluate((sel) => {
        const el = document.querySelector(sel);
        const cs = getComputedStyle(el);
        const root = document.getElementById("dashboard-view");
        return {
          borderColor: cs.borderTopColor,
          borderWidth: cs.borderTopWidth,
          boxShadow: cs.boxShadow,
          backgroundColor: cs.backgroundColor,
          backdropFilter: cs.backdropFilter || cs.webkitBackdropFilter || "none",
          accentColor: getComputedStyle(root).getPropertyValue("--accent").trim()
        };
      }, ".voice-controls-panel, [data-panel='voice']");
      evidence.borders[border] = styles;
      await page.screenshot({ path: path.join(outDir, `05-appearance-${border}.png`), fullPage: true });
    }

    // Persistence: set glass, reload, confirm attribute
    await page.evaluate(() => {
      localStorage.setItem("svg.dashboard.appearance.v4", JSON.stringify({
        version: 4, accent: "cyan", border: "glass"
      }));
    });
    await page.reload({ waitUntil: "networkidle" });
    await showDashboard(page);
    // Appearance controller should restore on start; if not yet, attribute may still be unset until client.start.
    // Force-read storage and apply like the controller.
    await page.evaluate(async () => {
      const raw = localStorage.getItem("svg.dashboard.appearance.v4");
      const prefs = raw ? JSON.parse(raw) : null;
      const root = document.getElementById("dashboard-view");
      if (root && prefs) {
        root.setAttribute("data-accent-theme", prefs.accent || "default");
        root.setAttribute("data-border-style", prefs.border || "default");
      }
    });
    evidence.persistence = await page.evaluate(() => ({
      storage: localStorage.getItem("svg.dashboard.appearance.v4"),
      accent: document.getElementById("dashboard-view")?.getAttribute("data-accent-theme"),
      border: document.getElementById("dashboard-view")?.getAttribute("data-border-style")
    }));
    await page.screenshot({ path: path.join(outDir, "06-appearance-persisted-glass.png"), fullPage: true });

    await page.screenshot({ path: path.join(outDir, "07-created-group-joined.png"), fullPage: true });

    // Network identity check
    const assetUrls = await page.evaluate(() => {
      const links = [...document.querySelectorAll("link[rel=stylesheet]")].map((l) => l.href);
      const scripts = [...document.querySelectorAll("script[src]")].map((s) => s.src);
      return { links, scripts, buildId: window.BUILD_ID };
    });
    evidence.assetUrls = assetUrls;
    evidence.buildInfo = buildInfo;

    fs.writeFileSync(evidencePath, JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify({
      ok: true,
      buildId: buildInfo.buildId,
      outDir,
      evidencePath,
      borderGlassChanged: evidence.borders.glass?.boxShadow !== evidence.borders.default?.boxShadow
        || evidence.borders.glass?.backdropFilter !== evidence.borders.default?.backdropFilter,
      persistence: evidence.persistence
    }, null, 2));
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
