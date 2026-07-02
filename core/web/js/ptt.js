import {getConnectedGamepad, getGamepadButtonName} from "./gamepad.js";
import {Logger} from "./utils/logger.js";

/**
 * @import {PttElements} from "./utils/types.js"
 */

const TRANSMIT_MODE_KEY = "svgTransmitMode";
const PTT_BINDING_KEY = "svgPttBinding";
const ALLOW_BACKGROUND_PTT_KEY = "svgAllowBackgroundPtt";

export class PttController {

    /**
     * @param {PttElements} elements
     */
    constructor(elements) {
        this.elements = elements;

        this.pttSources = new Set();

        this.bindingCaptureActive = false;
        this.muted = false;

        this.pttBinding = this.#loadPttBinding();
        this.allowBackgroundPtt = localStorage.getItem(ALLOW_BACKGROUND_PTT_KEY) === "true";
    }

    // ==================================================
    // Public API
    // ==================================================

    init() {
        this.elements.transmitModeSelect.value = localStorage.getItem(TRANSMIT_MODE_KEY) || "voice";

        this.elements.transmitModeSelect.addEventListener("change", () => {
            localStorage.setItem(TRANSMIT_MODE_KEY, this.#getTransmitMode());
            this.#updateTransmitModeUi();
        });

        if (this.elements.allowBackgroundPttCheckbox) {
            this.elements.allowBackgroundPttCheckbox.checked = this.allowBackgroundPtt;

            this.elements.allowBackgroundPttCheckbox.addEventListener("change", () => {
                this.allowBackgroundPtt = this.elements.allowBackgroundPttCheckbox.checked;
                localStorage.setItem(ALLOW_BACKGROUND_PTT_KEY, this.allowBackgroundPtt ? "true" : "false");
                Logger.log(`Background PTT ${this.allowBackgroundPtt ? "enabled" : "disabled"}.`);
            });
        }

        this.elements.bindPttBtn.addEventListener("click", () => {
            this.#setBindingCaptureState(true);
        });

        this.elements.clearPttBtn.addEventListener("click", () => {
            this.#savePttBinding(null);
            Logger.log("Push-to-talk binding cleared.");
        });

        this.elements.fullscreenPttBtn.addEventListener("click", () => {
            this.#requestFullscreenPtt();
        });

        this.elements.exitFullscreenPttBtn.addEventListener("click", () => {
            this.#exitFullscreenPtt();
        });

        this.#registerHoldButton(this.elements.pushToTalkBtn, "button");
        this.#registerHoldButton(this.elements.pushToTalkFullscreenBtn, "fullscreen");

        window.addEventListener("keydown", (event) => {
            if (this.bindingCaptureActive) {
                event.preventDefault();

                if (event.code === "Escape") {
                    this.#setBindingCaptureState(false);
                    return;
                }

                this.#capturePttBinding({ type: "keyboard", code: event.code });
                return;
            }

            if (!this.isPttMode() || !this.#bindingMatchesKeyboard(event) || event.repeat) return;
            if (this.#isEditableTarget(event.target)) return;

            event.preventDefault();
            this.#addPttSource(`keyboard:${this.pttBinding.code}`);
        });

        window.addEventListener("keyup", (event) => {
            if (!this.isPttMode() || !this.#bindingMatchesKeyboard(event)) return;

            event.preventDefault();
            this.#removePttSource(`keyboard:${this.pttBinding.code}`);
        });

        window.addEventListener("mousedown", (event) => {
            if (this.bindingCaptureActive) {
                event.preventDefault();
                this.#capturePttBinding({ type: "mouse", button: event.button });
                return;
            }

            if (!this.isPttMode() || !this.#bindingMatchesMouse(event)) return;

            event.preventDefault();
            this.#addPttSource(`mouse:${this.pttBinding.button}`);
        });

        window.addEventListener("mouseup", (event) => {
            if (!this.isPttMode() || !this.#bindingMatchesMouse(event)) return;

            event.preventDefault();
            this.#removePttSource(`mouse:${this.pttBinding.button}`);
        });

        window.addEventListener("auxclick", (event) => {
            if ((this.bindingCaptureActive || (this.isPttMode() && this.#bindingMatchesMouse(event))) && event.cancelable) {
                event.preventDefault();
            }
        });

        window.addEventListener("contextmenu", (event) => {
            if ((this.bindingCaptureActive || (this.isPttMode() && this.#bindingMatchesMouse(event))) && event.cancelable) {
                event.preventDefault();
            }
        });

        window.addEventListener("blur", () => {
            if (this.allowBackgroundPtt) {
                return;
            }
            this.#clearPttSources();
        });

        document.addEventListener("visibilitychange", () => {
            if (document.hidden) {
                if (this.allowBackgroundPtt) {
                    return;
                }
                this.#clearPttSources();
                return;
            }

            if (this.allowBackgroundPtt) {
                // Keyboard/mouse release events are not guaranteed while unfocused.
                this.#clearKeyboardAndMouseSources();
            }
        });

        window.addEventListener("focus", () => {
            if (this.allowBackgroundPtt) {
                // Clear stale keyboard/mouse holds once focus is regained.
                this.#clearKeyboardAndMouseSources();
            }
        });

        document.addEventListener("fullscreenchange", () => {
            this.#setFullscreenPtt(document.fullscreenElement === this.elements.pttFullscreenOverlay);
        });

        window.addEventListener("gamepadconnected", (event) => {
            Logger.log(`Controller connected: ${event.gamepad.id}`);
            this.#updatePttBindingLabel();
        });

        window.addEventListener("gamepaddisconnected", (event) => {
            Logger.log(`Controller disconnected: ${event.gamepad.id}`);
            this.#updatePttBindingLabel();
        });

        this.#updatePttBindingLabel();
        this.#updateTransmitModeUi();
        this.#updatePttButtons();
        window.requestAnimationFrame(() => this.#pollGamepads());
    }

    isPttMode() {
        return this.#getTransmitMode() === "ptt";
    }

    isPttActive() {
        return !this.muted && this.pttSources.size > 0;
    }

    setMuted(value) {
        this.muted = value;

        if (this.muted) {
            this.#clearPttSources();
        }

        this.#updatePttButtons();
    }

    reset() {
        this.#clearPttSources();
        this.#exitFullscreenPtt();
    }

    // ==================================================
    // Storage
    // ==================================================

    #loadPttBinding() {
        try {
            const raw = localStorage.getItem(PTT_BINDING_KEY);
            if (!raw) return null;

            const parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== "object") return null;

            if (parsed.type === "keyboard" && typeof parsed.code === "string") return parsed;
            if (parsed.type === "mouse" && Number.isInteger(parsed.button)) return parsed;
            if (parsed.type === "gamepad" && Number.isInteger(parsed.buttonIndex)) return parsed;
        } catch (error) {
            console.warn("Failed to load PTT binding:", error);
        }

        return null;
    }

    #savePttBinding(binding) {
        this.pttBinding = binding;

        if (binding) {
            localStorage.setItem(PTT_BINDING_KEY, JSON.stringify(binding));
        } else {
            localStorage.removeItem(PTT_BINDING_KEY);
        }

        this.#updatePttBindingLabel();
    }

    // ==================================================
    // State
    // ==================================================

    #getTransmitMode() {
        return this.elements.transmitModeSelect.value === "ptt" ? "ptt" : "voice";
    }

    #addPttSource(sourceId) {
        this.pttSources.add(sourceId);
        this.#updatePttButtons();
    }

    #removePttSource(sourceId) {
        this.pttSources.delete(sourceId);
        this.#updatePttButtons();
    }

    #clearPttSources() {
        this.pttSources.clear();
        this.#updatePttButtons();
    }

    #clearKeyboardAndMouseSources() {
        const retained = [];
        for (const sourceId of this.pttSources) {
            if (sourceId.startsWith("gamepad:")) {
                retained.push(sourceId);
            }
        }

        this.pttSources.clear();
        for (const sourceId of retained) {
            this.pttSources.add(sourceId);
        }

        this.#updatePttButtons();
    }

    // ==================================================
    // UI
    // ==================================================

    #updatePttButtons() {
        const active = this.isPttActive();
        this.elements.pushToTalkBtn.classList.toggle("active", active);
        this.elements.pushToTalkFullscreenBtn.classList.toggle("active", active);
        this.elements.pushToTalkBtn.textContent = active ? "Talking..." : "Hold to Talk";
        this.elements.pushToTalkFullscreenBtn.textContent = active ? "Talking..." : "Hold to Talk";
    }

    #updatePttBindingLabel() {
        this.elements.pttBindingLabel.textContent = this.#formatPttBinding(this.pttBinding);
    }

    #updateTransmitModeUi() {
        const pttMode = this.isPttMode();
        if (this.elements.pttCard) {
            this.elements.pttCard.classList.toggle("dev-hidden", !pttMode);
        }
        if (this.elements.micCard) {
            this.elements.micCard.classList.toggle("dev-hidden", pttMode);
        }

        this.elements.pttBindingControls.classList.toggle("dev-hidden", !pttMode);
        this.elements.pttControls.classList.toggle("dev-hidden", !pttMode);
        this.elements.fullscreenPttBtn.hidden = !pttMode;

        if (!pttMode) {
            this.#clearPttSources();
            this.#exitFullscreenPtt();
        }
    }

    #setBindingCaptureState(active) {
        this.bindingCaptureActive = active;
        this.elements.bindPttBtn.textContent = active ? "Press a key, mouse button, or controller button..." : "Bind Push-to-Talk";
        this.elements.bindPttBtn.disabled = active;
        this.elements.clearPttBtn.disabled = active;

        if (active) {
            this.elements.pttBindingLabel.textContent = "Waiting for input... press Escape to cancel.";
        } else {
            this.#updatePttBindingLabel();
        }
    }

    // ==================================================
    // Formatting
    // ==================================================

    #formatMouseButton(button) {
        if (button === 0) return "Mouse Left Button";
        if (button === 1) return "Mouse Middle Button";
        if (button === 2) return "Mouse Right Button";
        if (button === 3) return "Mouse Back Button";
        if (button === 4) return "Mouse Forward Button";
        return `Mouse Button ${button}`;
    }

    #formatKeyboardCode(code) {
        const aliases = {
            Space: "Space",
            Escape: "Escape",
            ShiftLeft: "Left Shift",
            ShiftRight: "Right Shift",
            ControlLeft: "Left Ctrl",
            ControlRight: "Right Ctrl",
            AltLeft: "Left Alt",
            AltRight: "Right Alt",
            MetaLeft: "Left Meta",
            MetaRight: "Right Meta"
        };

        if (aliases[code]) return aliases[code];
        return code
            .replace(/^Key/, "")
            .replace(/^Digit/, "")
            .replace(/([a-z])([A-Z])/g, "$1 $2");
    }

    #formatPttBinding(binding) {
        if (!binding) {
            return "No binding set. Use the hold button or add a binding.";
        }

        if (binding.type === "keyboard") {
            return `Bound to ${this.#formatKeyboardCode(binding.code)}`;
        }

        if (binding.type === "mouse") {
            return `Bound to ${this.#formatMouseButton(binding.button)}`;
        }

        if (binding.type === "gamepad") {
            const connectedGamepad = getConnectedGamepad();
            const buttonName = getGamepadButtonName(binding.buttonIndex, connectedGamepad ? connectedGamepad.id : "");
            return `Bound to Controller ${buttonName}`;
        }

        return "No binding set. Use the hold button or add a binding.";
    }

    // ==================================================
    // Fullscreen
    // ==================================================

    #setFullscreenPtt(active) {
        document.body.classList.toggle("fullscreen-ptt-active", active);
        this.elements.pttFullscreenOverlay.classList.toggle("visible", active);
        this.elements.pttFullscreenOverlay.setAttribute("aria-hidden", active ? "false" : "true");
    }

    async #requestFullscreenPtt() {
        if (!this.isPttMode()) return;

        this.#setFullscreenPtt(true);

        if (this.elements.pttFullscreenOverlay.requestFullscreen && document.fullscreenElement !== this.elements.pttFullscreenOverlay) {
            try {
                await this.elements.pttFullscreenOverlay.requestFullscreen();
            } catch (error) {
                console.warn("Fullscreen request failed:", error);
            }
        }
    }

    async #exitFullscreenPtt() {
        if (document.fullscreenElement === this.elements.pttFullscreenOverlay && document.exitFullscreen) {
            try {
                await document.exitFullscreen();
            } catch (error) {
                console.warn("Exiting fullscreen failed:", error);
            }
        }

        this.#setFullscreenPtt(false);
    }

    // ==================================================
    // Binding
    // ==================================================

    #capturePttBinding(binding) {
        this.#savePttBinding(binding);
        this.#setBindingCaptureState(false);
        Logger.log(`Push-to-talk binding saved: ${this.#formatPttBinding(binding)}`);
    }

    #bindingMatchesKeyboard(event) {
        return this.pttBinding && this.pttBinding.type === "keyboard" && event.code === this.pttBinding.code;
    }

    #bindingMatchesMouse(event) {
        return this.pttBinding && this.pttBinding.type === "mouse" && event.button === this.pttBinding.button;
    }

    // ==================================================
    // Input Helpers
    // ==================================================

    #isEditableTarget(target) {
        if (!(target instanceof HTMLElement)) return false;
        if (target.isContentEditable) return true;

        const tagName = target.tagName;
        return tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT";
    }

    #registerHoldButton(button, sourcePrefix) {
        button.addEventListener("pointerdown", (event) => {
            if (!this.isPttMode()) return;

            event.preventDefault();
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            this.#addPttSource(sourceId);

            if (button.setPointerCapture) {
                button.setPointerCapture(event.pointerId);
            }
        });

        const releasePointer = (event) => {
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            this.#removePttSource(sourceId);
        };

        button.addEventListener("pointerup", releasePointer);
        button.addEventListener("pointercancel", releasePointer);
        button.addEventListener("lostpointercapture", releasePointer);
        button.addEventListener("contextmenu", (event) => event.preventDefault());
    }

    #pollGamepads() {
        const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];

        if (this.bindingCaptureActive) {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const pressedIndex = gamepad.buttons.findIndex((button) => button.pressed || button.value > 0.5);
                if (pressedIndex !== -1) {
                    this.#capturePttBinding({ type: "gamepad", buttonIndex: pressedIndex });
                    break;
                }
            }
        }

        if (this.isPttMode() && this.pttBinding && this.pttBinding.type === "gamepad") {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const button = gamepad.buttons[this.pttBinding.buttonIndex];
                const sourceId = `gamepad:${gamepad.index}:${this.pttBinding.buttonIndex}`;
                if (button && (button.pressed || button.value > 0.5)) {
                    this.#addPttSource(sourceId);
                } else {
                    this.#removePttSource(sourceId);
                }
            }
        }

        window.requestAnimationFrame(() => this.#pollGamepads());
    }
}
