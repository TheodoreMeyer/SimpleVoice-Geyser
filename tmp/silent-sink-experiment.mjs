/**
 * Playwright runner for silent-sink AudioWorklet experiment.
 * Run: node tmp/silent-sink-experiment.mjs
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const audioRoot = path.join(repoRoot, "core", "web", "js", "audio");
const resultsPath = path.join(__dirname, "silent-sink-results.json");

const pwPath = "C:/Users/lowry/AppData/Local/Temp/svg-pw/node_modules/playwright";
const require = createRequire(import.meta.url);
let chromium;
try {
    ({ chromium } = require(pwPath));
} catch {
    const tmpPw = path.join(__dirname, "node_modules", "playwright");
    if (!fs.existsSync(path.join(tmpPw, "package.json"))) {
        const { execSync } = await import("node:child_process");
        execSync("npm init -y && npm install playwright --no-save", {
            cwd: __dirname,
            stdio: "inherit"
        });
    }
    ({ chromium } = require(path.join(tmpPw)));
}

const mime = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8"
};

const server = http.createServer((req, res) => {
    let urlPath = decodeURIComponent((req.url || "/").split("?")[0]);
    if (urlPath === "/") {
        urlPath = "/silent-sink-experiment.html";
    }
    const filePath = path.normalize(path.join(audioRoot, urlPath.replace(/^\//, "")));
    if (!filePath.startsWith(audioRoot) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        res.writeHead(404);
        res.end("not found");
        return;
    }
    res.writeHead(200, {
        "Content-Type": mime[path.extname(filePath)] || "application/octet-stream",
        "Cache-Control": "no-store"
    });
    fs.createReadStream(filePath).pipe(res);
});

await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const base = `http://127.0.0.1:${server.address().port}/silent-sink-experiment.html`;

const chromePath = [
    process.env.CHROME_PATH,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
].find((p) => p && fs.existsSync(p));

const browser = await chromium.launch({
    executablePath: chromePath,
    headless: true,
    args: ["--autoplay-policy=no-user-gesture-required"]
});

const page = await browser.newPage();
const errors = [];
page.on("pageerror", (err) => errors.push(String(err)));

await page.goto(base, { waitUntil: "networkidle" });

const durationSec = 10;
const result = await page.evaluate(async (sec) => {
    const { runSilentSinkTrial } = await import("./silent-sink-experiment.js");
    const trialA = await runSilentSinkTrial("worklet-only", sec);
    const trialB = await runSilentSinkTrial("silent-sink", sec);
    const ratio = trialB.workletCalls / Math.max(1, trialA.workletCalls);
    const doublingConfirmed = ratio >= 1.85;
    return { trialA, trialB, ratio, doublingConfirmed, durationSec: sec };
}, durationSec);

await browser.close();
server.close();

const payload = {
    ...result,
    ranAt: new Date().toISOString(),
    pageErrors: errors,
    expectedAt48kHz10s: {
        sourceSamples: 480000,
        workletCalls128: 3750
    },
    keepAliveRestored: null
};

fs.writeFileSync(resultsPath, JSON.stringify(payload, null, 2));

function row(label, a, b) {
    return `| ${label} | ${a} | ${b} |`;
}

console.log("\n## Silent Sink Experiment Results\n");
console.log("| Metric | A: worklet-only | B: silent-sink |");
console.log("| --- | ---: | ---: |");
console.log(row("sampleRate", result.trialA.sampleRate, result.trialB.sampleRate));
console.log(row("sourceSamples (approx)", result.trialA.sourceSamples, result.trialB.sourceSamples));
console.log(row("workletCalls", result.trialA.workletCalls, result.trialB.workletCalls));
console.log(row("inputSamples (worklet)", result.trialA.inputSamples, result.trialB.inputSamples));
console.log(row("frames960", result.trialA.frames960, result.trialB.frames960));
console.log(row("quantumSize", result.trialA.quantumSize, result.trialB.quantumSize));
console.log(`\n**Ratio B/A:** ${result.ratio.toFixed(3)}`);
console.log(`**Doubling confirmed:** ${result.doublingConfirmed ? "YES" : "NO"}`);
if (errors.length) {
    console.log(`\nPage errors: ${errors.join("; ")}`);
}
console.log(`\nResults written to ${resultsPath}`);
