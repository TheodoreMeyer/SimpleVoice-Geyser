/**
 * Authenticated-dashboard appearance (accent theme + border style).
 * Login view is never themed by this module.
 */

export const APPEARANCE_STORAGE_KEY = "svg.dashboard.appearance.v2";
export const APPEARANCE_STORAGE_KEY_V1 = "svg.dashboard.appearance.v1";
export const APPEARANCE_VERSION = 2;

/** @type {readonly string[]} */
export const ACCENT_THEMES = Object.freeze([
    "default",
    "cyan",
    "blue",
    "purple",
    "green",
    "red",
    "gold"
]);

/** @type {readonly string[]} */
export const BORDER_STYLES = Object.freeze([
    "default",
    "glass",
    "subtle",
    "glow",
    "high-contrast"
]);

/**
 * @param {unknown} value
 * @returns {string|null}
 */
export function validateAccent(value) {
    const accent = String(value || "").toLowerCase();
    return ACCENT_THEMES.includes(accent) ? accent : null;
}

/**
 * @param {unknown} value
 * @returns {string|null}
 */
export function validateBorder(value) {
    const border = String(value || "").toLowerCase();
    return BORDER_STYLES.includes(border) ? border : null;
}

/**
 * @typedef {{ accent: string, border: string }} AppearancePrefs
 */

/**
 * Migrates a v1 `{version:1, theme}` record to v2 `{accent, border}`.
 * v1 conflated color accents and the "glass" border into a single `theme`.
 * @param {unknown} parsed
 * @returns {AppearancePrefs}
 */
function migrateV1(parsed) {
    const raw = /** @type {any} */ (parsed);
    if (!raw || raw.version !== 1) {
        return { accent: "default", border: "default" };
    }
    const theme = String(raw.theme || "").toLowerCase();
    const colorThemes = ["cyan", "blue", "purple", "green", "red", "gold"];
    return {
        accent: colorThemes.includes(theme) ? theme : "default",
        border: theme === "glass" ? "glass" : "default"
    };
}

/**
 * @param {string} [storageKey]
 * @returns {AppearancePrefs}
 */
export function loadAppearance(storageKey = APPEARANCE_STORAGE_KEY) {
    try {
        let raw = localStorage.getItem(storageKey);
        if (!raw && storageKey === APPEARANCE_STORAGE_KEY) {
            // Migrate v1 → v2 once, then drop the old key.
            raw = localStorage.getItem(APPEARANCE_STORAGE_KEY_V1);
            if (raw) {
                const migrated = migrateV1(JSON.parse(raw));
                saveAppearance(migrated.accent, migrated.border, APPEARANCE_STORAGE_KEY);
                localStorage.removeItem(APPEARANCE_STORAGE_KEY_V1);
                return migrated;
            }
        }
        if (!raw) {
            return { accent: "default", border: "default" };
        }
        const parsed = JSON.parse(raw);
        if (!parsed || parsed.version !== APPEARANCE_VERSION) {
            return { accent: "default", border: "default" };
        }
        return {
            accent: validateAccent(parsed.accent) || "default",
            border: validateBorder(parsed.border) || "default"
        };
    } catch {
        return { accent: "default", border: "default" };
    }
}

/**
 * @param {string} accent
 * @param {string} border
 * @param {string} [storageKey]
 */
export function saveAppearance(accent, border, storageKey = APPEARANCE_STORAGE_KEY) {
    const validAccent = validateAccent(accent) || "default";
    const validBorder = validateBorder(border) || "default";
    localStorage.setItem(
        storageKey,
        JSON.stringify({ version: APPEARANCE_VERSION, accent: validAccent, border: validBorder })
    );
}

/**
 * @param {string} [storageKey]
 */
export function clearAppearance(storageKey = APPEARANCE_STORAGE_KEY) {
    localStorage.removeItem(storageKey);
    if (storageKey === APPEARANCE_STORAGE_KEY) {
        localStorage.removeItem(APPEARANCE_STORAGE_KEY_V1);
    }
}

/**
 * Apply accent + border appearance to the dashboard root only — never the
 * login view.
 */
export class AppearanceController {
    /**
     * @param {object} options
     * @param {HTMLElement} options.dashboardRoot
     * @param {HTMLSelectElement|null} [options.accentSelectEl]
     * @param {HTMLSelectElement|null} [options.borderSelectEl]
     * @param {HTMLElement|null} [options.accentSwatchGroupEl]
     * @param {HTMLElement|null} [options.borderSwatchGroupEl]
     * @param {HTMLElement|null} [options.resetBtn]
     * @param {string} [options.storageKey]
     * @param {(accent: string, border: string) => void} [options.onChange]
     */
    constructor(options) {
        this.dashboardRoot = options.dashboardRoot;
        this.accentSelectEl = options.accentSelectEl || null;
        this.borderSelectEl = options.borderSelectEl || null;
        this.accentSwatchGroupEl = options.accentSwatchGroupEl || null;
        this.borderSwatchGroupEl = options.borderSwatchGroupEl || null;
        this.resetBtn = options.resetBtn || null;
        this.storageKey = options.storageKey || APPEARANCE_STORAGE_KEY;
        this.onChange = options.onChange || (() => {});
        const loaded = loadAppearance(this.storageKey);
        this.accent = loaded.accent;
        this.border = loaded.border;
        this.bound = false;
    }

    init() {
        if (this.bound) {
            return;
        }
        this.bound = true;
        this.apply(this.accent, this.border, { persist: false });

        this.accentSelectEl?.addEventListener("change", () => {
            const next = validateAccent(this.accentSelectEl.value) || "default";
            this.apply(next, this.border, { persist: true });
        });

        this.borderSelectEl?.addEventListener("change", () => {
            const next = validateBorder(this.borderSelectEl.value) || "default";
            this.apply(this.accent, next, { persist: true });
        });

        this.accentSwatchGroupEl?.addEventListener("click", (event) => {
            const target = /** @type {HTMLElement} */ (event.target);
            const btn = target?.closest?.("[data-accent]");
            if (!btn) {
                return;
            }
            const next = validateAccent(btn.getAttribute("data-accent")) || "default";
            this.apply(next, this.border, { persist: true });
        });

        this.borderSwatchGroupEl?.addEventListener("click", (event) => {
            const target = /** @type {HTMLElement} */ (event.target);
            const btn = target?.closest?.("[data-border]");
            if (!btn) {
                return;
            }
            const next = validateBorder(btn.getAttribute("data-border")) || "default";
            this.apply(this.accent, next, { persist: true });
        });

        this.resetBtn?.addEventListener("click", (event) => {
            event.preventDefault();
            clearAppearance(this.storageKey);
            this.apply("default", "default", { persist: true });
        });
    }

    /**
     * @param {string} accent
     * @param {string} border
     * @param {{ persist?: boolean }} [opts]
     */
    apply(accent, border, opts = {}) {
        const { persist = true } = opts;
        const validAccent = validateAccent(accent) || "default";
        const validBorder = validateBorder(border) || "default";
        this.accent = validAccent;
        this.border = validBorder;
        this.dashboardRoot.dataset.accentTheme = validAccent;
        this.dashboardRoot.dataset.borderStyle = validBorder;
        this.#syncControls();
        if (persist) {
            saveAppearance(validAccent, validBorder, this.storageKey);
        }
        // Appearance must never recreate audio/WebSocket graphs.
        this.onChange(validAccent, validBorder);
    }

    #syncControls() {
        if (this.accentSelectEl) {
            this.accentSelectEl.value = this.accent;
        }
        if (this.borderSelectEl) {
            this.borderSelectEl.value = this.border;
        }
        this.accentSwatchGroupEl?.querySelectorAll?.("[data-accent]")?.forEach?.((btn) => {
            const isActive = btn.getAttribute("data-accent") === this.accent;
            btn.classList.toggle("is-active", isActive);
            btn.setAttribute("aria-pressed", String(isActive));
        });
        this.borderSwatchGroupEl?.querySelectorAll?.("[data-border]")?.forEach?.((btn) => {
            const isActive = btn.getAttribute("data-border") === this.border;
            btn.classList.toggle("is-active", isActive);
            btn.setAttribute("aria-pressed", String(isActive));
        });
    }
}
