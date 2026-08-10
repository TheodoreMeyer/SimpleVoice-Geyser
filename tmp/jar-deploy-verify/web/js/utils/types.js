/**
 * @typedef {Object} FormElements
 * @property {HTMLFormElement} formEl
 * @property {HTMLButtonElement} joinButton
 * @property {HTMLElement} statusEl
 * @property {HTMLInputElement} usernameInput
 * @property {HTMLInputElement} passwordInput
 * @property {HTMLButtonElement} [passwordToggle]
 * @property {HTMLButtonElement} [copyPswdBtn]
 * @property {HTMLElement} [copyStatusEl]
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
 * @property {HTMLElement} [devToggle]
 * @property {HTMLElement} [devContent]
 */

/**
 * @typedef {Object} ViewElements
 * @property {HTMLElement} loginView
 * @property {HTMLElement} dashboardView
 */

/**
 * @typedef {Object} DashboardElements
 * @property {HTMLElement} [playerNameEl]
 * @property {HTMLElement} [wsStatusEl]
 * @property {HTMLElement} [audioModeEl]
 * @property {HTMLElement} [nativeNoticeEl]
 * @property {HTMLElement} [voiceControlsEl]
 * @property {HTMLButtonElement} [logoutBtn]
 * @property {HTMLElement} [micErrorEl]
 * @property {HTMLElement} [reconnectOverlay]
 * @property {HTMLElement} [gridEl]
 * @property {HTMLButtonElement} [resetLayoutBtn]
 * @property {HTMLElement} [layoutLiveEl]
 * @property {HTMLButtonElement} [resetAppearanceBtn]
 * @property {HTMLElement} [appearanceAccentGroup]
 * @property {HTMLElement} [appearanceBorderGroup]
 * @property {HTMLElement} [appearanceAccentSwatches]
 * @property {HTMLElement} [appearanceBorderSwatches]
 * @property {HTMLElement} [buildMismatchBanner]
 * @property {HTMLButtonElement} [reloadClientBtn]
 */

/**
 * @typedef {Object} GroupElements
 * @property {HTMLElement} listEl
 * @property {HTMLElement} currentGroupEl
 * @property {HTMLButtonElement} createBtn
 * @property {HTMLButtonElement} leaveBtn
 * @property {HTMLElement} createModal
 * @property {HTMLFormElement} createForm
 * @property {HTMLInputElement} createNameInput
 * @property {HTMLInputElement} createPasswordInput
 * @property {HTMLSelectElement} createTypeSelect
 * @property {HTMLElement} [createTypeHelp]
 * @property {HTMLElement} [createErrorEl]
 * @property {HTMLButtonElement} [createCloseBtn]
 * @property {HTMLButtonElement} createCancelBtn
 * @property {HTMLButtonElement} [createSubmitBtn]
 * @property {HTMLElement} joinModal
 * @property {HTMLFormElement} joinForm
 * @property {HTMLInputElement} joinPasswordInput
 * @property {HTMLElement} joinGroupNameEl
 * @property {HTMLElement} [joinErrorEl]
 * @property {HTMLButtonElement} [joinCloseBtn]
 * @property {HTMLButtonElement} joinCancelBtn
 * @property {HTMLElement} [typeHintEl]
 * @property {HTMLElement} [errorEl]
 */

/**
 * UI elements used by SvgClient.
 *
 * @typedef {Object} SvgUIElements
 * @property {FormElements} form
 * @property {AudioElements} audio
 * @property {PttElements} ptt
 * @property {DevElements} [dev]
 * @property {ViewElements} views
 * @property {DashboardElements} dashboard
 * @property {GroupElements} groups
 * @property {ChatElements} [chat]
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
 * @property {number} [sampleRate]
 * @property {string} degradedReason
 */

export {};
