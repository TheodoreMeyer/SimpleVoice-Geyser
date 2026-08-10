/**
 * Authenticated-dashboard border appearance themes.
 * Login view is never themed by this module.
 */

export const APPEARANCE_STORAGE_KEY = "svg.dashboard.appearance.v1";
export const APPEARANCE_VERSION = 1;

/** @type {readonly string[]} */
export const BORDER_THEMES = Object.freeze([
    "default",
    "glass",
    "cyan",
    "blue",
    "purple",
    "green",
    "red",
    "gold"
]);

/**
 * @param {unknown} value
 * @returns {string|null}
 */
export function validateTheme(value) {
    const theme = String(value || "").toLowerCase();
    return BORDER_THEMES.includes(theme) ? theme : null;
}

/**
 * @param {string} [storageKey]
 * @returns {string}
 */
export function loadTheme(storageKey = APPEARANCE_STORAGE_KEY) {
    try {
        const raw = localStorage.getItem(storageKey);
        if (!raw) {
            return "default";
        }
        const parsed = JSON.parse(raw);
        if (!parsed || parsed.version !== APPEARANCE_VERSION) {
            return "default";
        }
        return validateTheme(parsed.theme) || "default";
    } catch {
        return "default";
    }
}

/**
 * @param {string} theme
 * @param {string} [storageKey]
 */
export function saveTheme(theme, storageKey = APPEARANCE_STORAGE_KEY) {
    const valid = validateTheme(theme);
    if (!valid) {
        return;
    }
    localStorage.setItem(
        storageKey,
        JSON.stringify({ version: APPEARANCE_VERSION, theme: valid })
    );
}

/**
 * @param {string} [storageKey]
 */
export function clearTheme(storageKey = APPEARANCE_STORAGE_KEY) {
    localStorage.removeItem(storageKey);
}

/**
 * Apply theme to the dashboard root only — never the login view.
 */
export class AppearanceController {
    /**
     * @param {object} options
     * @param {HTMLElement} options.dashboardRoot
     * @param {HTMLSelectElement|null} [options.selectEl]
     * @param {HTMLElement|null} [options.resetBtn]
     * @param {string} [options.storageKey]
     * @param {() => void} [options.onChange]
     */
    constructor(options) {
        this.dashboardRoot = options.dashboardRoot;
        this.selectEl = options.selectEl || null;
        this.resetBtn = options.resetBtn || null;
        this.storageKey = options.storageKey || APPEARANCE_STORAGE_KEY;
        this.onChange = options.onChange || (() => {});
        this.theme = loadTheme(this.storageKey);
        this.bound = false;
    }

    init() {
        if (this.bound) {
            return;
        }
        this.bound = true;
        this.apply(this.theme, { persist: false });

        this.selectEl?.addEventListener("change", () => {
            const next = validateTheme(this.selectEl.value) || "default";
            this.apply(next, { persist: true });
        });

        this.resetBtn?.addEventListener("click", (event) => {
            event.preventDefault();
            clearTheme(this.storageKey);
            this.apply("default", { persist: true });
        });
    }

    /**
     * @param {string} theme
     * @param {{ persist?: boolean }} [opts]
     */
    apply(theme, opts = {}) {
        const { persist = true } = opts;
        const valid = validateTheme(theme) || "default";
        this.theme = valid;
        this.dashboardRoot.dataset.borderTheme = valid;
        if (this.selectEl && this.selectEl.value !== valid) {
            this.selectEl.value = valid;
        }
        if (persist) {
            saveTheme(valid, this.storageKey);
        }
        // Appearance must never recreate audio/WebSocket graphs.
        this.onChange(valid);
    }
}
