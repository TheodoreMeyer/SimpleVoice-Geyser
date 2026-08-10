/**
 * Dashboard layout controller tests.
 * Run: node --test core/web/js/dashboard-layout.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
    DEFAULT_LAYOUT,
    LAYOUT_STORAGE_KEY,
    LAYOUT_STORAGE_KEY_V1,
    LAYOUT_STORAGE_KEY_V3,
    LAYOUT_VERSION,
    GRID_COLS,
    GRID_ROWS,
    INTERACTIVE_SELECTOR,
    validateLayout,
    placementsOverlap,
    cloneLayout,
    loadLayout,
    saveLayout,
    clearLayout,
    movePanelKeyboard,
    snapToGrid,
    resolvePlacement,
    DashboardLayoutController
} from "./dashboard-layout.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "css", "styles.css"), "utf8");
const layoutJs = fs.readFileSync(path.join(root, "js", "dashboard-layout.js"), "utf8");

/** Minimal localStorage for Node tests. */
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

function makePanel(id) {
    const el = {
        style: {
            props: {},
            setProperty(k, v) { this.props[k] = v; },
            removeProperty(k) { delete this.props[k]; },
            get gridColumn() { return this.props["grid-column"] || ""; },
            set gridColumn(v) { this.props["grid-column"] = v; },
            get gridRow() { return this.props["grid-row"] || ""; },
            set gridRow(v) { this.props["grid-row"] = v; },
            get order() { return this.props.order || ""; },
            set order(v) { this.props.order = v; },
            get transform() { return this.props.transform || ""; },
            set transform(v) { this.props.transform = v; },
            get position() { return this.props.position || ""; },
            set position(v) { this.props.position = v; },
            get left() { return this.props.left || ""; },
            set left(v) { this.props.left = v; },
            get top() { return this.props.top || ""; },
            set top(v) { this.props.top = v; },
            get width() { return this.props.width || ""; },
            set width(v) { this.props.width = v; },
            get height() { return this.props.height || ""; },
            set height(v) { this.props.height = v; },
            get zIndex() { return this.props.zIndex || ""; },
            set zIndex(v) { this.props.zIndex = v; }
        },
        dataset: {},
        classList: {
            set: new Set(),
            add(...c) { c.forEach((x) => this.set.add(x)); },
            remove(...c) { c.forEach((x) => this.set.delete(x)); },
            toggle(c, force) {
                if (force) this.set.add(c);
                else this.set.delete(c);
            },
            contains(c) { return this.set.has(c); }
        },
        attributes: {},
        setAttribute(k, v) { this.attributes[k] = v; },
        removeAttribute(k) { delete this.attributes[k]; },
        getBoundingClientRect: () => ({ left: 10, top: 20, width: 200, height: 100, right: 210, bottom: 120 }),
        setPointerCapture() { this._captured = true; },
        releasePointerCapture() { this._captured = false; },
        querySelector(sel) {
            if (sel === "[data-panel-handle]") {
                return this._handle;
            }
            return null;
        },
        querySelectorAll() { return []; }
    };
    el._handle = {
        addEventListener(type, fn) {
            this._listeners = this._listeners || {};
            this._listeners[type] = this._listeners[type] || [];
            this._listeners[type].push(fn);
        },
        setAttribute() {},
        getAttribute() { return null; },
        hasAttribute() { return false },
        setPointerCapture(id) {
            this._captured = true;
            this._captureId = id;
            el._captured = true;
        },
        releasePointerCapture() {
            this._captured = false;
            el._captured = false;
        },
        contains() { return false; },
        dispatch(type, event) {
            const payload = { ...event, currentTarget: this };
            for (const fn of this._listeners?.[type] || []) fn(payload);
        }
    };
    el.dataset.panelId = id;
    return el;
}

test("login view is not marked draggable", () => {
    const loginStart = html.indexOf('id="login-view"');
    const loginEnd = html.indexOf('id="dashboard-view"');
    const login = html.slice(loginStart, loginEnd);
    assert.doesNotMatch(login, /data-panel-handle/);
    assert.doesNotMatch(login, /data-panel="/);
    assert.doesNotMatch(login, /dash-panel/);
});

test("default layout: voice left, chat+appearance right (aligned bottoms), groups full width", () => {
    assert.equal(DEFAULT_LAYOUT.voice.row, DEFAULT_LAYOUT.chat.row);
    assert.equal(
        DEFAULT_LAYOUT.voice.rowSpan,
        DEFAULT_LAYOUT.chat.rowSpan + DEFAULT_LAYOUT.appearance.rowSpan
    );
    assert.equal(DEFAULT_LAYOUT.appearance.col, DEFAULT_LAYOUT.chat.col);
    assert.equal(
        DEFAULT_LAYOUT.appearance.row,
        DEFAULT_LAYOUT.chat.row + DEFAULT_LAYOUT.chat.rowSpan
    );
    assert.ok(DEFAULT_LAYOUT.voice.colSpan > DEFAULT_LAYOUT.chat.colSpan, "voice is wider than chat (~55–60%)");
    assert.ok(DEFAULT_LAYOUT.voice.colSpan / GRID_COLS >= 0.55, "voice occupies ≥55% of upper width");
    assert.ok(DEFAULT_LAYOUT.chat.colSpan / GRID_COLS <= 0.45, "chat/appearance occupy ≤45% of upper width");

    assert.equal(DEFAULT_LAYOUT.groups.colSpan, GRID_COLS);
    assert.equal(DEFAULT_LAYOUT.groups.row, DEFAULT_LAYOUT.voice.row + DEFAULT_LAYOUT.voice.rowSpan);

    assert.equal(DEFAULT_LAYOUT.voice.rowSpan + DEFAULT_LAYOUT.groups.rowSpan, GRID_ROWS);
});

test("default placements never overlap", () => {
    assert.equal(placementsOverlap(Object.values(DEFAULT_LAYOUT)), false);
});

test("layout storage key is v4", () => {
    assert.equal(LAYOUT_STORAGE_KEY, "svg.dashboard.layout.v4");
    assert.equal(LAYOUT_VERSION, 4);
});

test("validateLayout recovers from corrupted JSON shapes", () => {
    assert.equal(validateLayout(null), null);
    assert.equal(validateLayout({ version: 99, panels: {} }), null);
    assert.equal(validateLayout({ version: LAYOUT_VERSION, panels: { voice: { col: 1 } } }), null);

    const repaired = validateLayout({
        version: LAYOUT_VERSION,
        panels: {
            voice: { col: 1, row: 1, colSpan: 7, rowSpan: 8 },
            chat: { col: 8, row: 1, colSpan: 5, rowSpan: 12 }
        }
    });
    assert.ok(repaired);
    assert.equal(repaired.groups.colSpan, DEFAULT_LAYOUT.groups.colSpan);
    assert.ok(repaired.appearance, "appearance panel must be inserted when missing");
});

test("v2 layout without appearance is rejected (v4 only accepts version 4)", () => {
    const migrated = validateLayout({
        version: 2,
        panels: {
            voice: { col: 1, row: 1, colSpan: 5, rowSpan: 8 },
            chat: { col: 6, row: 1, colSpan: 7, rowSpan: 8 },
            groups: { col: 1, row: 9, colSpan: 12, rowSpan: 4 }
        }
    });
    assert.equal(migrated, null);
});

test("overlapping stored layout falls back to defaults", () => {
    const bad = validateLayout({
        version: LAYOUT_VERSION,
        panels: {
            voice: { col: 1, row: 1, colSpan: 8, rowSpan: 8 },
            chat: { col: 2, row: 2, colSpan: 8, rowSpan: 8 },
            groups: { col: 1, row: 1, colSpan: 4, rowSpan: 4 }
        }
    });
    assert.deepEqual(bad.voice, DEFAULT_LAYOUT.voice);
});

test("save/load round-trip and reset clears only layout key", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem("preset", "dark");
        saveLayout(DEFAULT_LAYOUT);
        assert.ok(store.getItem(LAYOUT_STORAGE_KEY));
        const loaded = loadLayout();
        assert.equal(loaded.voice.colSpan, DEFAULT_LAYOUT.voice.colSpan);
        clearLayout();
        assert.equal(store.getItem(LAYOUT_STORAGE_KEY), null);
        assert.equal(store.getItem("preset"), "dark");
        assert.deepEqual(loadLayout().chat, DEFAULT_LAYOUT.chat);
    } finally {
        globalThis.localStorage = prev;
    }
});

test("obsolete v1/v2/v3 layouts are archived and default layout is used on first v4 load", () => {
    const prev = globalThis.localStorage;
    const store = new MemoryStorage();
    globalThis.localStorage = store;
    try {
        store.setItem(
            LAYOUT_STORAGE_KEY_V3,
            JSON.stringify({
                version: 3,
                panels: {
                    voice: { col: 1, row: 1, colSpan: 7, rowSpan: 8 },
                    chat: { col: 8, row: 1, colSpan: 5, rowSpan: 12 },
                    groups: { col: 1, row: 9, colSpan: 7, rowSpan: 4 }
                }
            })
        );
        store.setItem(
            LAYOUT_STORAGE_KEY_V1,
            JSON.stringify({
                version: 1,
                panels: {
                    voice: { col: 1, row: 1, colSpan: 7, rowSpan: 8 },
                    chat: { col: 8, row: 1, colSpan: 5, rowSpan: 12 },
                    groups: { col: 1, row: 9, colSpan: 7, rowSpan: 4 }
                }
            })
        );
        const loaded = loadLayout();
        assert.deepEqual(loaded.voice, DEFAULT_LAYOUT.voice);
        assert.deepEqual(loaded.chat, DEFAULT_LAYOUT.chat);
        assert.deepEqual(loaded.appearance, DEFAULT_LAYOUT.appearance);
        assert.equal(store.getItem(LAYOUT_STORAGE_KEY_V3), null);
        assert.equal(store.getItem(LAYOUT_STORAGE_KEY_V1), null);
        assert.equal(store.getItem(LAYOUT_STORAGE_KEY), null);
    } finally {
        globalThis.localStorage = prev;
    }
});

test("keyboard move left/right stays in bounds without overlap when possible", () => {
    const moved = movePanelKeyboard(cloneLayout(DEFAULT_LAYOUT), "voice", "right");
    if (moved) {
        assert.equal(placementsOverlap(Object.values(moved)), false);
    }
});

test("snapToGrid clamps to valid origin for span", () => {
    const rect = { left: 0, top: 0, width: 1200, height: 1200 };
    const snapped = snapToGrid(rect, 1190, 1190, 5, 4, 16);
    assert.ok(snapped.col >= 1 && snapped.col <= GRID_COLS - 5 + 1);
    assert.ok(snapped.row >= 1 && snapped.row <= GRID_ROWS - 4 + 1);
});

test("resolvePlacement snaps without overlap", () => {
    // Voice + chat occupy the entire upper region, and groups fills the row(s)
    // below in the new layout, so dropping groups onto voice either resolves
    // to a non-overlapping swap or is rejected (null) — never an overlap.
    const next = resolvePlacement(cloneLayout(DEFAULT_LAYOUT), "groups", 1, 1);
    if (next) {
        assert.equal(placementsOverlap(Object.values(next)), false);
    }
    // Dropping back onto its own slot is always a valid, non-overlapping placement.
    const free = resolvePlacement(cloneLayout(DEFAULT_LAYOUT), "groups", 1, 9);
    assert.ok(free);
    assert.equal(placementsOverlap(Object.values(free)), false);
});

test("dashboard markup has four draggable panels including Appearance and reset control", () => {
    assert.match(html, /data-panel="voice"/);
    assert.match(html, /data-panel="chat"/);
    assert.match(html, /data-panel="appearance"/);
    assert.match(html, /data-panel="groups"/);
    assert.equal((html.match(/data-panel-handle/g) || []).length, 4);
    assert.match(html, /data-svg="dash\.reset-layout"/);
    assert.match(html, /data-svg="audio\.level"/);
    assert.match(html, /Voice will not transmit until you join a voice group/);
});

test("groups panel spans the full grid width; Appearance is a required panel below Chat", () => {
    assert.match(html, /data-panel="appearance"/);
    assert.match(html, /id="dash-appearance-accent"/);
    assert.match(html, /id="dash-appearance-border"/);

    const chatStart = html.indexOf('data-panel="chat"');
    const appearanceStart = html.indexOf('data-panel="appearance"');
    const groupsStart = html.indexOf('data-panel="groups"');
    assert.ok(chatStart > 0 && appearanceStart > chatStart && groupsStart > appearanceStart);
    assert.match(css, /\.chat-panel-body\s*\{[^}]*flex:\s*1\s*1\s*auto/);
});

test("desktop first-paint CSS matches v4 default grid (chat rowSpan 5, appearance below)", () => {
    assert.match(css, /\[data-panel="voice"\][\s\S]*?grid-column:\s*1\s*\/\s*span\s*7/);
    assert.match(css, /\[data-panel="chat"\][\s\S]*?grid-column:\s*8\s*\/\s*span\s*5/);
    assert.match(css, /\[data-panel="chat"\][\s\S]*?grid-row:\s*1\s*\/\s*span\s*5/);
    assert.match(css, /\[data-panel="appearance"\][\s\S]*?grid-column:\s*8\s*\/\s*span\s*5/);
    assert.match(css, /\[data-panel="appearance"\][\s\S]*?grid-row:\s*6\s*\/\s*span\s*3/);
    assert.match(css, /\[data-panel="voice"\][\s\S]*?grid-row:\s*1\s*\/\s*span\s*8/);
    assert.match(css, /\[data-panel="groups"\][\s\S]*?grid-row:\s*9\s*\/\s*span\s*4/);
});
test("group cards use a 2-column grid on desktop and 1 column on mobile", () => {
    assert.match(css, /\.group-list\s*\{[^}]*display:\s*grid/);
    assert.match(css, /\.group-list\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/);
    assert.match(css, /@media \(max-width:\s*900px\)[\s\S]*?\.group-list\s*\{[^}]*grid-template-columns:\s*1fr/);
});

test("no visible movement-arrow interface remains", () => {
    assert.doesNotMatch(html, /panel-move-menu/);
    assert.doesNotMatch(html, />↑</);
    assert.doesNotMatch(html, />↓</);
    assert.doesNotMatch(html, />←</);
    assert.doesNotMatch(html, />→</);
    assert.match(html, /visually-hidden panel-a11y-move/);
    assert.match(css, /\.panel-move-menu[\s\S]*display:\s*none\s*!important/);
});

test("mobile CSS stacks panels and hides drag chrome", () => {
    assert.match(css, /@media \(max-width:\s*900px\)/);
    assert.match(css, /\.dashboard-grid\.is-mobile-stack/);
});

test("pointer drag applies translate3d before release", () => {
    assert.match(layoutJs, /translate3d/);
    assert.match(layoutJs, /setPointerCapture/);
    assert.match(layoutJs, /requestAnimationFrame/);
    assert.match(layoutJs, /INTERACTIVE_SELECTOR/);

    const prev = globalThis.localStorage;
    globalThis.localStorage = new MemoryStorage();
    globalThis.window = globalThis;
    globalThis.document = {
        body: { classList: { add() {}, remove() {} } },
        createElement(tag) {
            return {
                tagName: tag,
                style: {},
                className: "",
                hidden: false,
                setAttribute() {},
                remove() {}
            };
        }
    };
    try {
        const gridEl = {
            classList: { toggle() {} },
            appendChild() {},
            insertBefore() {},
            getBoundingClientRect: () => ({ left: 0, top: 0, width: 1200, height: 1200 })
        };
        const panels = {
            voice: makePanel("voice"),
            chat: makePanel("chat"),
            appearance: makePanel("appearance"),
            groups: makePanel("groups")
        };
        const mq = { matches: false, addEventListener() {} };
        globalThis.matchMedia = () => mq;

        const listeners = {};
        const origAdd = globalThis.addEventListener;
        globalThis.addEventListener = (type, fn) => {
            listeners[type] = listeners[type] || [];
            listeners[type].push(fn);
        };
        globalThis.removeEventListener = () => {};
        globalThis.requestAnimationFrame = (fn) => { fn(); return 1; };
        globalThis.cancelAnimationFrame = () => {};

        const controller = new DashboardLayoutController({ gridEl, panels });
        controller.init();

        const handle = panels.voice._handle;
        handle.dispatch("pointerdown", {
            button: 0,
            pointerType: "mouse",
            pointerId: 7,
            clientX: 50,
            clientY: 50,
            target: {
                closest(sel) {
                    if (sel === INTERACTIVE_SELECTOR) return null;
                    return null;
                }
            },
            preventDefault() {}
        });

        assert.equal(panels.voice._captured, true);
        assert.ok(panels.voice.classList.contains("is-dragging"));

        for (const fn of listeners.pointermove || []) {
            fn({
                pointerId: 7,
                clientX: 180,
                clientY: 140,
                preventDefault() {}
            });
        }

        assert.match(panels.voice.style.transform, /translate3d\(/);
        assert.ok(controller.isDragging());

        for (const fn of listeners.pointerup || []) {
            fn({ pointerId: 7, clientX: 180, clientY: 140 });
        }

        assert.equal(controller.isDragging(), false);
        assert.equal(placementsOverlap(Object.values(controller.layout)), false);

        globalThis.addEventListener = origAdd;
    } finally {
        globalThis.localStorage = prev;
    }
});

test("interactive selector blocks drag from controls", () => {
    assert.match(INTERACTIVE_SELECTOR, /button/);
    assert.match(INTERACTIVE_SELECTOR, /input/);
    assert.match(INTERACTIVE_SELECTOR, /\.no-drag/);
    assert.doesNotMatch(INTERACTIVE_SELECTOR, /role=button/);
});

test("panel handle must not self-block drag via role=button", () => {
    assert.doesNotMatch(layoutJs, /handle\.setAttribute\("role", "button"\)/);
    assert.match(layoutJs, /isInteractiveDragTarget/);
    assert.match(layoutJs, /setPointerCapture/);
    assert.match(layoutJs, /translate3d/);
});

test("layout module is imported by the app entrypoint map", () => {
    assert.match(html, /dashboard-layout\.js/);
    assert.match(html, /appearance\.js/);
});
