import { getConnectedGamepad, getGamepadButtonName } from "./gamepad.js";

const TRANSMIT_MODE_KEY = "svgTransmitMode";
const PTT_BINDING_KEY = "svgPttBinding";
const ALLOW_BACKGROUND_PTT_KEY = "svgAllowBackgroundPtt";

export function createPttController({ elements, log }) {
    const {
        transmitModeSelect,
        pttBindingControls,
        bindPttBtn,
        clearPttBtn,
        pttBindingLabel,
        pttControls,
        pushToTalkBtn,
        fullscreenPttBtn,
        pttFullscreenOverlay,
        pushToTalkFullscreenBtn,
        exitFullscreenPttBtn,
        allowBackgroundPttCheckbox
    } = elements;

    const logFn = typeof log === "function" ? log : () => {};

    const touchDevice = window.matchMedia("(pointer: coarse)").matches || navigator.maxTouchPoints > 0;
    const pttSources = new Set();

    let bindingCaptureActive = false;
    let muted = false;
    let pttBinding = loadPttBinding();
    let allowBackgroundPtt = localStorage.getItem(ALLOW_BACKGROUND_PTT_KEY) === "true";

    if (allowBackgroundPttCheckbox) {
        allowBackgroundPttCheckbox.checked = allowBackgroundPtt;
        allowBackgroundPttCheckbox.addEventListener("change", () => {
            allowBackgroundPtt = allowBackgroundPttCheckbox.checked;
            localStorage.setItem(ALLOW_BACKGROUND_PTT_KEY, allowBackgroundPtt ? "true" : "false");
            logFn(`Background PTT ${allowBackgroundPtt ? "enabled" : "disabled"}.`);
        });
    }

    function loadPttBinding() {
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

    function savePttBinding(binding) {
        pttBinding = binding;

        if (binding) {
            localStorage.setItem(PTT_BINDING_KEY, JSON.stringify(binding));
        } else {
            localStorage.removeItem(PTT_BINDING_KEY);
        }

        updatePttBindingLabel();
    }

    function getTransmitMode() {
        return transmitModeSelect.value === "ptt" ? "ptt" : "voice";
    }

    function isPttMode() {
        return getTransmitMode() === "ptt";
    }

    function isPttActive() {
        return !muted && pttSources.size > 0;
    }

    function addPttSource(sourceId) {
        pttSources.add(sourceId);
        updatePttButtons();
    }

    function removePttSource(sourceId) {
        pttSources.delete(sourceId);
        updatePttButtons();
    }

    function clearPttSources() {
        pttSources.clear();
        updatePttButtons();
    }

    function clearKeyboardAndMouseSources() {
        const retained = [];
        for (const sourceId of pttSources) {
            if (sourceId.startsWith("gamepad:")) {
                retained.push(sourceId);
            }
        }

        pttSources.clear();
        for (const sourceId of retained) {
            pttSources.add(sourceId);
        }

        updatePttButtons();
    }

    function updatePttButtons() {
        const active = isPttActive();
        pushToTalkBtn.classList.toggle("active", active);
        pushToTalkFullscreenBtn.classList.toggle("active", active);
        pushToTalkBtn.textContent = active ? "Talking..." : "Hold to Talk";
        pushToTalkFullscreenBtn.textContent = active ? "Talking..." : "Hold to Talk";
    }

    function setBindingCaptureState(active) {
        bindingCaptureActive = active;
        bindPttBtn.textContent = active ? "Press a key, mouse button, or controller button..." : "Bind Push-to-Talk";
        bindPttBtn.disabled = active;
        clearPttBtn.disabled = active;

        if (active) {
            pttBindingLabel.textContent = "Waiting for input... press Escape to cancel.";
        } else {
            updatePttBindingLabel();
        }
    }

    function formatMouseButton(button) {
        if (button === 0) return "Mouse Left Button";
        if (button === 1) return "Mouse Middle Button";
        if (button === 2) return "Mouse Right Button";
        if (button === 3) return "Mouse Back Button";
        if (button === 4) return "Mouse Forward Button";
        return `Mouse Button ${button}`;
    }

    function formatKeyboardCode(code) {
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

    function formatPttBinding(binding) {
        if (!binding) {
            return "No binding set. Use the hold button or add a binding.";
        }

        if (binding.type === "keyboard") {
            return `Bound to ${formatKeyboardCode(binding.code)}`;
        }

        if (binding.type === "mouse") {
            return `Bound to ${formatMouseButton(binding.button)}`;
        }

        if (binding.type === "gamepad") {
            const connectedGamepad = getConnectedGamepad();
            const buttonName = getGamepadButtonName(binding.buttonIndex, connectedGamepad ? connectedGamepad.id : "");
            return `Bound to Controller ${buttonName}`;
        }

        return "No binding set. Use the hold button or add a binding.";
    }

    function updatePttBindingLabel() {
        pttBindingLabel.textContent = formatPttBinding(pttBinding);
    }

    function setFullscreenPtt(active) {
        document.body.classList.toggle("fullscreen-ptt-active", active);
        pttFullscreenOverlay.classList.toggle("visible", active);
        pttFullscreenOverlay.setAttribute("aria-hidden", active ? "false" : "true");
    }

    async function requestFullscreenPtt() {
        if (!isPttMode()) return;

        setFullscreenPtt(true);

        if (pttFullscreenOverlay.requestFullscreen && document.fullscreenElement !== pttFullscreenOverlay) {
            try {
                await pttFullscreenOverlay.requestFullscreen();
            } catch (error) {
                console.warn("Fullscreen request failed:", error);
            }
        }
    }

    async function exitFullscreenPtt() {
        if (document.fullscreenElement === pttFullscreenOverlay && document.exitFullscreen) {
            try {
                await document.exitFullscreen();
            } catch (error) {
                console.warn("Exiting fullscreen failed:", error);
            }
        }

        setFullscreenPtt(false);
    }

    function updateTransmitModeUi() {
        const pttMode = isPttMode();
        pttBindingControls.hidden = !pttMode;
        pttControls.hidden = !pttMode;
        fullscreenPttBtn.hidden = !pttMode || !touchDevice;

        if (!pttMode) {
            clearPttSources();
            exitFullscreenPtt();
        }
    }

    function isEditableTarget(target) {
        if (!(target instanceof HTMLElement)) return false;
        if (target.isContentEditable) return true;

        const tagName = target.tagName;
        return tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT";
    }

    function bindingMatchesKeyboard(event) {
        return pttBinding && pttBinding.type === "keyboard" && event.code === pttBinding.code;
    }

    function bindingMatchesMouse(event) {
        return pttBinding && pttBinding.type === "mouse" && event.button === pttBinding.button;
    }

    function capturePttBinding(binding) {
        savePttBinding(binding);
        setBindingCaptureState(false);
        logFn(`Push-to-talk binding saved: ${formatPttBinding(binding)}`);
    }

    function registerHoldButton(button, sourcePrefix) {
        button.addEventListener("pointerdown", (event) => {
            if (!isPttMode()) return;

            event.preventDefault();
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            addPttSource(sourceId);

            if (button.setPointerCapture) {
                button.setPointerCapture(event.pointerId);
            }
        });

        const releasePointer = (event) => {
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            removePttSource(sourceId);
        };

        button.addEventListener("pointerup", releasePointer);
        button.addEventListener("pointercancel", releasePointer);
        button.addEventListener("lostpointercapture", releasePointer);
        button.addEventListener("contextmenu", (event) => event.preventDefault());
    }

    function pollGamepads() {
        const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];

        if (bindingCaptureActive) {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const pressedIndex = gamepad.buttons.findIndex((button) => button.pressed || button.value > 0.5);
                if (pressedIndex !== -1) {
                    capturePttBinding({ type: "gamepad", buttonIndex: pressedIndex });
                    break;
                }
            }
        }

        if (isPttMode() && pttBinding && pttBinding.type === "gamepad") {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const button = gamepad.buttons[pttBinding.buttonIndex];
                const sourceId = `gamepad:${gamepad.index}:${pttBinding.buttonIndex}`;
                if (button && (button.pressed || button.value > 0.5)) {
                    addPttSource(sourceId);
                } else {
                    removePttSource(sourceId);
                }
            }
        }

        window.requestAnimationFrame(pollGamepads);
    }

    function init() {
        transmitModeSelect.value = localStorage.getItem(TRANSMIT_MODE_KEY) || "voice";

        transmitModeSelect.addEventListener("change", () => {
            localStorage.setItem(TRANSMIT_MODE_KEY, getTransmitMode());
            updateTransmitModeUi();
        });

        bindPttBtn.addEventListener("click", () => {
            setBindingCaptureState(true);
        });

        clearPttBtn.addEventListener("click", () => {
            savePttBinding(null);
            logFn("Push-to-talk binding cleared.");
        });

        fullscreenPttBtn.addEventListener("click", () => {
            requestFullscreenPtt();
        });

        exitFullscreenPttBtn.addEventListener("click", () => {
            exitFullscreenPtt();
        });

        registerHoldButton(pushToTalkBtn, "button");
        registerHoldButton(pushToTalkFullscreenBtn, "fullscreen");

        window.addEventListener("keydown", (event) => {
            if (bindingCaptureActive) {
                event.preventDefault();

                if (event.code === "Escape") {
                    setBindingCaptureState(false);
                    return;
                }

                capturePttBinding({ type: "keyboard", code: event.code });
                return;
            }

            if (!isPttMode() || !bindingMatchesKeyboard(event) || event.repeat) return;
            if (isEditableTarget(event.target)) return;

            event.preventDefault();
            addPttSource(`keyboard:${pttBinding.code}`);
        });

        window.addEventListener("keyup", (event) => {
            if (!isPttMode() || !bindingMatchesKeyboard(event)) return;

            event.preventDefault();
            removePttSource(`keyboard:${pttBinding.code}`);
        });

        window.addEventListener("mousedown", (event) => {
            if (bindingCaptureActive) {
                event.preventDefault();
                capturePttBinding({ type: "mouse", button: event.button });
                return;
            }

            if (!isPttMode() || !bindingMatchesMouse(event)) return;

            event.preventDefault();
            addPttSource(`mouse:${pttBinding.button}`);
        });

        window.addEventListener("mouseup", (event) => {
            if (!isPttMode() || !bindingMatchesMouse(event)) return;

            event.preventDefault();
            removePttSource(`mouse:${pttBinding.button}`);
        });

        window.addEventListener("auxclick", (event) => {
            if ((bindingCaptureActive || (isPttMode() && bindingMatchesMouse(event))) && event.cancelable) {
                event.preventDefault();
            }
        });

        window.addEventListener("contextmenu", (event) => {
            if ((bindingCaptureActive || (isPttMode() && bindingMatchesMouse(event))) && event.cancelable) {
                event.preventDefault();
            }
        });

        window.addEventListener("blur", () => {
            if (allowBackgroundPtt) {
                return;
            }
            clearPttSources();
        });

        document.addEventListener("visibilitychange", () => {
            if (document.hidden) {
                if (allowBackgroundPtt) {
                    return;
                }
                clearPttSources();
                return;
            }

            if (allowBackgroundPtt) {
                // Keyboard/mouse release events are not guaranteed while unfocused.
                clearKeyboardAndMouseSources();
            }
        });

        window.addEventListener("focus", () => {
            if (allowBackgroundPtt) {
                // Clear stale keyboard/mouse holds once focus is regained.
                clearKeyboardAndMouseSources();
            }
        });

        document.addEventListener("fullscreenchange", () => {
            setFullscreenPtt(document.fullscreenElement === pttFullscreenOverlay);
        });

        window.addEventListener("gamepadconnected", (event) => {
            logFn(`Controller connected: ${event.gamepad.id}`);
            updatePttBindingLabel();
        });

        window.addEventListener("gamepaddisconnected", (event) => {
            logFn(`Controller disconnected: ${event.gamepad.id}`);
            updatePttBindingLabel();
        });

        updatePttBindingLabel();
        updateTransmitModeUi();
        updatePttButtons();
        window.requestAnimationFrame(pollGamepads);
    }

    return {
        init,
        isPttMode,
        isPttActive,
        setMuted(value) {
            muted = value;
            if (muted) {
                clearPttSources();
            }
            updatePttButtons();
        },
        reset() {
            clearPttSources();
            exitFullscreenPtt();
        }
    };
}
