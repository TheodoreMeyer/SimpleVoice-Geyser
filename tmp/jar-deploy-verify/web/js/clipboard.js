/**
 * Clipboard helpers for the login `/svg pswd ` copy control.
 * Prefer the secure Clipboard API; fall back to a temporary textarea.
 */

export const PSWD_COMMAND = "/svg pswd ";
export const SVG_PASSWORD_COMMAND = PSWD_COMMAND;

/**
 * Copy text to the clipboard.
 * Uses Clipboard API only in a secure context; otherwise uses the legacy fallback.
 * @param {string} text
 * @param {HTMLElement|null} [restoreFocus]
 * @returns {Promise<void>}
 */
export async function copyText(text, restoreFocus = null) {
    const value = String(text ?? "");
    if (
        typeof window !== "undefined"
        && window.isSecureContext
        && typeof navigator !== "undefined"
        && navigator.clipboard
        && typeof navigator.clipboard.writeText === "function"
    ) {
        try {
            await navigator.clipboard.writeText(value);
            return;
        } catch {
            // Fall through to legacy path.
        }
    }

    fallbackCopyText(value, restoreFocus);
}

/**
 * Legacy execCommand fallback.
 * @param {string} text
 * @param {HTMLElement|null} [restoreFocus]
 * @returns {void}
 * @throws {Error} when the legacy copy operation reports failure
 */
export function fallbackCopyText(text, restoreFocus = null) {
    const textarea = document.createElement("textarea");
    textarea.value = String(text ?? "");
    textarea.setAttribute("readonly", "");
    textarea.setAttribute("aria-hidden", "true");
    textarea.style.position = "fixed";
    textarea.style.top = "0";
    textarea.style.left = "0";
    textarea.style.width = "1px";
    textarea.style.height = "1px";
    textarea.style.padding = "0";
    textarea.style.border = "none";
    textarea.style.outline = "none";
    textarea.style.boxShadow = "none";
    textarea.style.background = "transparent";
    textarea.style.opacity = "0";
    textarea.style.zIndex = "-1";

    document.body.appendChild(textarea);
    let ok = false;
    try {
        textarea.focus();
        textarea.select();
        textarea.setSelectionRange(0, textarea.value.length);
        ok = document.execCommand("copy");
    } catch {
        ok = false;
    } finally {
        textarea.remove();
        if (restoreFocus && typeof restoreFocus.focus === "function") {
            try {
                restoreFocus.focus();
            } catch {
                // ignore focus restoration failures
            }
        }
    }

    if (!ok) {
        throw new Error("Clipboard fallback failed");
    }
}

/** @deprecated Use fallbackCopyText */
export function copyTextFallback(text, restoreFocus = null) {
    try {
        fallbackCopyText(text, restoreFocus);
        return true;
    } catch {
        return false;
    }
}

/**
 * Production copy-command handler used by document-level event delegation.
 * @param {HTMLButtonElement|HTMLElement} button
 * @param {{ statusEl?: HTMLElement|null, command?: string, resetMs?: number }} [options]
 * @returns {Promise<boolean>}
 */
export async function handleCopyCommand(button, options = {}) {
    if (!button) {
        return false;
    }

    const statusEl = options.statusEl
        || document.getElementById("copy-command-status")
        || null;
    const command = options.command ?? SVG_PASSWORD_COMMAND;
    const defaultLabel = "Copy command";
    const successLabel = "Copied!";
    const resetMs = options.resetMs ?? 2000;

    if (button.dataset.copyBusy === "1") {
        return false;
    }
    button.dataset.copyBusy = "1";

    const announce = (message, success) => {
        if (!statusEl) return;
        statusEl.textContent = message;
        statusEl.dataset.state = success ? "ready" : "error";
    };

    let ok = false;
    try {
        await copyText(command, button);
        ok = true;
    } catch {
        try {
            fallbackCopyText(command, button);
            ok = true;
        } catch {
            ok = false;
        }
    }

    if (ok) {
        button.textContent = successLabel;
        announce("Copied!", true);
        setTimeout(() => {
            button.textContent = defaultLabel;
            if (statusEl) {
                statusEl.textContent = "";
                statusEl.dataset.state = "idle";
            }
            delete button.dataset.copyBusy;
        }, resetMs);
        return true;
    }

    button.textContent = defaultLabel;
    announce("Couldn’t copy automatically. Select and copy the command above.", false);
    delete button.dataset.copyBusy;
    return false;
}

/**
 * Bind a single copy-command control exactly once.
 * Prefer document-level delegation via handleCopyCommand in the production entrypoint.
 * @param {object} options
 * @param {HTMLButtonElement} options.button
 * @param {HTMLElement|null} [options.statusEl]
 * @param {string} [options.command]
 * @param {string} [options.defaultLabel]
 * @param {string} [options.successLabel]
 * @param {number} [options.resetMs]
 * @returns {() => void} dispose
 */
export function bindCopyPswdButton(options) {
    const button = options.button;
    if (!button) {
        return () => {};
    }

    if (typeof button._svgCopyDispose === "function") {
        button._svgCopyDispose();
    }

    const onActivate = (event) => {
        event.preventDefault();
        event.stopPropagation();
        return handleCopyCommand(button, {
            statusEl: options.statusEl || null,
            command: options.command,
            resetMs: options.resetMs
        });
    };

    button.addEventListener("click", onActivate);
    button.dataset.copyBound = "1";
    if (!button.getAttribute("type")) {
        button.setAttribute("type", "button");
    }
    if (!button.textContent?.trim() || button.textContent.trim() === "Copy") {
        button.textContent = options.defaultLabel || "Copy command";
    }

    const dispose = () => {
        button.removeEventListener("click", onActivate);
        delete button.dataset.copyBound;
        if (button._svgCopyDispose === dispose) {
            delete button._svgCopyDispose;
        }
    };
    button._svgCopyDispose = dispose;
    return dispose;
}
