/**
 * Appearance v3 (accent + border) tests.
 * Run: node --test core/web/js/appearance.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
    APPEARANCE_STORAGE_KEY,
    APPEARANCE_STORAGE_KEY_V1,
    APPEARANCE_STORAGE_KEY_V2,
    APPEARANCE_VERSION,
    ACCENT_THEMES,
    BORDER_STYLES,
    validateAccent,
    validateBorder,
    loadAppearance,
    saveAppearance,
    clearAppearance,
    AppearanceController
} from "./appearance.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "css", "styles.css"), "utf8");

class MemoryStorage {
    constructor() {
        this.map = new Map();
    }
    getItem(k) {
        return this.map.has(k) ? this.map.get(k) : null;
    }
    setItem(k, v) {
        this.map.set(k, String(v));
    }
    removeItem(k) {
        this.map.delete(k);
    }
}

/** Minimal fake DOM element supporting the bits AppearanceController needs. */
function makeFakeGroup(buttons) {
    const listeners = {};
    return {
        _buttons: buttons,
        addEventListener(type, fn) {
            listeners[type] = listeners[type] || [];
            listeners[type].push(fn);
        },
        dispatch(type, event) {
            for (const fn of listeners[type] || []) fn(event);
        },
        querySelectorAll(sel) {
            const attr = sel.replace(/[[\]]/g, "");
            return buttons.filter((b) => attr in b.attrs);
        }
    };
}

function makeFakeButton(attrs) {
    const classes = new Set();
    return {
        attrs,
        classList: {
            add: (...c) => c.forEach((x) => classes.add(x)),
            remove: (...c) => c.forEach((x) => classes.delete(x)),
            toggle(c, force) {
                if (force) classes.add(c);
                else classes.delete(c);
            },
            contains: (c) => classes.has(c)
        },
        getAttribute(k) {
            return k in attrs ? attrs[k] : null;
        },
        setAttribute(k, v) {
            attrs[k] = String(v);
        },
        closest(sel) {
            const attr = sel.replace(/[[\]]/g, "");
            return attr in attrs ? this : null;
        }
    };
}

test("accent themes and border styles are the expected sets", () => {
    for (const accent of ["default", "cyan", "blue", "purple", "green", "red", "gold"]) {
        assert.ok(ACCENT_THEMES.includes(accent));
    }
    for (const border of ["default", "glass", "subtle", "glow", "high-contrast"]) {
        assert.ok(BORDER_STYLES.includes(border));
    }
});

test("invalid accent/border rejected", () => {
    assert.equal(validateAccent("neon"), null);
    assert.equal(validateAccent(""), null);
    assert.equal(validateBorder("chunky"), null);
    assert.equal(validateBorder(""), null);
});

test("appearance persists under versioned v3 key", () => {
    const prev = globalThis.localStorage;
    globalThis.localStorage = new MemoryStorage();
    try {
        saveAppearance("cyan", "glass");
        const loaded = loadAppearance();
        assert.equal(loaded.accent, "cyan");
        assert.equal(loaded.border, "glass");
        assert.ok(localStorage.getItem(APPEARANCE_STORAGE_KEY));
        assert.equal(APPEARANCE_STORAGE_KEY, "svg.dashboard.appearance.v3");
        assert.equal(APPEARANCE_VERSION, 3);
        clearAppearance();
        const cleared = loadAppearance();
        assert.equal(cleared.accent, "default");
        assert.equal(cleared.border, "default");
    } finally {
        globalThis.localStorage = prev;
    }
});

test("v2 {accent, border} migrates to v3 and removes obsolete keys", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem(APPEARANCE_STORAGE_KEY_V2, JSON.stringify({ version: 2, accent: "purple", border: "glow" }));
        const loaded = loadAppearance();
        assert.equal(loaded.accent, "purple");
        assert.equal(loaded.border, "glow");
        assert.ok(store.getItem(APPEARANCE_STORAGE_KEY));
        assert.equal(store.getItem(APPEARANCE_STORAGE_KEY_V2), null);
    } finally {
        globalThis.localStorage = prev;
    }
});

test("v3 record without version field is accepted", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem(APPEARANCE_STORAGE_KEY, JSON.stringify({ accent: "gold", border: "subtle" }));
        const loaded = loadAppearance();
        assert.equal(loaded.accent, "gold");
        assert.equal(loaded.border, "subtle");
    } finally {
        globalThis.localStorage = prev;
    }
});

test("v1 {version:1, theme} migrates to v3 {accent, border}", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem(APPEARANCE_STORAGE_KEY_V1, JSON.stringify({ version: 1, theme: "cyan" }));
        const loaded = loadAppearance();
        assert.equal(loaded.accent, "cyan");
        assert.equal(loaded.border, "default");
        assert.ok(store.getItem(APPEARANCE_STORAGE_KEY));
        assert.equal(store.getItem(APPEARANCE_STORAGE_KEY_V1), null);
    } finally {
        globalThis.localStorage = prev;
    }
});

test("v1 theme:'glass' migrates to default accent + glass border", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem(APPEARANCE_STORAGE_KEY_V1, JSON.stringify({ version: 1, theme: "glass" }));
        const loaded = loadAppearance();
        assert.equal(loaded.accent, "default");
        assert.equal(loaded.border, "glass");
    } finally {
        globalThis.localStorage = prev;
    }
});

test("controller applies data-accent-theme and data-border-style to dashboard root", () => {
    const prev = globalThis.localStorage;
    globalThis.localStorage = new MemoryStorage();
    try {
        const dashboardRoot = { dataset: {} };
        const accentButtons = ["default", "cyan", "blue", "purple", "green", "red", "gold"].map((a) =>
            makeFakeButton({ "data-accent": a })
        );
        const borderButtons = ["default", "glass", "subtle", "glow", "high-contrast"].map((b) =>
            makeFakeButton({ "data-border": b })
        );
        const accentSwatchGroupEl = makeFakeGroup(accentButtons);
        const borderSwatchGroupEl = makeFakeGroup(borderButtons);
        const accentSelectEl = {
            value: "default",
            addEventListener(type, fn) {
                this._onChange = type === "change" ? fn : this._onChange;
            },
            dispatch() {
                this._onChange?.();
            }
        };
        const borderSelectEl = {
            value: "default",
            addEventListener(type, fn) {
                this._onChange = type === "change" ? fn : this._onChange;
            },
            dispatch() {
                this._onChange?.();
            }
        };
        let resetListener = null;
        const resetBtn = { addEventListener: (type, fn) => { if (type === "click") resetListener = fn; } };

        const controller = new AppearanceController({
            dashboardRoot,
            accentSelectEl,
            borderSelectEl,
            accentSwatchGroupEl,
            borderSwatchGroupEl,
            resetBtn
        });
        controller.init();

        assert.equal(dashboardRoot.dataset.accentTheme, "default");
        assert.equal(dashboardRoot.dataset.borderStyle, "default");

        accentSwatchGroupEl.dispatch("click", { target: accentButtons[1] }); // cyan
        assert.equal(dashboardRoot.dataset.accentTheme, "cyan");
        assert.ok(accentButtons[1].classList.contains("is-active"));
        assert.equal(accentButtons[0].classList.contains("is-active"), false);
        assert.equal(accentSelectEl.value, "cyan");

        borderSelectEl.value = "glass";
        borderSelectEl.dispatch();
        assert.equal(dashboardRoot.dataset.borderStyle, "glass");
        assert.ok(borderButtons[1].classList.contains("is-active"));

        resetListener({ preventDefault() {} });
        assert.equal(dashboardRoot.dataset.accentTheme, "default");
        assert.equal(dashboardRoot.dataset.borderStyle, "default");
    } finally {
        globalThis.localStorage = prev;
    }
});

test("login HTML is not appearance-scoped", () => {
    const loginStart = html.indexOf('id="login-view"');
    const loginEnd = html.indexOf('id="dashboard-view"');
    const login = html.slice(loginStart, loginEnd);
    assert.doesNotMatch(login, /data-accent-theme/);
    assert.doesNotMatch(login, /data-border-style/);
    assert.doesNotMatch(login, /dash\.appearance/);
});

test("dashboard has v2 appearance controls (Theme + Border dropdowns with swatch previews) and reset", () => {
    assert.match(html, /data-svg="dash\.appearance-accent"/);
    assert.match(html, /data-svg="dash\.appearance-border"/);
    assert.match(html, /data-svg="dash\.reset-appearance"/);
    assert.match(html, /appearance-tile/);
    assert.match(html, /id="dash-appearance-accent"/);
    assert.match(html, /id="dash-appearance-border"/);
    assert.match(html, /<option value="cyan">Cyan<\/option>/);
    assert.match(html, /<option value="glass">Glass<\/option>/);
    assert.match(css, /data-border-style="glass"/);
    assert.match(css, /data-accent-theme="cyan"/);
    assert.match(css, /--panel-border-color/);
});

test("every border style changes borderTopColor and/or boxShadow and/or backdropFilter", () => {
    for (const style of BORDER_STYLES) {
        const re = new RegExp(
            `#dashboard-view\\[data-border-style="${style}"\\]\\s*\\{[^}]*(--panel-border-color|--panel-border-shadow|--panel-backdrop-filter|backdrop-filter)`
        );
        assert.match(css, re, `border style "${style}" should affect border-color, box-shadow, or backdrop-filter`);
    }
});

test("every accent theme changes --accent", () => {
    for (const accent of ACCENT_THEMES) {
        const re = new RegExp(`#dashboard-view\\[data-accent-theme="${accent}"\\]\\s*\\{[^}]*--accent:`);
        assert.match(css, re, `accent theme "${accent}" should set --accent`);
    }
});

test("dashboard spacing tokens are defined and applied", () => {
    assert.match(css, /#dashboard-view\s*\{/);
    assert.match(css, /--panel-padding:\s*1\.25rem/);
    assert.match(css, /--panel-gap:\s*1rem/);
    assert.match(css, /--field-gap:\s*0\.625rem/);
    assert.match(css, /--section-gap:\s*1\.25rem/);
    assert.match(css, /--control-height:\s*2\.75rem/);
    assert.match(css, /--dash-panel-padding/);
    assert.match(css, /--dash-panel-gap/);
    assert.match(css, /--dash-field-gap/);
    assert.match(css, /--dash-section-gap/);
    assert.match(css, /--dash-control-height/);
    assert.match(css, /\.panel\s*\{[\s\S]*?padding:\s*var\(--dash-panel-padding/);
    assert.match(css, /\.dashboard-grid\s*\{[\s\S]*?gap:\s*var\(--dash-panel-gap/);
    assert.match(css, /\.control-group\s*\{[\s\S]*?gap:\s*var\(--dash-field-gap/);
    assert.match(css, /\.voice-status-row\s*\{[\s\S]*?gap:\s*var\(--dash-field-gap/);
    assert.match(css, /\.group-card,\s*\.group-row\s*\{[\s\S]*?padding:\s*var\(--dash-panel-padding/);
    assert.match(css, /\.panel-header\s*\{[\s\S]*?justify-content:\s*space-between/);
    assert.doesNotMatch(css, /\}\s*align-items:\s*center;\s*justify-content:\s*space-between/);
});

test("appearance tile is a distinct dash-panel using shared spacing tokens", () => {
    assert.match(html, /appearance-tile panel dash-panel/);
    assert.match(css, /\.appearance-tile\s*\{[^}]*padding:\s*var\(--dash-section-gap/);
});

test("appearance is a required panel outside Voice Controls, between Chat and Groups", () => {
    const voiceStart = html.indexOf('data-panel="voice"');
    const voiceEnd = html.indexOf('data-panel="chat"');
    const voice = html.slice(voiceStart, voiceEnd);
    assert.doesNotMatch(voice, /dash\.appearance/);
    assert.doesNotMatch(voice, /presets-inline|color-editor|preset-field/);

    const appearanceStart = html.indexOf('data-panel="appearance"');
    const chatStart = html.indexOf('data-panel="chat"');
    const groupsIdx = html.indexOf('data-panel="groups"');
    assert.ok(appearanceStart > chatStart);
    assert.ok(appearanceStart < groupsIdx, "Appearance must come before Groups");
    assert.match(html.slice(appearanceStart, groupsIdx), /data-svg="dash\.appearance-accent"/);
});

test("audio settings footer and appearance footer share the same secondary button style", () => {
    assert.match(css, /\.audio-settings__footer\s*\{[^}]*border-top:/);
    assert.match(css, /\.audio-settings__footer\s*\{[^}]*justify-content:\s*flex-end/);
    assert.match(html, /class="btn btn-secondary" data-svg="audio\.reset-settings"/);
    assert.match(html, /class="btn btn-secondary" data-svg="dash\.reset-appearance"/);
});
