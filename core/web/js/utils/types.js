/**
 * @typedef {Object} FormElements
 * @property {HTMLFormElement} formEl
 * @property {HTMLButtonElement} joinButton
 * @property {HTMLElement} statusEl
 * @property {HTMLInputElement} usernameInput
 * @property {HTMLInputElement} passwordInput
 */

/**
 * @typedef {Object} ChatElements
 * @property {HTMLElement} logEl
 * @property {HTMLInputElement} inputEl
 * @property {HTMLButtonElement} sendBtn
 */

/**
 * @typedef {Object} AudioElements
 * @property {HTMLSelectElement} speakerSelect
 * @property {HTMLSelectElement} micSelect
 * @property {HTMLButtonElement} muteBtn
 * @property {HTMLElement} micIndicator
 */

/**
 * @typedef {Object} PttElements
 * @property {HTMLElement} micCard
 * @property {HTMLSelectElement} transmitModeSelect
 * @property {HTMLElement} pttCard
 * @property {HTMLElement} pttBindingControls
 * @property {HTMLButtonElement} bindPttBtn
 * @property {HTMLButtonElement} clearPttBtn
 * @property {HTMLElement} pttBindingLabel
 * @property {HTMLElement} pttControls
 * @property {HTMLButtonElement} pushToTalkBtn
 * @property {HTMLButtonElement} fullscreenPttBtn
 * @property {HTMLElement} pttFullscreenOverlay
 * @property {HTMLButtonElement} pushToTalkFullscreenBtn
 * @property {HTMLButtonElement} exitFullscreenPttBtn
 * @property {HTMLInputElement} allowBackgroundPttCheckbox
 */

/**
 * @typedef {Object} DevElements
 * @property {HTMLElement} devToggle
 * @property {HTMLElement} devContent
 */

/**
 * UI elements used by SvgClient.
 *
 * @typedef {Object} SvgUIElements
 * @property {FormElements} form
 * @property {AudioElements} audio
 * @property {PttElements} ptt
 * @property {DevElements} dev
 */

/**
 * Configuration accepted by SvgClient.
 *
 * @typedef {Object} SvgClientOptions
 * @property {SvgUIElements} ui
 * @property {ChatElements} chat
 */

/**
 * Browser audio capability information.
 *
 * @typedef {Object} AudioRuntime
 * @property {boolean} audioContextSupported
 * @property {boolean} workletSupported
 * @property {boolean} mediaDevicesSupported
 * @property {boolean} canCaptureMic
 * @property {boolean} canSelectOutput
 * @property {string} degradedReason
 */

export {};