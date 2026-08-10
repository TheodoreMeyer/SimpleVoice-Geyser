/**
 * Clipboard module tests.
 * Run: node --test core/web/js/clipboard.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import {
    PSWD_COMMAND,
    copyText,
    fallbackCopyText,
    copyTextFallback,
    bindCopyPswdButton
} from "./clipboard.js";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const uiJs = fs.readFileSync(path.join(root, "js", "ui.js"), "utf8");

function withNavigatorClipboard(clipboard, fn, { secure = true } = {}) {
    const navDescriptor = Object.getOwnPropertyDescriptor(globalThis, "navigator");
    const winDescriptor = Object.getOwnPropertyDescriptor(globalThis, "window");
    Object.defineProperty(globalThis, "navigator", {
        configurable: true,
        enumerable: true,
        value: { clipboard },
        writable: true
    });
    Object.defineProperty(globalThis, "window", {
        configurable: true,
        enumerable: true,
        value: { isSecureContext: secure },
        writable: true
    });
    return Promise.resolve()
        .then(fn)
        .finally(() => {
            if (navDescriptor) {
                Object.defineProperty(globalThis, "navigator", navDescriptor);
            }
            if (winDescriptor) {
                Object.defineProperty(globalThis, "window", winDescriptor);
            } else {
                delete globalThis.window;
            }
        });
}

function mockDocumentForFallback({ execOk = true } = {}) {
    const removed = [];
    const originalDoc = globalThis.document;
    globalThis.document = {
        body: {
            appendChild(node) {
                this._node = node;
            }
        },
        execCommand(cmd) {
            assert.equal(cmd, "copy");
            return execOk;
        },
        createElement(tag) {
            assert.equal(tag, "textarea");
            return {
                value: "",
                style: {},
                setAttribute() {},
                focus() {},
                select() {},
                setSelectionRange() {},
                remove() {
                    removed.push(true);
                }
            };
        }
    };
    return {
        removed,
        restore() {
            globalThis.document = originalDoc;
        }
    };
}

test("button markup uses type=button and stable id", () => {
    assert.match(html, /id="copy-svg-command"/);
    assert.match(html, /id="copy-svg-command"[^>]*type="button"|type="button"[^>]*id="copy-svg-command"/s);
    assert.match(html, /data-svg="form\.copy-pswd"/);
});

test("entrypoint binds clipboard via handleCopyCommand delegation", () => {
    const module = html.match(/<script type="module">([\s\S]*?)<\/script>/)?.[1] || "";
    assert.match(module, /handleCopyCommand/);
    assert.match(module, /from\s+['"]\.\/js\/clipboard\.js['"]/);
    assert.match(module, /closest\(['"]#copy-svg-command['"]\)/);
    assert.doesNotMatch(uiJs, /bindCopyPswdButton\s*\(/);
});

test("handleCopyCommand copies exact command and updates label", async () => {
    const { handleCopyCommand, SVG_PASSWORD_COMMAND } = await import("./clipboard.js");
    assert.equal(SVG_PASSWORD_COMMAND, "/svg pswd ");
    const button = { textContent: "Copy command", dataset: {}, focus() {} };
    const statusEl = { textContent: "", dataset: {} };
    await withNavigatorClipboard({
        async writeText(text) {
            assert.equal(text, "/svg pswd ");
        }
    }, async () => {
        const ok = await handleCopyCommand(button, { statusEl, resetMs: 20 });
        assert.equal(ok, true);
        assert.equal(button.textContent, "Copied!");
        await new Promise((r) => setTimeout(r, 30));
        assert.equal(button.textContent, "Copy command");
    });
});

test("copied string is exactly '/svg pswd '", () => {
    assert.equal(PSWD_COMMAND, "/svg pswd ");
    assert.equal(PSWD_COMMAND.endsWith(" "), true);
    assert.equal(PSWD_COMMAND.includes("<your password>"), false);
});

test("secure Clipboard API success", async () => {
    const writes = [];
    await withNavigatorClipboard({
        async writeText(text) {
            writes.push(text);
        }
    }, async () => {
        await copyText(PSWD_COMMAND);
        assert.deepEqual(writes, ["/svg pswd "]);
    });
});

test("insecure context skips Clipboard API and uses fallback", async () => {
    const mock = mockDocumentForFallback({ execOk: true });
    try {
        await withNavigatorClipboard({
            async writeText() {
                assert.fail("clipboard.writeText must not run outside secure context");
            }
        }, async () => {
            await copyText(PSWD_COMMAND);
            assert.equal(mock.removed.length, 1);
        }, { secure: false });
    } finally {
        mock.restore();
    }
});

test("Clipboard API rejection uses fallback", async () => {
    const mock = mockDocumentForFallback({ execOk: true });
    try {
        await withNavigatorClipboard({
            async writeText() {
                throw new Error("denied");
            }
        }, async () => {
            await copyText(PSWD_COMMAND);
            assert.equal(mock.removed.length, 1);
        });
    } finally {
        mock.restore();
    }
});

test("missing Clipboard API uses fallback", async () => {
    const mock = mockDocumentForFallback({ execOk: true });
    try {
        await withNavigatorClipboard(undefined, async () => {
            await copyText(PSWD_COMMAND);
            assert.equal(mock.removed.length, 1);
        });
    } finally {
        mock.restore();
    }
});

test("fallback restores focus and throws on failure", () => {
    const mock = mockDocumentForFallback({ execOk: true });
    let focused = false;
    const button = { focus() { focused = true; } };
    try {
        fallbackCopyText(PSWD_COMMAND, button);
        assert.equal(focused, true);
        assert.equal(copyTextFallback(PSWD_COMMAND, button), true);
    } finally {
        mock.restore();
    }

    const failMock = mockDocumentForFallback({ execOk: false });
    try {
        assert.throws(() => fallbackCopyText(PSWD_COMMAND, button));
        assert.equal(copyTextFallback(PSWD_COMMAND, button), false);
    } finally {
        failMock.restore();
    }
});

test("bindCopyPswdButton success feedback and single listener", async () => {
    const listeners = [];
    const button = {
        textContent: "Copy",
        dataset: {},
        getAttribute(name) {
            return name === "type" ? "button" : null;
        },
        setAttribute() {},
        addEventListener(type, fn) {
            listeners.push({ type, fn });
        },
        removeEventListener(type, fn) {
            const idx = listeners.findIndex((l) => l.type === type && l.fn === fn);
            if (idx >= 0) listeners.splice(idx, 1);
        }
    };
    const statusEl = {
        textContent: "",
        hidden: true,
        dataset: { copyOnly: "1" }
    };

    await withNavigatorClipboard({
        async writeText(text) {
            assert.equal(text, "/svg pswd ");
        }
    }, async () => {
        const dispose = bindCopyPswdButton({ button, statusEl, resetMs: 10 });
        assert.equal(button.dataset.copyBound, "1");
        assert.equal(button.textContent, "Copy command");
        assert.equal(listeners.length, 1);

        // Second bind replaces the first listener (no stacking).
        const dispose2 = bindCopyPswdButton({ button, statusEl, resetMs: 10 });
        assert.equal(listeners.length, 1);

        await listeners[0].fn({ preventDefault() {}, stopPropagation() {} });
        assert.equal(button.textContent, "Copied!");
        assert.equal(statusEl.textContent, "Copied!");
        dispose2();
        dispose();
    });
});

test("failure produces instructions without unhandled rejection", async () => {
    const mock = mockDocumentForFallback({ execOk: false });
    const button = {
        textContent: "Copy command",
        dataset: {},
        getAttribute() { return "button"; },
        setAttribute() {},
        addEventListener(type, fn) { this._fn = fn; },
        removeEventListener() {},
        focus() {}
    };
    const statusEl = { textContent: "", hidden: true, dataset: { copyOnly: "1" } };
    const rejections = [];
    const onUnhandled = (reason) => rejections.push(reason);
    process.on("unhandledRejection", onUnhandled);

    try {
        await withNavigatorClipboard({
            async writeText() {
                throw new Error("denied");
            }
        }, async () => {
            bindCopyPswdButton({ button, statusEl, resetMs: 10 });
            await button._fn({ preventDefault() {}, stopPropagation() {} });
            // Allow microtasks to settle.
            await new Promise((r) => setTimeout(r, 20));
            assert.match(statusEl.textContent, /Couldn’t copy automatically/);
            assert.equal(rejections.length, 0);
        });
    } finally {
        process.off("unhandledRejection", onUnhandled);
        mock.restore();
    }
});

test("click does not clear credentials or submit login", async () => {
    let submitted = false;
    const form = {
        addEventListener() {},
        username: "Steve",
        password: "secretpass"
    };
    const button = {
        textContent: "Copy command",
        dataset: {},
        getAttribute() { return "button"; },
        setAttribute() {},
        addEventListener(type, fn) { this._fn = fn; },
        removeEventListener() {},
        focus() {}
    };
    const statusEl = { textContent: "", hidden: true, dataset: { copyOnly: "1" } };

    await withNavigatorClipboard({
        async writeText() {}
    }, async () => {
        bindCopyPswdButton({ button, statusEl, resetMs: 10 });
        await button._fn({
            preventDefault() { submitted = false; },
            stopPropagation() {}
        });
        assert.equal(form.username, "Steve");
        assert.equal(form.password, "secretpass");
        assert.equal(submitted, false);
    });
});
