import {SvgLang} from "./utils/lang.js";
import {
    getPrefs,
    getVolumeRows,
    onVolumesChange,
    setMaster,
    setOther,
    setPlayer
} from "./audio/receive-volumes.js";

const DEFAULT_AVATAR = "images/v2/mic.png";
const AVATAR_SIZE = 48;

/**
 * Format amplification like SVC Java: 100% → "0%", 105% → "+5%", 80% → "-20%".
 * @param {number} percent
 * @returns {string}
 */
function formatAmplification(percent) {
    const delta = Math.round(percent) - 100;
    if (delta > 0) {
        return `+${delta}%`;
    }
    return `${delta}%`;
}

/**
 * SVC-style Adjust Volumes panel (per-player only; master is in Settings).
 */
export class AdjustVolumesUI {
    /**
     * @param {{
     *   root: HTMLElement,
     *   listEl: HTMLElement,
     *   searchInput: HTMLInputElement
     * }} els
     */
    constructor(els) {
        this.root = els.root;
        this.listEl = els.listEl;
        this.searchInput = els.searchInput;
        this._search = "";
        this._unsub = null;
    }

    init() {
        this.searchInput.addEventListener("input", () => {
            this._search = String(this.searchInput.value || "").trim().toLowerCase();
            this.render();
        });
        this._unsub = onVolumesChange(() => this.render());
        this.render();
    }

    destroy() {
        if (this._unsub) {
            this._unsub();
            this._unsub = null;
        }
    }

    render() {
        const prefs = getPrefs();
        const rows = getVolumeRows().filter((row) => {
            if (row.kind !== "player") {
                return false;
            }
            if (!this._search) {
                return true;
            }
            return row.name.toLowerCase().includes(this._search);
        });

        this.listEl.replaceChildren();

        if (rows.length === 0) {
            const empty = document.createElement("div");
            empty.className = "svcListRow";
            SvgLang.setElement(empty, "volumesEmptyLabel");
            this.listEl.appendChild(empty);
            return;
        }

        for (const row of rows) {
            this.listEl.appendChild(this.#buildRow(row, prefs));
        }
    }

    /**
     * @param {{ key: string, kind: string, uuid: string|null, name: string }} row
     * @param {import("./audio/receive-volumes.js").VolumePrefs|ReturnType<typeof getPrefs>} prefs
     */
    #buildRow(row, prefs) {
        const entry = row.kind === "master"
            ? { percent: prefs.master, muted: false }
            : row.kind === "other"
                ? prefs.other
                : (prefs.players[row.uuid] || { percent: 100, muted: false });

        // Legacy mute flag: show as 0% (SVC has no mute button; slider to 0).
        const percent = entry.muted ? 0 : entry.percent;

        const rowEl = document.createElement("div");
        rowEl.className = "volumeRow fullWidth";
        rowEl.dataset.volumeKey = row.key;

        const avatar = document.createElement("img");
        avatar.className = "volumeAvatar";
        avatar.alt = "";
        avatar.width = AVATAR_SIZE;
        avatar.height = AVATAR_SIZE;
        if (row.kind === "player" && row.uuid) {
            avatar.src = `https://mc-heads.net/avatar/${row.uuid}/${AVATAR_SIZE}`;
            avatar.onerror = () => {
                avatar.onerror = null;
                avatar.src = DEFAULT_AVATAR;
            };
        } else if (row.kind === "other") {
            avatar.src = DEFAULT_AVATAR;
            avatar.classList.add("volumeAvatarOther");
        } else {
            avatar.src = DEFAULT_AVATAR;
            avatar.classList.add("volumeAvatarMaster");
        }
        rowEl.appendChild(avatar);

        const bar = document.createElement("div");
        bar.className = "volumeBar";

        const nameEl = document.createElement("span");
        nameEl.className = "mcTextStyle2 volumeBarName";
        if (row.kind === "master") {
            SvgLang.setElement(nameEl, "volumeOverallLabel");
        } else if (row.kind === "other") {
            SvgLang.setElement(nameEl, "volumeOtherLabel");
        } else {
            nameEl.textContent = row.name;
        }
        bar.appendChild(nameEl);

        const ampLabel = document.createElement("span");
        ampLabel.className = "mcTextStyle1 volumeBarAmp";
        const updateAmpLabel = (p) => {
            SvgLang.setElement(ampLabel, () =>
                `${SvgLang.string("volumeAmplificationPrefix")}${formatAmplification(p)}`
            );
        };
        updateAmpLabel(percent);
        bar.appendChild(ampLabel);

        const slider = document.createElement("input");
        slider.type = "range";
        slider.className = "volumeSlider svcMcSlider";
        slider.min = "0";
        slider.max = "200";
        slider.value = String(percent);
        slider.setAttribute(
            "aria-label",
            row.kind === "master"
                ? "Overall volume"
                : row.kind === "other"
                    ? "Other volume"
                    : `Volume ${row.name}`
        );
        slider.addEventListener("input", () => {
            const value = Number(slider.value) || 0;
            updateAmpLabel(value);
            const silent = { notify: false };
            if (row.kind === "master") {
                setMaster(value, silent);
            } else if (row.kind === "other") {
                setOther(value, false, silent);
            } else {
                setPlayer(row.uuid, value, false, silent);
            }
        });
        bar.appendChild(slider);
        rowEl.appendChild(bar);

        return rowEl;
    }
}
