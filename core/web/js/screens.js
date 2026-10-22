/**
 * SVC-style screen router for the connected UI (hub / settings / volumes / devices).
 * Also opens mic/speaker lists from the join form before connect.
 */
export class ScreenRouter {
    /**
     * @param {HTMLElement} root element with data-active-screen
     */
    constructor(root) {
        this.root = root;
        this.stack = ["hub"];
    }

    /** @returns {string} */
    current() {
        return this.stack[this.stack.length - 1] || "hub";
    }

    /**
     * @param {string} name
     * @param {{ push?: boolean }} [opts]
     */
    show(name, opts = {}) {
        const push = opts.push !== false;
        if (!name) {
            return;
        }
        if (push) {
            if (this.current() !== name) {
                this.stack.push(name);
            }
        } else {
            this.stack[this.stack.length - 1] = name;
        }
        this.root.dataset.activeScreen = name;
        this.root.querySelectorAll("[data-screen]").forEach((panel) => {
            const active = panel.getAttribute("data-screen") === name;
            panel.classList.toggle("svcScreenActive", active);
            panel.setAttribute("aria-hidden", active ? "false" : "true");
        });
    }

    /**
     * Open mic/speaker picker from the join screen (before connected).
     * @param {"mic"|"speaker"} name
     */
    openJoinDevice(name) {
        document.body.classList.add("join-device-picker");
        this.stack = ["hub"];
        this.show(name);
    }

    /** Leave join-device-picker mode and return to the login form. */
    closeJoinDevicePicker() {
        document.body.classList.remove("join-device-picker");
        this.stack = ["hub"];
        this.root.dataset.activeScreen = "hub";
        this.root.querySelectorAll("[data-screen]").forEach((panel) => {
            const active = panel.getAttribute("data-screen") === "hub";
            panel.classList.toggle("svcScreenActive", active);
            panel.setAttribute("aria-hidden", active ? "false" : "true");
        });
    }

    back() {
        if (this.stack.length > 1) {
            this.stack.pop();
        }
        const next = this.current();
        if (
            !document.body.classList.contains("connected")
            && document.body.classList.contains("join-device-picker")
            && next === "hub"
        ) {
            this.closeJoinDevicePicker();
            return;
        }
        this.show(next, { push: false });
    }

    reset() {
        document.body.classList.remove("join-device-picker");
        this.stack = ["hub"];
        this.show("hub", { push: false });
    }

    /**
     * Wire open/back buttons inside root.
     * @param {{
     *   openSettings?: HTMLElement|null,
     *   openGroups?: HTMLElement|null,
     *   openCreateGroup?: HTMLElement|null,
     *   hubVolumesIcon?: HTMLElement|null,
     *   openMic?: HTMLElement|null,
     *   openSpeaker?: HTMLElement|null,
     *   joinOpenMic?: HTMLElement|null,
     *   joinOpenSpeaker?: HTMLElement|null
     * }} buttons
     */
    bind(buttons = {}) {
        buttons.openSettings?.addEventListener("click", () => this.show("settings"));
        buttons.openGroups?.addEventListener("click", () => this.show("groups"));
        buttons.openCreateGroup?.addEventListener("click", () => this.show("create-group"));
        buttons.hubVolumesIcon?.addEventListener("click", () => this.show("volumes"));
        buttons.openMic?.addEventListener("click", () => this.show("mic"));
        buttons.openSpeaker?.addEventListener("click", () => this.show("speaker"));
        buttons.joinOpenMic?.addEventListener("click", () => this.openJoinDevice("mic"));
        buttons.joinOpenSpeaker?.addEventListener("click", () => this.openJoinDevice("speaker"));

        this.root.querySelectorAll("[data-screen-back]").forEach((btn) => {
            btn.addEventListener("click", () => this.back());
        });

        this.reset();
    }
}
