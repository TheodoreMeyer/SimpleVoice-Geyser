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
 * @property {HTMLButtonElement} [muteSpeakerBtn]
 * @property {HTMLElement} micIndicator
 * @property {HTMLElement} micSettings
 * @property {HTMLElement} vadMeter
 * @property {HTMLElement} vadEnergyBar
 * @property {HTMLElement} vadMarker
 * @property {HTMLInputElement} vadSlider
 * @property {HTMLInputElement} gainSlider
 * @property {HTMLElement} gainValueLabel
 * @property {HTMLInputElement} masterSlider
 * @property {HTMLElement} masterValueLabel
 * @property {HTMLElement} volumesRoot
 * @property {HTMLInputElement} volumesSearch
 * @property {HTMLElement} volumesList
 * @property {HTMLElement} micList
 * @property {HTMLElement} speakerList
 */

/**
 * @typedef {Object} ScreenElements
 * @property {HTMLElement} root
 * @property {HTMLButtonElement} openSettings
 * @property {HTMLButtonElement} openGroups
 * @property {HTMLButtonElement} openCreateGroup
 * @property {HTMLButtonElement} hubVolumesIcon
 * @property {HTMLButtonElement} openMic
 * @property {HTMLButtonElement} openSpeaker
 * @property {HTMLButtonElement} joinOpenMic
 * @property {HTMLButtonElement} joinOpenSpeaker
 * @property {HTMLElement} groupsList
 * @property {HTMLButtonElement} groupsOpenCurrent
 * @property {HTMLInputElement} groupsPassword
 * @property {HTMLButtonElement} groupsJoinSubmit
 * @property {HTMLButtonElement} groupsLeave
 * @property {HTMLElement} groupMenuTitle
 * @property {HTMLElement} groupMenuMembers
 * @property {HTMLInputElement} createGroupName
 * @property {HTMLInputElement} createGroupPassword
 * @property {HTMLSelectElement} createGroupType
 * @property {HTMLElement} createGroupTypeHint
 * @property {HTMLButtonElement} createGroupSubmit
 */

/**
 * @typedef {Object} PttElements
 * @property {HTMLElement} micCard
 * @property {HTMLSelectElement} transmitModeSelect
 * @property {HTMLButtonElement} [activationMethodBtn]
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
 * UI elements used by SvgClient.
 *
 * @typedef {Object} SvgUIElements
 * @property {FormElements} form
 * @property {AudioElements} audio
 * @property {PttElements} ptt
 * @property {ScreenElements} screens
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
