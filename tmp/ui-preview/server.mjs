import http from "node:http";
import fs from "node:fs";
import path from "node:path";

const web = path.resolve("c:/Users/lowry/Coding/SimpleVoice-Geyser/core/web");
const preview = path.resolve("c:/Users/lowry/Coding/SimpleVoice-Geyser/tmp/ui-preview");
const types = {
    ".html": "text/html",
    ".css": "text/css",
    ".js": "text/javascript",
    ".mjs": "text/javascript",
    ".png": "image/png",
    ".svg": "image/svg+xml",
    ".ico": "image/x-icon"
};

http.createServer((req, res) => {
    let u = decodeURIComponent((req.url || "/").split("?")[0]);
    let root = web;
    let rel = u.replace(/^\//, "");
    if (u === "/" || u === "/index.html") {
        rel = "index.html";
    } else if (u.startsWith("/preview")) {
        root = preview;
        rel = u.replace(/^\/preview\/?/, "");
        if (!rel) rel = "index.html";
    }
    const f = path.normalize(path.join(root, rel));
    if (!f.startsWith(root) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) {
        res.writeHead(404);
        return res.end("not found " + u);
    }
    res.writeHead(200, {
        "Content-Type": types[path.extname(f)] || "application/octet-stream",
        "Cache-Control": "no-store"
    });
    fs.createReadStream(f).pipe(res);
}).listen(8765, () => console.log("serving http://127.0.0.1:8765"));
