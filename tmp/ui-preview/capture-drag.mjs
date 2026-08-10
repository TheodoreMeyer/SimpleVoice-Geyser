/**
 * One-off drag/custom dashboard screenshots (test-only).
 */
import { spawn } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import net from "node:net";
import path from "node:path";

const chrome =
    process.env.CHROME_PATH ||
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const debugPort = 9334;
const out = path.resolve("tmp/screenshots/baseline");
fs.mkdirSync(out, { recursive: true });

function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
}

async function waitPort(port) {
    for (let i = 0; i < 50; i++) {
        const ok = await new Promise((res) => {
            const s = net.connect({ port, host: "127.0.0.1" }, () => {
                s.end();
                res(true);
            });
            s.on("error", () => res(false));
        });
        if (ok) return;
        await sleep(100);
    }
    throw new Error("debug port not ready");
}

class Cdp {
    constructor(wsUrl) {
        this.wsUrl = wsUrl;
        this.nextId = 1;
        this.pending = new Map();
    }

    async connect() {
        this.ws = new WebSocket(this.wsUrl);
        await new Promise((res, rej) => {
            this.ws.addEventListener("open", res, { once: true });
            this.ws.addEventListener("error", rej, { once: true });
        });
        this.ws.addEventListener("message", (ev) => {
            const msg = JSON.parse(ev.data);
            if (msg.id && this.pending.has(msg.id)) {
                const { resolve, reject } = this.pending.get(msg.id);
                this.pending.delete(msg.id);
                if (msg.error) reject(new Error(JSON.stringify(msg.error)));
                else resolve(msg.result);
            }
        });
    }

    send(method, params = {}) {
        const id = this.nextId++;
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject });
            this.ws.send(JSON.stringify({ id, method, params }));
        });
    }

    close() {
        try {
            this.ws.close();
        } catch {
            // ignore
        }
    }
}

const profile = path.resolve("tmp/screenshots/.cdp-drag-profile");
fs.rmSync(profile, { recursive: true, force: true });
fs.mkdirSync(profile, { recursive: true });

const child = spawn(
    chrome,
    [
        `--remote-debugging-port=${debugPort}`,
        "--headless=new",
        "--disable-gpu",
        `--user-data-dir=${profile}`,
        "about:blank"
    ],
    { stdio: "ignore" }
);

await waitPort(debugPort);
const created = await new Promise((resolve, reject) => {
    const req = http.request(
        {
            hostname: "127.0.0.1",
            port: debugPort,
            path: "/json/new?about:blank",
            method: "PUT"
        },
        (res) => {
            let d = "";
            res.on("data", (c) => (d += c));
            res.on("end", () => resolve(JSON.parse(d)));
        }
    );
    req.on("error", reject);
    req.end();
});

const cdp = new Cdp(created.webSocketDebuggerUrl);
await cdp.connect();
await cdp.send("Page.enable");
await cdp.send("Runtime.enable");
await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: 1440,
    height: 900,
    deviceScaleFactor: 1,
    mobile: false
});
await cdp.send("Page.navigate", {
    url: "http://127.0.0.1:8765/preview/?state=dashboard"
});
await sleep(900);

await cdp.send("Runtime.evaluate", {
    expression: `
      const grid = document.querySelector('[data-svg="dash.grid"]');
      const panel = document.querySelector('[data-panel="voice"]');
      panel.classList.add('is-dragging');
      document.body.classList.add('is-panel-dragging');
      let hint = document.querySelector('.dash-drop-hint');
      if (!hint) {
        hint = document.createElement('div');
        hint.className = 'dash-drop-hint';
        grid.appendChild(hint);
      }
      hint.hidden = false;
      hint.style.gridColumn = '1 / span 7';
      hint.style.gridRow = '1 / span 8';
      true;
    `
});
await sleep(200);
const dragShot = await cdp.send("Page.captureScreenshot", { format: "png" });
fs.writeFileSync(
    path.join(out, "dashboard-dragging-1440x900.png"),
    Buffer.from(dragShot.data, "base64")
);

await cdp.send("Runtime.evaluate", {
    expression: `
      document.body.classList.remove('is-panel-dragging');
      document.querySelector('[data-panel="voice"]')?.classList.remove('is-dragging');
      const hint = document.querySelector('.dash-drop-hint');
      if (hint) hint.hidden = true;
      const v = document.querySelector('[data-panel="voice"]');
      const c = document.querySelector('[data-panel="chat"]');
      const g = document.querySelector('[data-panel="groups"]');
      v.style.gridColumn = '8 / span 5';
      v.style.gridRow = '1 / span 12';
      c.style.gridColumn = '1 / span 7';
      c.style.gridRow = '1 / span 8';
      g.style.gridColumn = '1 / span 7';
      g.style.gridRow = '9 / span 4';
      true;
    `
});
await sleep(250);
const customShot = await cdp.send("Page.captureScreenshot", { format: "png" });
fs.writeFileSync(
    path.join(out, "dashboard-custom-1440x900.png"),
    Buffer.from(customShot.data, "base64")
);

cdp.close();
child.kill();
console.log("Wrote drag + custom screenshots");
