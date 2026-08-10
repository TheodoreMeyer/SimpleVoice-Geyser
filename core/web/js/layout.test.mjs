/**
 * Layout / markup invariants for the web dashboard.
 * Run: node --test core/web/js/layout.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "css", "styles.css"), "utf8");
const indexModule = html.match(/<script type="module">([\s\S]*?)<\/script>/)?.[1] || "";

function countIds(id) {
    const re = new RegExp(`id="${id}"`, "g");
    return (html.match(re) || []).length;
}

test("1. initial login visible in markup", () => {
    assert.doesNotMatch(html, /id="login-view"[^>]*\bhidden\b/);
    assert.match(html, /id="login-view"/);
});

test("2. dashboard initially hidden", () => {
    assert.match(html, /id="dashboard-view"[^>]*\bhidden\b/);
});

test("3/4. closed dialogs and no static open", () => {
    assert.doesNotMatch(html, /<dialog[^>]*\sopen\b/);
    assert.match(html, /id="create-group-dialog"/);
    assert.match(html, /id="protected-group-dialog"/);
    assert.match(css, /dialog:not\(\[open\]\)\s*\{[^}]*display:\s*none\s*!important/s);
});

test("5. no orphan full-screen overlay rule", () => {
    assert.doesNotMatch(css, /div\.modal:not\(dialog\)\s*\{[^}]*display:\s*flex/s);
    assert.equal((html.match(/class="[^"]*modal[^"]*"/g) || []).length, 0);
});

test("6. [hidden] cannot be overridden", () => {
    assert.match(css, /\[hidden\]\s*\{[^}]*display:\s*none\s*!important/s);
});

test("dialog CSS never forces closed dialogs visible", () => {
    assert.doesNotMatch(css, /^\.app-dialog\s*\{[^}]*display:\s*(flex|grid|block)/m);
    assert.match(css, /\.app-dialog\[open\]\s*\{[^}]*display:\s*block/s);
    assert.match(css, /\.dialog-surface\s*\{[^}]*display:\s*grid/s);
});

test("ptt overlay starts hidden", () => {
    assert.match(html, /id="pttFullscreenOverlay"[^>]*\bhidden\b/);
});

test("11. no nested forms", () => {
    let depth = 0;
    for (const token of html.matchAll(/<\/?form\b[^>]*>/gi)) {
        if (token[0].startsWith("</")) depth -= 1;
        else depth += 1;
        assert.ok(depth <= 1);
        assert.ok(depth >= 0);
    }
    assert.equal(depth, 0);
});

test("12. no duplicate IDs", () => {
    const ids = [...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
    const seen = new Set();
    for (const id of ids) {
        assert.equal(seen.has(id), false, `duplicate id=${id}`);
        seen.add(id);
    }
});

test("exactly one of each primary shell element", () => {
    assert.equal(countIds("login-view"), 1);
    assert.equal(countIds("dashboard-view"), 1);
    assert.equal(countIds("create-group-dialog"), 1);
    assert.equal(countIds("protected-group-dialog"), 1);
    assert.equal(countIds("app-root"), 1);
    assert.equal((html.match(/<footer class="site-footer"/g) || []).length, 1);
});

test("dialogs live outside login/dashboard forms", () => {
    const loginFormStart = html.indexOf('id="joinForm"');
    const loginFormEnd = html.indexOf("</form>", loginFormStart);
    const createIdx = html.indexOf('id="create-group-dialog"');
    const protectedIdx = html.indexOf('id="protected-group-dialog"');
    assert.ok(createIdx > loginFormEnd);
    assert.ok(protectedIdx > loginFormEnd);
    assert.match(html, /<\/main>\s*<footer class="site-footer"/);
});

test("13/14. copy button type=button and handler imported by entrypoint", () => {
    assert.match(html, /id="copy-svg-command"/);
    assert.match(html, /id="copy-svg-command"[\s\S]{0,160}?type="button"|type="button"[\s\S]{0,160}?id="copy-svg-command"/);
    assert.match(indexModule, /handleCopyCommand/);
    assert.match(indexModule, /from\s+['"]\.\/js\/clipboard\.js['"]/);
    assert.match(indexModule, /closest\(['"]#copy-svg-command['"]\)/);
});

test("login form has gap without nested gray form chrome", () => {
    assert.match(css, /\.login-form\s*\{[^}]*gap:\s*var\(--space-4\)/s);
    assert.doesNotMatch(css, /\.login-form\s*\{[^}]*background:/s);
    assert.match(css, /\.login-card\s*\{[^}]*padding:\s*clamp\(24px/s);
});

test("spacing scale variables are defined", () => {
    assert.match(css, /--space-1:\s*0\.375rem/);
    assert.match(css, /--space-6:\s*2\.5rem/);
});

test("26/27. footer centered with two credit rows", () => {
    assert.equal((html.match(/footer-credits/g) || []).length, 1);
    assert.match(html, /<span>\s*Created by TheodoreMeyer\s*<\/span>/);
    assert.match(html, /<span>\s*Modernized by Kopeka\s*<\/span>/);
    assert.match(css, /\.site-footer[\s\S]*?justify-items:\s*center/);
    assert.match(css, /\.footer-credits[\s\S]*?display:\s*grid/);
});

test("create group type defaults to ISOLATED", () => {
    assert.match(html, /<option value="ISOLATED" selected>Isolated<\/option>/);
    assert.equal(countIds("create-group-name"), 1);
    assert.equal(countIds("create-group-password"), 1);
    assert.equal(countIds("close-protected-group"), 1);
});

test("box-sizing border-box is global", () => {
    assert.match(css, /\*\s*,\s*\*::before\s*,\s*\*::after\s*\{[^}]*box-sizing:\s*border-box/s);
});

test("28. 320px-friendly shell padding clamp present", () => {
    assert.match(css, /\.app-shell[\s\S]*padding:\s*clamp\(20px,\s*4vw,\s*44px\)/s);
});

test("ownership model: app-dialog classes", () => {
    assert.match(html, /class="app-dialog"/);
    assert.match(html, /class="dialog-surface"/);
    assert.match(html, /class="dialog-form"/);
    assert.match(html, /class="dialog-actions"/);
});

test("dashboard hosts voice/chat/appearance/groups in canonical nested layout", () => {
    assert.match(html, /data-panel="voice"/);
    assert.match(html, /data-panel="chat"/);
    assert.match(html, /data-panel="appearance"/);
    assert.match(html, /data-panel="groups"/);
    assert.match(html, /class="dashboard-upper"/);
    assert.match(html, /class="dashboard-right"/);
    assert.match(css, /\.dashboard-upper\s*\{/);
    assert.match(css, /\.dashboard-right\s*\{/);
    assert.match(css, /\.group-list\s*\{[^}]*grid-template-columns:\s*repeat\(2/);
});

test("presets do not style generic form/input/button layout", () => {
    const presets = fs.readFileSync(path.join(root, "css", "presets.css"), "utf8");
    assert.doesNotMatch(presets, /\.preset-\w+\s+form\s*\{/);
    assert.doesNotMatch(presets, /\.preset-\w+\s+input\s*,/);
    assert.doesNotMatch(presets, /\.preset-\w+\s+button\s*\{/);
    assert.doesNotMatch(presets, /#main-vc-container/);
});
