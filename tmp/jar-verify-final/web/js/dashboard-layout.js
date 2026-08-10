/**
 * Pointer-Events draggable CSS-grid dashboard layout (authenticated view only).
 * Persists semantic grid placement — never raw pixel coordinates.
 * Panels follow the pointer with translate3d while the button is held, then snap.
 */

export const LAYOUT_STORAGE_KEY = "svg.dashboard.layout.v2";
export const LAYOUT_STORAGE_KEY_V1 = "svg.dashboard.layout.v1";
export const LAYOUT_VERSION = 2;
export const GRID_COLS = 12;
export const GRID_ROWS = 12;

/** @typedef {{ id: string, col: number, row: number, colSpan: number, rowSpan: number }} PanelPlacement */

/** @type {Record<string, PanelPlacement>} */
export const DEFAULT_LAYOUT = {
    // Voice largest (~47%), chat second (~42%), groups compact (~19%).
    voice: { id: "voice", col: 1, row: 1, colSpan: 7, rowSpan: 8 },
    chat: { id: "chat", col: 8, row: 1, colSpan: 5, rowSpan: 12 },
    groups: { id: "groups", col: 1, row: 9, colSpan: 7, rowSpan: 4 }
};

const PANEL_IDS = Object.keys(DEFAULT_LAYOUT);

export const INTERACTIVE_SELECTOR =
    "button, a, input, textarea, select, option, [role=button], [contenteditable=true], .no-drag";

/**
 * @param {unknown} value
 * @returns {Record<string, PanelPlacement>|null}
 */
export function validateLayout(value) {
    if (!value || typeof value !== "object") {
        return null;
    }
    const raw = /** @type {any} */ (value);
    const version = Number(raw.version);
    if ((version !== LAYOUT_VERSION && version !== 1) || !raw.panels || typeof raw.panels !== "object") {
        return null;
    }

    /** @type {Record<string, PanelPlacement>} */
    const panels = {};
    for (const id of PANEL_IDS) {
        const p = raw.panels[id];
        if (!p || typeof p !== "object") {
            continue;
        }
        const col = Number(p.col);
        const row = Number(p.row);
        const colSpan = Number(p.colSpan);
        const rowSpan = Number(p.rowSpan);
        if (![col, row, colSpan, rowSpan].every((n) => Number.isInteger(n) && n > 0)) {
            continue;
        }
        if (col + colSpan - 1 > GRID_COLS || row + rowSpan - 1 > GRID_ROWS) {
            continue;
        }
        if (colSpan < 3 || rowSpan < 3) {
            continue;
        }
        panels[id] = { id, col, row, colSpan, rowSpan };
    }

    if (Object.keys(panels).length === 0) {
        return null;
    }

    for (const id of PANEL_IDS) {
        if (!panels[id]) {
            panels[id] = { ...DEFAULT_LAYOUT[id] };
        }
    }

    if (placementsOverlap(Object.values(panels))) {
        return { ...cloneLayout(DEFAULT_LAYOUT) };
    }
    return panels;
}

/**
 * @param {PanelPlacement[]} placements
 * @returns {boolean}
 */
export function placementsOverlap(placements) {
    const cells = new Set();
    for (const p of placements) {
        for (let r = p.row; r < p.row + p.rowSpan; r++) {
            for (let c = p.col; c < p.col + p.colSpan; c++) {
                const key = `${c}:${r}`;
                if (cells.has(key)) {
                    return true;
                }
                cells.add(key);
            }
        }
    }
    return false;
}

/**
 * @param {Record<string, PanelPlacement>} layout
 * @returns {Record<string, PanelPlacement>}
 */
export function cloneLayout(layout) {
    /** @type {Record<string, PanelPlacement>} */
    const out = {};
    for (const id of Object.keys(layout)) {
        out[id] = { ...layout[id] };
    }
    return out;
}

/**
 * @param {string} [storageKey]
 * @returns {Record<string, PanelPlacement>}
 */
export function loadLayout(storageKey = LAYOUT_STORAGE_KEY) {
    try {
        let raw = localStorage.getItem(storageKey);
        if (!raw && storageKey === LAYOUT_STORAGE_KEY) {
            // Migrate v1 → v2 once, then drop the old key.
            raw = localStorage.getItem(LAYOUT_STORAGE_KEY_V1);
            if (raw) {
                const migrated = validateLayout(JSON.parse(raw));
                if (migrated) {
                    saveLayout(migrated, LAYOUT_STORAGE_KEY);
                    localStorage.removeItem(LAYOUT_STORAGE_KEY_V1);
                    return migrated;
                }
                localStorage.removeItem(LAYOUT_STORAGE_KEY_V1);
            }
        }
        if (!raw) {
            return cloneLayout(DEFAULT_LAYOUT);
        }
        const parsed = JSON.parse(raw);
        return validateLayout(parsed) || cloneLayout(DEFAULT_LAYOUT);
    } catch {
        return cloneLayout(DEFAULT_LAYOUT);
    }
}

/**
 * @param {Record<string, PanelPlacement>} layout
 * @param {string} [storageKey]
 */
export function saveLayout(layout, storageKey = LAYOUT_STORAGE_KEY) {
    const valid = validateLayout({ version: LAYOUT_VERSION, panels: layout });
    if (!valid) {
        return;
    }
    localStorage.setItem(
        storageKey,
        JSON.stringify({ version: LAYOUT_VERSION, panels: valid })
    );
}

/**
 * @param {string} [storageKey]
 */
export function clearLayout(storageKey = LAYOUT_STORAGE_KEY) {
    localStorage.removeItem(storageKey);
    if (storageKey === LAYOUT_STORAGE_KEY) {
        localStorage.removeItem(LAYOUT_STORAGE_KEY_V1);
    }
}

/**
 * Snap a pixel point inside the grid to a col/row origin for a given span.
 * @param {DOMRect} gridRect
 * @param {number} clientX
 * @param {number} clientY
 * @param {number} colSpan
 * @param {number} rowSpan
 * @param {number} gap
 * @returns {{ col: number, row: number }}
 */
export function snapToGrid(gridRect, clientX, clientY, colSpan, rowSpan, gap = 16) {
    const innerW = Math.max(1, gridRect.width - gap * (GRID_COLS - 1));
    const innerH = Math.max(1, gridRect.height - gap * (GRID_ROWS - 1));
    const cellW = innerW / GRID_COLS;
    const cellH = innerH / GRID_ROWS;
    const x = clientX - gridRect.left;
    const y = clientY - gridRect.top;
    let col = Math.round(x / (cellW + gap)) + 1;
    let row = Math.round(y / (cellH + gap)) + 1;
    col = Math.max(1, Math.min(GRID_COLS - colSpan + 1, col));
    row = Math.max(1, Math.min(GRID_ROWS - rowSpan + 1, row));
    return { col, row };
}

/**
 * Resolve collisions by swapping with the panel occupying the target cell.
 * @param {Record<string, PanelPlacement>} layout
 * @param {string} panelId
 * @param {number} col
 * @param {number} row
 * @returns {Record<string, PanelPlacement>|null}
 */
export function resolvePlacement(layout, panelId, col, row) {
    const next = cloneLayout(layout);
    const moving = next[panelId];
    if (!moving) {
        return null;
    }
    moving.col = col;
    moving.row = row;

    if (!placementsOverlap(Object.values(next))) {
        return next;
    }

    const targetId = panelAt(layout, col, row, panelId);
    if (!targetId) {
        return null;
    }

    const swap = cloneLayout(layout);
    const a = swap[panelId];
    const b = swap[targetId];
    const aCol = a.col;
    const aRow = a.row;
    a.col = b.col;
    a.row = b.row;
    b.col = aCol;
    b.row = aRow;
    a.col = Math.min(a.col, GRID_COLS - a.colSpan + 1);
    a.row = Math.min(a.row, GRID_ROWS - a.rowSpan + 1);
    b.col = Math.min(b.col, GRID_COLS - b.colSpan + 1);
    b.row = Math.min(b.row, GRID_ROWS - b.rowSpan + 1);
    if (placementsOverlap(Object.values(swap))) {
        return null;
    }
    return swap;
}

/**
 * @param {Record<string, PanelPlacement>} layout
 * @param {number} col
 * @param {number} row
 * @param {string} exceptId
 * @returns {string|null}
 */
export function panelAt(layout, col, row, exceptId) {
    for (const id of PANEL_IDS) {
        if (id === exceptId) continue;
        const p = layout[id];
        if (
            col >= p.col && col < p.col + p.colSpan &&
            row >= p.row && row < p.row + p.rowSpan
        ) {
            return id;
        }
    }
    return null;
}

/**
 * @param {Record<string, PanelPlacement>} layout
 * @param {string} panelId
 * @param {"up"|"down"|"left"|"right"} direction
 * @returns {Record<string, PanelPlacement>|null}
 */
export function movePanelKeyboard(layout, panelId, direction) {
    const panel = layout[panelId];
    if (!panel) {
        return null;
    }
    let col = panel.col;
    let row = panel.row;
    if (direction === "left") col = Math.max(1, panel.col - 1);
    if (direction === "right") col = Math.min(GRID_COLS - panel.colSpan + 1, panel.col + 1);
    if (direction === "up") row = Math.max(1, panel.row - 1);
    if (direction === "down") row = Math.min(GRID_ROWS - panel.rowSpan + 1, panel.row + 1);
    return resolvePlacement(layout, panelId, col, row);
}

/**
 * Controller that repositions existing panel nodes without recreating them.
 */
export class DashboardLayoutController {
    /**
     * @param {object} options
     * @param {HTMLElement} options.gridEl
     * @param {Record<string, HTMLElement>} options.panels
     * @param {HTMLElement|null} [options.resetBtn]
     * @param {HTMLElement|null} [options.liveRegion]
     * @param {string} [options.storageKey]
     * @param {() => void} [options.onLayoutChange]
     */
    constructor(options) {
        this.gridEl = options.gridEl;
        this.panels = options.panels;
        this.resetBtn = options.resetBtn || null;
        this.liveRegion = options.liveRegion || null;
        this.storageKey = options.storageKey || LAYOUT_STORAGE_KEY;
        this.onLayoutChange = options.onLayoutChange || (() => {});
        this.layout = loadLayout(this.storageKey);
        /** @type {null|{
         *   panelId: string,
         *   pointerId: number,
         *   startX: number,
         *   startY: number,
         *   originLeft: number,
         *   originTop: number,
         *   width: number,
         *   height: number,
         *   place: PanelPlacement,
         *   dx: number,
         *   dy: number,
         *   raf: number,
         *   moved: boolean
         * }} */
        this.drag = null;
        this.dropHint = null;
        this.placeholder = null;
        this.bound = false;
        this.mq = window.matchMedia("(max-width: 900px)");
        this._onPointerMove = (event) => this.#onPointerMove(event);
        this._onPointerUp = (event) => this.#onPointerUp(event);
        this._onResize = () => this.#onResize();
    }

    init() {
        if (this.bound) {
            return;
        }
        this.bound = true;
        this.#ensureDropHint();
        this.apply(this.layout, { animate: false, persist: false });

        for (const [id, el] of Object.entries(this.panels)) {
            const handle = el.querySelector("[data-panel-handle]");
            if (handle) {
                handle.addEventListener("pointerdown", (event) => this.#onPointerDown(event, id));
                handle.addEventListener("keydown", (event) => this.#onHandleKeyDown(event, id));
                if (!handle.hasAttribute("tabindex")) {
                    handle.setAttribute("tabindex", "0");
                }
                if (!handle.getAttribute("aria-label")) {
                    handle.setAttribute("aria-label", `Drag to rearrange ${id} panel. Arrow keys also move.`);
                }
                handle.setAttribute("role", "button");
            }
            // Screen-reader-only reorder (never visible arrows).
            el.querySelectorAll("[data-panel-move]").forEach((btn) => {
                btn.addEventListener("click", (event) => {
                    event.preventDefault();
                    const dir = btn.getAttribute("data-panel-move");
                    if (dir === "up" || dir === "down" || dir === "left" || dir === "right") {
                        this.moveKeyboard(id, dir);
                    }
                });
            });
        }

        this.resetBtn?.addEventListener("click", (event) => {
            event.preventDefault();
            this.reset();
            this.#announce("Dashboard layout reset.");
        });

        this.mq.addEventListener?.("change", () => this.apply(this.layout, { animate: false, persist: false }));
        window.addEventListener("pointermove", this._onPointerMove, { passive: false });
        window.addEventListener("pointerup", this._onPointerUp);
        window.addEventListener("pointercancel", this._onPointerUp);
        window.addEventListener("resize", this._onResize);
    }

    destroy() {
        window.removeEventListener("pointermove", this._onPointerMove);
        window.removeEventListener("pointerup", this._onPointerUp);
        window.removeEventListener("pointercancel", this._onPointerUp);
        window.removeEventListener("resize", this._onResize);
        this.#cancelDrag(true);
        this.bound = false;
    }

    /**
     * @param {Record<string, PanelPlacement>} layout
     * @param {{ animate?: boolean, persist?: boolean }} [opts]
     */
    apply(layout, opts = {}) {
        const { animate = true, persist = true } = opts;
        const valid = validateLayout({ version: LAYOUT_VERSION, panels: layout }) || cloneLayout(DEFAULT_LAYOUT);
        this.layout = valid;

        const mobile = this.mq.matches;
        this.gridEl.classList.toggle("is-mobile-stack", mobile);
        this.gridEl.classList.toggle("is-desktop-grid", !mobile);

        for (const id of PANEL_IDS) {
            const el = this.panels[id];
            const place = valid[id];
            if (!el || !place) continue;
            el.dataset.panelId = id;
            el.style.removeProperty("transform");
            el.style.removeProperty("left");
            el.style.removeProperty("top");
            el.style.removeProperty("width");
            el.style.removeProperty("height");
            el.style.removeProperty("position");
            el.classList.remove("is-dragging", "is-drag-preview");
            if (mobile) {
                el.style.removeProperty("grid-column");
                el.style.removeProperty("grid-row");
                const order = id === "voice" ? 1 : id === "chat" ? 2 : 3;
                el.style.order = String(order);
            } else {
                el.style.order = "";
                el.style.gridColumn = `${place.col} / span ${place.colSpan}`;
                el.style.gridRow = `${place.row} / span ${place.rowSpan}`;
            }
            el.classList.toggle("panel-snap", !!animate);
        }

        if (persist && !mobile) {
            saveLayout(valid, this.storageKey);
        }
        this.onLayoutChange();
    }

    reset() {
        clearLayout(this.storageKey);
        this.apply(cloneLayout(DEFAULT_LAYOUT), { animate: true, persist: true });
    }

    /**
     * @param {string} panelId
     * @param {"up"|"down"|"left"|"right"} direction
     */
    moveKeyboard(panelId, direction) {
        if (this.mq.matches) {
            return;
        }
        const next = movePanelKeyboard(this.layout, panelId, direction);
        if (next) {
            this.apply(next);
            this.#announce(`${panelId} panel moved.`);
        }
    }

    /**
     * @returns {boolean}
     */
    isDragging() {
        return !!this.drag;
    }

    #ensureDropHint() {
        if (this.dropHint) return;
        this.dropHint = document.createElement("div");
        this.dropHint.className = "dash-drop-hint";
        this.dropHint.hidden = true;
        this.dropHint.setAttribute("aria-hidden", "true");
        this.gridEl.appendChild(this.dropHint);
    }

    /**
     * @param {PointerEvent} event
     * @param {string} panelId
     */
    #onPointerDown(event, panelId) {
        if (this.mq.matches) return;
        if (event.pointerType === "mouse" && event.button !== 0) return;
        const target = /** @type {HTMLElement} */ (event.target);
        if (target.closest(INTERACTIVE_SELECTOR)) {
            return;
        }
        // Allow text selection inside chat/group body to cancel drag start.
        if (target.closest(".chat-log, .group-list, .group-members, .level-meter, .chat-compose")) {
            return;
        }

        const panel = this.panels[panelId];
        if (!panel || this.drag) return;

        const place = this.layout[panelId];
        if (!place) return;

        event.preventDefault();
        panel.setPointerCapture?.(event.pointerId);

        const rect = panel.getBoundingClientRect();
        this.#ensurePlaceholder(panel, place);

        this.drag = {
            panelId,
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            originLeft: rect.left,
            originTop: rect.top,
            width: rect.width,
            height: rect.height,
            place: { ...place },
            dx: 0,
            dy: 0,
            raf: 0,
            moved: false
        };

        panel.classList.add("is-dragging", "is-drag-preview");
        panel.setAttribute("aria-grabbed", "true");
        document.body.classList.add("is-panel-dragging");
        this.dropHint.hidden = false;
        this.#updateHint(place);
        this.#applyDragTransform(0, 0);
    }

    /**
     * @param {PointerEvent} event
     */
    #onPointerMove(event) {
        if (!this.drag || event.pointerId !== this.drag.pointerId) return;
        event.preventDefault();

        this.drag.dx = event.clientX - this.drag.startX;
        this.drag.dy = event.clientY - this.drag.startY;
        if (Math.abs(this.drag.dx) > 2 || Math.abs(this.drag.dy) > 2) {
            this.drag.moved = true;
        }

        if (!this.drag.raf) {
            this.drag.raf = requestAnimationFrame(() => {
                if (!this.drag) return;
                this.drag.raf = 0;
                this.#applyDragTransform(this.drag.dx, this.drag.dy);

                const rect = this.gridEl.getBoundingClientRect();
                const cx = this.drag.originLeft + this.drag.dx + this.drag.width / 2;
                const cy = this.drag.originTop + this.drag.dy + this.drag.height / 2;
                const snapped = snapToGrid(
                    rect,
                    cx,
                    cy,
                    this.drag.place.colSpan,
                    this.drag.place.rowSpan
                );
                this.#updateHint({
                    ...this.drag.place,
                    col: snapped.col,
                    row: snapped.row
                });
            });
        }
    }

    /**
     * @param {PointerEvent} event
     */
    #onPointerUp(event) {
        if (!this.drag || event.pointerId !== this.drag.pointerId) return;

        const panelId = this.drag.panelId;
        const panel = this.panels[panelId];
        const place = this.drag.place;
        const dx = this.drag.dx;
        const dy = this.drag.dy;
        const originLeft = this.drag.originLeft;
        const originTop = this.drag.originTop;
        const width = this.drag.width;
        const height = this.drag.height;

        if (this.drag.raf) {
            cancelAnimationFrame(this.drag.raf);
        }

        try {
            panel?.releasePointerCapture?.(event.pointerId);
        } catch {
            // ignore
        }

        this.drag = null;
        this.dropHint.hidden = true;
        document.body.classList.remove("is-panel-dragging");
        this.#removePlaceholder();

        if (panel) {
            panel.classList.remove("is-dragging", "is-drag-preview");
            panel.removeAttribute("aria-grabbed");
            panel.style.removeProperty("transform");
            panel.style.removeProperty("left");
            panel.style.removeProperty("top");
            panel.style.removeProperty("width");
            panel.style.removeProperty("height");
            panel.style.removeProperty("position");
            panel.style.removeProperty("z-index");
        }

        const rect = this.gridEl.getBoundingClientRect();
        const cx = originLeft + dx + width / 2;
        const cy = originTop + dy + height / 2;
        const snapped = snapToGrid(rect, cx, cy, place.colSpan, place.rowSpan);
        const next = resolvePlacement(this.layout, panelId, snapped.col, snapped.row);

        if (next) {
            this.apply(next);
            this.#announce(`${panelId} panel placed.`);
        } else {
            this.apply(this.layout, { animate: true, persist: false });
            this.#announce(`${panelId} panel returned to previous position.`);
        }
    }

    #onResize() {
        if (this.drag) {
            this.#cancelDrag(false);
        }
        this.apply(this.layout, { animate: false, persist: false });
    }

    /**
     * @param {boolean} restoreLayout
     */
    #cancelDrag(restoreLayout) {
        if (!this.drag) return;
        const panel = this.panels[this.drag.panelId];
        if (this.drag.raf) {
            cancelAnimationFrame(this.drag.raf);
        }
        try {
            panel?.releasePointerCapture?.(this.drag.pointerId);
        } catch {
            // ignore
        }
        this.drag = null;
        this.dropHint.hidden = true;
        document.body.classList.remove("is-panel-dragging");
        this.#removePlaceholder();
        if (panel) {
            panel.classList.remove("is-dragging", "is-drag-preview");
            panel.removeAttribute("aria-grabbed");
            panel.style.removeProperty("transform");
            panel.style.removeProperty("left");
            panel.style.removeProperty("top");
            panel.style.removeProperty("width");
            panel.style.removeProperty("height");
            panel.style.removeProperty("position");
            panel.style.removeProperty("z-index");
        }
        if (restoreLayout) {
            this.apply(this.layout, { animate: false, persist: false });
        }
    }

    /**
     * @param {number} dx
     * @param {number} dy
     */
    #applyDragTransform(dx, dy) {
        if (!this.drag) return;
        const panel = this.panels[this.drag.panelId];
        if (!panel) return;

        // Lift out of grid flow visually while placeholder holds the cell.
        panel.style.position = "fixed";
        panel.style.left = `${this.drag.originLeft}px`;
        panel.style.top = `${this.drag.originTop}px`;
        panel.style.width = `${this.drag.width}px`;
        panel.style.height = `${this.drag.height}px`;
        panel.style.zIndex = "40";
        panel.style.transform = `translate3d(${dx}px, ${dy}px, 0)`;
    }

    /**
     * @param {HTMLElement} panel
     * @param {PanelPlacement} place
     */
    #ensurePlaceholder(panel, place) {
        this.#removePlaceholder();
        this.placeholder = document.createElement("div");
        this.placeholder.className = "dash-panel-placeholder";
        this.placeholder.setAttribute("aria-hidden", "true");
        this.placeholder.style.gridColumn = `${place.col} / span ${place.colSpan}`;
        this.placeholder.style.gridRow = `${place.row} / span ${place.rowSpan}`;
        // Keep panel in DOM (listeners/audio intact); placeholder occupies the vacated cell.
        this.gridEl.insertBefore(this.placeholder, panel.nextSibling);
    }

    #removePlaceholder() {
        this.placeholder?.remove();
        this.placeholder = null;
    }

    /**
     * @param {KeyboardEvent} event
     * @param {string} panelId
     */
    #onHandleKeyDown(event, panelId) {
        const map = {
            ArrowLeft: "left",
            ArrowRight: "right",
            ArrowUp: "up",
            ArrowDown: "down"
        };
        const dir = map[event.key];
        if (!dir) return;
        event.preventDefault();
        this.moveKeyboard(panelId, /** @type {"up"|"down"|"left"|"right"} */ (dir));
    }

    /**
     * @param {PanelPlacement} place
     */
    #updateHint(place) {
        if (!this.dropHint) return;
        this.dropHint.style.gridColumn = `${place.col} / span ${place.colSpan}`;
        this.dropHint.style.gridRow = `${place.row} / span ${place.rowSpan}`;
    }

    /**
     * @param {string} message
     */
    #announce(message) {
        if (!this.liveRegion) return;
        this.liveRegion.textContent = "";
        // Retrigger SR announcement.
        requestAnimationFrame(() => {
            if (this.liveRegion) {
                this.liveRegion.textContent = message;
            }
        });
    }
}
