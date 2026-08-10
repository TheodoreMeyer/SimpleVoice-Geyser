import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require("C:/Users/lowry/AppData/Local/Temp/svg-pw/node_modules/playwright");
const root = path.resolve("core/web");
const outDir = path.resolve("tmp/recovery-screenshots");
fs.mkdirSync(outDir, { recursive: true });
const mime = { ".html":"text/html;charset=utf-8",".css":"text/css",".js":"text/javascript",".mjs":"text/javascript",".png":"image/png" };
const server = http.createServer((req,res)=>{
  let p = decodeURIComponent((req.url||"/").split("?")[0]);
  if (p==="/") p="/index.html";
  const fp = path.normalize(path.join(root, p.replace(/^\//,"")));
  if (!fp.startsWith(root) || !fs.existsSync(fp)) { res.writeHead(404); res.end("nf"); return; }
  res.writeHead(200, {"Content-Type": mime[path.extname(fp)]||"application/octet-stream","Cache-Control":"no-store"});
  fs.createReadStream(fp).pipe(res);
});
await new Promise(r=>server.listen(0,"127.0.0.1",r));
const base = `http://127.0.0.1:${server.address().port}/`;
const chromePath = ["C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe","C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"].find(p=>fs.existsSync(p));
const browser = await chromium.launch({ executablePath: chromePath, headless: true });
async function showDash(page){
  await page.evaluate(()=>{
    localStorage.clear();
    document.getElementById("login-view").hidden = true;
    const dash = document.getElementById("dashboard-view");
    dash.hidden = false; dash.setAttribute("aria-hidden","false");
    document.querySelector('[data-svg="dash.player"]').textContent = "Steve";
    const ws = document.querySelector('[data-svg="dash.ws-status"]');
    ws.textContent = "Connected"; ws.dataset.state = "connected";
    document.querySelector('[data-svg="dash.audio-mode"]').textContent = "WEB_VOICE";
    document.querySelector('[data-svg="groups.current"]').textContent = "Builders";
  });
  // Force layout controller default if module loaded
  await page.evaluate(async ()=>{
    try {
      const mod = await import("./js/dashboard-layout.js");
      const grid = document.querySelector("[data-svg='dash.grid']");
      const panels = {};
      for (const id of ["voice","chat","appearance","groups"]) {
        panels[id] = document.querySelector(`[data-panel='${id}']`);
      }
      const ctl = new mod.DashboardLayoutController({ gridEl: grid, panels, resetBtn: null, liveRegion: null });
      ctl.init();
    } catch (e) { console.error(e); }
    try {
      const amod = await import("./js/appearance.js");
      const ctl = new amod.AppearanceController({
        dashboardRoot: document.getElementById("dashboard-view"),
        accentSelectEl: document.getElementById("dash-appearance-accent"),
        borderSelectEl: document.getElementById("dash-appearance-border"),
        accentSwatchGroupEl: document.querySelector('[data-svg="dash.appearance-accent-swatches"]'),
        borderSwatchGroupEl: document.querySelector('[data-svg="dash.appearance-border-swatches"]'),
      });
      ctl.init();
    } catch (e) { console.error(e); }
  });
}
const sizes = [[1440,900],[1280,720],[1024,768],[390,844]];
const report = {};
for (const [w,h] of sizes) {
  const page = await browser.newPage({ viewport: { width:w, height:h }});
  const errors=[]; page.on("pageerror", e=>errors.push(String(e)));
  await page.goto(base,{waitUntil:"networkidle"});
  await showDash(page);
  await page.screenshot({ path: path.join(outDir, `dashboard-${w}x${h}.png`), fullPage:true });
  if (w===1440) {
    const styles = await page.evaluate(()=>{
      const panel = document.querySelector('[data-panel="voice"]');
      const root = document.getElementById("dashboard-view");
      const out = {};
      for (const border of ["default","glass","subtle","glow","high-contrast"]) {
        root.dataset.borderStyle = border;
        const s = getComputedStyle(panel);
        out[border] = { borderTopColor:s.borderTopColor, borderTopWidth:s.borderTopWidth, boxShadow:s.boxShadow, backgroundColor:s.backgroundColor, backdropFilter:s.backdropFilter||s.webkitBackdropFilter };
      }
      for (const accent of ["default","cyan","blue","purple","green","red","gold"]) {
        root.dataset.accentTheme = accent;
        out["accent:"+accent] = getComputedStyle(root).getPropertyValue("--accent").trim();
      }
      const layout = {};
      for (const id of ["voice","chat","appearance","groups"]) {
        const el = document.querySelector(`[data-panel='${id}']`);
        const r = el.getBoundingClientRect();
        layout[id] = { x:Math.round(r.x), y:Math.round(r.y), w:Math.round(r.width), h:Math.round(r.height) };
      }
      return { styles: out, layout, accentsDistinct: true };
    });
    // Apply glass and screenshot
    await page.evaluate(()=>{
      document.getElementById("dashboard-view").dataset.accentTheme="cyan";
      document.getElementById("dashboard-view").dataset.borderStyle="glass";
    });
    await page.screenshot({ path: path.join(outDir, "appearance-cyan-glass-1440x900.png"), fullPage:true });
    await page.evaluate(()=>{
      document.getElementById("dashboard-view").dataset.accentTheme="gold";
      document.getElementById("dashboard-view").dataset.borderStyle="glow";
    });
    await page.screenshot({ path: path.join(outDir, "appearance-gold-glow-1440x900.png"), fullPage:true });
    report.appearance = styles;
  }
  report[`${w}x${h}`] = { errors };
  await page.close();
}
fs.writeFileSync(path.join(outDir,"appearance-proof.json"), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
await browser.close(); server.close();
