/**
 * Per-player / master receive volume prefs (localStorage) + roster merge for Adjust Volumes UI.
 */

const STORAGE_KEY = "svgReceiveVolumes_v1";

/** @typedef {{ percent: number, muted: boolean }} VolumeEntry */
/** @typedef {{ master: number, deafen: boolean, other: VolumeEntry, players: Record<string, VolumeEntry> }} VolumePrefs */
/** @typedef {{ uuid: string, name: string }} RosterPlayer */

/** @type {VolumePrefs} */
let prefs = loadPrefs();

/** @type {Map<string, string>} uuid -> name from server roster */
const rosterNames = new Map();

/** @type {Map<string, string>} uuid -> name from recently heard frames (fallback label) */
const heardNames = new Map();

/** @type {Set<() => void>} */
const changeListeners = new Set();

/**
 * Normalize UUID strings so roster + audio-frame keys match.
 * @param {string|null|undefined} uuid
 * @returns {string}
 */
function normalizeUuid(uuid) {
    return String(uuid || "").trim().toLowerCase();
}

/**
 * Temporary label when roster name is unknown.
 * Floodgate UUIDs share the prefix 00000000-0000-0000-0009-… — never use the first 8 hex chars.
 * @param {string} uuid
 * @returns {string}
 */
function shortLabel(uuid) {
    const id = normalizeUuid(uuid);
    const parts = id.split("-");
    if (parts.length === 5 && parts[4]) {
        return parts[4];
    }
    return id.length > 12 ? id.slice(-12) : id;
}

/**
 * @param {string} uuid
 * @returns {string}
 */
function resolveName(uuid) {
    const id = normalizeUuid(uuid);
    return rosterNames.get(id) || heardNames.get(id) || shortLabel(id);
}

/**
 * True when `label` looks like a UUID fragment, not a real player name.
 * @param {string|null|undefined} label
 * @param {string} uuid
 */
function isPlaceholderName(label, uuid) {
    const name = String(label || "").trim().toLowerCase();
    if (!name) {
        return true;
    }
    const id = normalizeUuid(uuid);
    if (name === id || name === shortLabel(id)) {
        return true;
    }
    // Old bug: uuid.slice(0, 8) → "00000000" for every Floodgate player.
    if (/^[0-9a-f]{8}$/i.test(name) && id.startsWith(name)) {
        return true;
    }
    return false;
}

function defaultPrefs() {
    return {
        master: 100,
        deafen: false,
        other: { percent: 100, muted: false },
        players: {}
    };
}

function clampPercent(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) {
        return 100;
    }
    return Math.max(0, Math.min(200, Math.round(n)));
}

function normalizeEntry(raw) {
    if (!raw || typeof raw !== "object") {
        return { percent: 100, muted: false };
    }
    return {
        percent: clampPercent(raw.percent),
        muted: !!raw.muted
    };
}

/** @returns {VolumePrefs} */
function loadPrefs() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) {
            return defaultPrefs();
        }
        const parsed = JSON.parse(raw);
        const players = {};
        if (parsed?.players && typeof parsed.players === "object") {
            for (const [uuid, entry] of Object.entries(parsed.players)) {
                players[String(uuid)] = normalizeEntry(entry);
            }
        }
        return {
            master: clampPercent(parsed?.master ?? 100),
            deafen: !!parsed?.deafen,
            other: normalizeEntry(parsed?.other),
            players
        };
    } catch {
        return defaultPrefs();
    }
}

/** @param {boolean} [notify] */
function persist(notify = true) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs));
    } catch {
        // quota / private mode — ignore
    }
    if (!notify) {
        return;
    }
    for (const listener of changeListeners) {
        try {
            listener();
        } catch {
            // ignore UI listener errors
        }
    }
}

/**
 * @param {string|null|undefined} uuid
 * @returns {number} linear gain multiplier (0 when muted)
 */
export function getEffectiveGain(uuid) {
    if (prefs.deafen) {
        return 0;
    }
    const master = clampPercent(prefs.master) / 100;
    /** @type {VolumeEntry} */
    let entry;
    const id = normalizeUuid(uuid);
    if (id && prefs.players[id]) {
        entry = prefs.players[id];
    } else if (uuid && prefs.players[uuid]) {
        // Legacy prefs keys before UUID normalization.
        entry = prefs.players[uuid];
    } else {
        entry = prefs.other;
    }
    if (entry.muted) {
        return 0;
    }
    return master * (clampPercent(entry.percent) / 100);
}

/**
 * Multiply samples in-place by user receive gain.
 * @param {Float32Array} samples
 * @param {string|null|undefined} uuid null = Other bucket (svg-v2 without sender)
 * @returns {Float32Array}
 */
export function applyUserGain(samples, uuid) {
    return multiplySamples(samples, getEffectiveGain(uuid));
}

/**
 * Legacy PCM path: master volume only (no per-player / Other).
 * @param {Float32Array} samples
 * @returns {Float32Array}
 */
export function applyMasterGainOnly(samples) {
    if (prefs.deafen) {
        return multiplySamples(samples, 0);
    }
    return multiplySamples(samples, clampPercent(prefs.master) / 100);
}

/** @param {Float32Array} samples @param {number} gain */
function multiplySamples(samples, gain) {
    if (!samples || samples.length === 0) {
        return samples;
    }
    if (gain === 1) {
        return samples;
    }
    if (gain === 0) {
        samples.fill(0);
        return samples;
    }
    for (let i = 0; i < samples.length; i++) {
        samples[i] *= gain;
    }
    return samples;
}

/** @returns {VolumePrefs} */
export function getPrefs() {
    return {
        master: prefs.master,
        deafen: !!prefs.deafen,
        other: { ...prefs.other },
        players: { ...prefs.players }
    };
}

/**
 * @param {number} percent
 * @param {{ notify?: boolean }} [opts] notify=false avoids full UI re-render while dragging
 */
export function setMaster(percent, opts = {}) {
    prefs.master = clampPercent(percent);
    persist(opts.notify !== false);
}

/** @returns {boolean} */
export function isDeafened() {
    return !!prefs.deafen;
}

/**
 * @param {boolean} [force] if set, apply that value; otherwise toggle
 * @returns {boolean} new deafen state
 */
export function toggleDeafen(force) {
    prefs.deafen = force === undefined ? !prefs.deafen : !!force;
    persist(true);
    return prefs.deafen;
}

/**
 * @param {number} percent
 * @param {boolean} [muted]
 * @param {{ notify?: boolean }} [opts]
 */
export function setOther(percent, muted, opts = {}) {
    prefs.other = {
        percent: clampPercent(percent),
        muted: muted === undefined ? prefs.other.muted : !!muted
    };
    persist(opts.notify !== false);
}

/**
 * @param {string} uuid
 * @param {number} percent
 * @param {boolean} [muted]
 * @param {{ notify?: boolean }} [opts]
 */
export function setPlayer(uuid, percent, muted, opts = {}) {
    const id = normalizeUuid(uuid);
    if (!id) {
        return;
    }
    const prev = prefs.players[id] || prefs.players[uuid] || { percent: 100, muted: false };
    prefs.players[id] = {
        percent: clampPercent(percent === undefined ? prev.percent : percent),
        muted: muted === undefined ? prev.muted : !!muted
    };
    if (uuid && uuid !== id && prefs.players[uuid]) {
        delete prefs.players[uuid];
    }
    persist(opts.notify !== false);
}

/** @param {string} uuid @param {boolean} muted */
export function setPlayerMuted(uuid, muted) {
    const id = normalizeUuid(uuid);
    if (!id) {
        setOther(prefs.other.percent, muted);
        return;
    }
    const prev = prefs.players[id] || prefs.players[uuid] || { percent: 100, muted: false };
    prefs.players[id] = { percent: prev.percent, muted: !!muted };
    if (uuid && uuid !== id && prefs.players[uuid]) {
        delete prefs.players[uuid];
    }
    persist();
}

/** @param {boolean} muted */
export function setOtherMuted(muted) {
    prefs.other = { percent: prefs.other.percent, muted: !!muted };
    persist();
}

/**
 * @param {Array<{uuid?: string, name?: string}>|null|undefined} players
 */
export function setRoster(players) {
    rosterNames.clear();
    if (!Array.isArray(players)) {
        notifyUiOnly();
        return;
    }
    for (const p of players) {
        const uuid = normalizeUuid(p?.uuid);
        if (!uuid) {
            continue;
        }
        const name = String(p?.name || "").trim();
        rosterNames.set(uuid, name || shortLabel(uuid));
        // Upgrade any heard-only placeholder once the real name arrives.
        if (name && (!heardNames.has(uuid) || isPlaceholderName(heardNames.get(uuid), uuid))) {
            heardNames.set(uuid, name);
        }
    }
    notifyUiOnly();
}

/**
 * @param {string|null|undefined} uuid
 * @param {string} [fallbackName]
 */
export function noteHeard(uuid, fallbackName) {
    const id = normalizeUuid(uuid);
    if (!id) {
        return;
    }
    const fromRoster = rosterNames.get(id);
    if (fromRoster && !isPlaceholderName(fromRoster, id)) {
        if (heardNames.get(id) !== fromRoster) {
            heardNames.set(id, fromRoster);
            notifyUiOnly();
        }
        return;
    }
    const next = String(fallbackName || "").trim() || shortLabel(id);
    const prev = heardNames.get(id);
    if (!prev || (isPlaceholderName(prev, id) && next !== prev)) {
        heardNames.set(id, next);
        notifyUiOnly();
    }
}

/**
 * Rows for the Adjust Volumes list: Overall + Other + merged players.
 * @returns {Array<{ key: string, kind: 'master'|'other'|'player', uuid: string|null, name: string }>}
 */
export function getVolumeRows() {
    /** @type {Array<{ key: string, kind: 'master'|'other'|'player', uuid: string|null, name: string }>} */
    const players = [];
    const seen = new Set();
    for (const uuid of rosterNames.keys()) {
        seen.add(uuid);
        players.push({ key: uuid, kind: "player", uuid, name: resolveName(uuid) });
    }
    for (const uuid of heardNames.keys()) {
        if (seen.has(uuid)) {
            continue;
        }
        players.push({ key: uuid, kind: "player", uuid, name: resolveName(uuid) });
    }
    players.sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: "base" }));
    // Master (Voice Chat Volume) lives in Settings; Other stays in prefs without a UI row.
    return players;
}

/** @param {() => void} listener */
export function onVolumesChange(listener) {
    changeListeners.add(listener);
    return () => changeListeners.delete(listener);
}

function notifyUiOnly() {
    for (const listener of changeListeners) {
        try {
            listener();
        } catch {
            // ignore
        }
    }
}
