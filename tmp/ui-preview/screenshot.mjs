/**
 * Accurate viewport screenshots via Chrome DevTools Protocol.
 * Test-only — not packaged into the JAR.
 */
import { spawn } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import net from "node:net";
import path from "node:path";
import { fileURLToPath } from "node:url";

const chrome =
    process.env.CHROME_PATH ||
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const outRoot = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    "../screenshots"
);
const debugPort = 9333;

const sizes = [
    { name: "1440x900", width: 1440, height: 900, mobile: false },
    { name: "1024x768", width: 1024, height: 768, mobile: false },
    { name: "390x844", width: 390, height: 844, mobile: true }
];

const states = [
    { name: "01-login", url: "http://127.0.0.1:8765/preview/?state=login" },
    { name: "02-dashboard", url: "http://127.0.0.1:8765/preview/?state=dashboard" },
    { name: "03-create", url: "http://127.0.0.1:8765/preview/?state=create" },
    { name: "04-protected", url: "http://127.0.0.1:8765/preview/?state=protected" }
];

function sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
}

function httpGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, (res) => {
            let data = "";
            res.on("data", (c) => (data += c));
            res.on("end", () => resolve({ status: res.statusCode, data, headers: res.headers }));
        }).on("error", reject);
    });
}

async function waitPort(port, timeoutMs = 20000) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
        const ok = await new Promise((resolve) => {
            const s = net.connect({ port, host: "127.0.0.1" }, () => {
                s.end();
                resolve(true);
            });
            s.on("error", () => resolve(false));
        });
        if (ok) return;
        await sleep(150);
    }
    throw new Error("debug port not ready");
}

class Cdp {
    constructor(wsUrl) {
        this.wsUrl = wsUrl;
        this.ws = null;
        this.nextId = 1;
        this.pending = new Map();
    }

    async connect() {
        this.ws = new WebSocket(this.wsUrl);
        await new Promise((resolve, reject) => {
            this.ws.addEventListener("open", resolve, { once: true });
            this.ws.addEventListener("error", reject, { once: true });
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
            setTimeout(() => {
                if (this.pending.has(id)) {
                    this.pending.delete(id);
                    reject(new Error(`timeout ${method}`));
                }
            }, 30000);
        });
    }

    close() {
        try { this.ws?.close(); } catch {}
    }
}

async function main() {
    fs.mkdirSync(outRoot, { recursive: true });
    const profile = path.join(outRoot, ".cdp-profile");
    fs.rmSync(profile, { recursive: true, force: true });
    fs.mkdirSync(profile, { recursive: true });

    const child = spawn(chrome, [
        `--remote-debugging-port=${debugPort}`,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        `--user-data-dir=${profile}`,
        "about:blank"
    ], { stdio: "ignore" });

    try {
        await waitPort(debugPort);
        // Prefer PUT /json/new on modern Chrome
        let pageWs = null;
        try {
            const created = await new Promise((resolve, reject) => {
                const req = http.request({
                    hostname: "127.0.0.1",
                    port: debugPort,
                    path: "/json/new?about:blank",
                    method: "PUT"
                }, (res) => {
                    let data = "";
                    res.on("data", (c) => (data += c));
                    res.on("end", () => resolve({ status: res.statusCode, data }));
                });
                req.on("error", reject);
                req.end();
            });
            if (created.status === 200) {
                pageWs = JSON.parse(created.data).webSocketDebuggerUrl;
            }
        } catch {
            // fall through
        }
        if (!pageWs) {
            const list = JSON.parse((await httpGet(`http://127.0.0.1:${debugPort}/json/list`)).data);
            const page = list.find((t) => t.type === "page") || list[0];
            pageWs = page.webSocketDebuggerUrl;
        }

        const cdp = new Cdp(pageWs);
        await cdp.connect();
        await cdp.send("Page.enable");
        await cdp.send("Runtime.enable");

        for (const size of sizes) {
            for (const state of states) {
                const dir = path.join(outRoot, size.name, state.name);
                fs.mkdirSync(dir, { recursive: true });
                const shotPath = path.join(dir, "shot.png");

                await cdp.send("Emulation.setDeviceMetricsOverride", {
                    width: size.width,
                    height: size.height,
                    deviceScaleFactor: 1,
                    mobile: size.mobile
                });
                await cdp.send("Page.navigate", { url: state.url });
                await cdp.send("Page.loadEventFired").catch(() => {});
                await sleep(400);
                for (let i = 0; i < 30; i++) {
                    const ready = await cdp.send("Runtime.evaluate", {
                        expression: "document.body && document.body.classList.contains('harness-ready')",
                        returnByValue: true
                    });
                    if (ready?.result?.value) break;
                    await sleep(100);
                }
                // Collect layout diagnostics for mobile login
                if (state.name === "01-login") {
                    const diag = await cdp.send("Runtime.evaluate", {
                        expression: `(() => ({
                          iw: window.innerWidth,
                          sw: document.documentElement.scrollWidth,
                          overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
                          loginW: document.querySelector('.login-card')?.getBoundingClientRect().width || 0
                        }))()`,
                        returnByValue: true
                    });
                    console.log(size.name, state.name, diag.result.value);
                }
                const shot = await cdp.send("Page.captureScreenshot", {
                    format: "png",
                    fromSurface: true,
                    captureBeyondViewport: false
                });
                fs.writeFileSync(shotPath, Buffer.from(shot.data, "base64"));
                console.log("wrote", shotPath);
            }
        }
        cdp.close();
    } finally {
        child.kill();
    }
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
