import { Logger } from "./utils/logger.js";

const GROUP_TYPE_HELP = {
    ISOLATED: {
        label: "Isolated",
        text: "Only group members can hear each other. Recommended for private group chat."
    },
    NORMAL: {
        label: "Normal",
        text: "Group members retain normal proximity behavior."
    },
    OPEN: {
        label: "Open",
        text: "Nearby players outside the group may hear group members."
    }
};

/**
 * Bidirectional voice-group directory UI with accessible modals.
 */
export class GroupsController {
    /**
     * @param {object} options
     */
    constructor(options) {
        this.ws = options.webSocketController;
        this.appState = options.appState || null;
        this.onSnapshot = options.onSnapshot || null;
        this.onLoadError = options.onLoadError || null;
        this.onMembershipChange = options.onMembershipChange || null;
        this.listEl = options.listEl;
        this.currentGroupEl = options.currentGroupEl;
        this.createBtn = options.createBtn;
        this.leaveBtn = options.leaveBtn;
        this.createModal = options.createModal;
        this.createForm = options.createForm;
        this.createNameInput = options.createNameInput;
        this.createPasswordInput = options.createPasswordInput;
        this.createTypeSelect = options.createTypeSelect;
        this.createTypeHelp = options.createTypeHelp || null;
        this.createErrorEl = options.createErrorEl || null;
        this.createCloseBtn = options.createCloseBtn || null;
        this.createCancelBtn = options.createCancelBtn;
        this.createSubmitBtn = options.createSubmitBtn || null;
        this.joinModal = options.joinModal;
        this.joinForm = options.joinForm;
        this.joinPasswordInput = options.joinPasswordInput;
        this.joinGroupNameEl = options.joinGroupNameEl;
        this.joinErrorEl = options.joinErrorEl || null;
        this.joinCloseBtn = options.joinCloseBtn || null;
        this.joinCancelBtn = options.joinCancelBtn;
        this.typeHintEl = options.typeHintEl || null;
        this.errorEl = options.errorEl || null;

        /** @type {Map<string, object>} */
        this.groups = new Map();
        this.revision = 0;
        this.pendingJoinGroupId = null;
        this.pendingOps = new Map();
        this.enabled = false;
        this.lastFocus = null;
        this.activeModal = null;
        this.boundKeyHandler = (e) => this.#onKeyDown(e);
        this.currentGroupId = null;
        this.createPending = false;
        this.allowWebCreation = true;
        this.createSubmitDefaultLabel = this.createSubmitBtn?.textContent || "Create";
    }

    init() {
        this.createBtn.addEventListener("click", () => {
            if (!this.enabled) return;
            if (!this.allowWebCreation) {
                this.#setGroupsPanelError("Web group creation is disabled on this server.");
                return;
            }
            console.debug("groups:create click received");
            this.#openModal(this.createModal, this.createNameInput);
            console.debug("groups:create modal opened");
        });

        this.leaveBtn.addEventListener("click", () => {
            if (!this.enabled) return;
            this.#requestLeave();
        });

        this.createCancelBtn.addEventListener("click", (e) => {
            e.preventDefault();
            this.#closeModal(this.createModal);
        });
        this.createCloseBtn?.addEventListener("click", (e) => {
            e.preventDefault();
            this.#closeModal(this.createModal);
        });
        this.joinCancelBtn.addEventListener("click", (e) => {
            e.preventDefault();
            this.#closeModal(this.joinModal);
        });
        this.joinCloseBtn?.addEventListener("click", (e) => {
            e.preventDefault();
            this.#closeModal(this.joinModal);
        });

        this.createModal.addEventListener("click", (e) => {
            if (e.target === this.createModal) this.#closeModal(this.createModal);
        });
        this.joinModal.addEventListener("click", (e) => {
            if (e.target === this.joinModal) this.#closeModal(this.joinModal);
        });

        // Native <dialog> cancel (Escape) — honor pending-create guard.
        this.createModal.addEventListener("cancel", (e) => {
            if (this.createPending || this.pendingOps.size > 0) {
                e.preventDefault();
                return;
            }
            this.#onDialogClosed(this.createModal);
        });
        this.joinModal.addEventListener("cancel", (e) => {
            if (this.pendingOps.size > 0) {
                e.preventDefault();
                return;
            }
            this.#onDialogClosed(this.joinModal);
        });

        this.createForm.addEventListener("submit", (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (!this.enabled || this.createPending) return;

            const name = this.createNameInput.value.trim();
            if (!name) {
                this.#setCreateError("Group name is required.");
                this.createNameInput.focus();
                return;
            }
            if (name.length > 32) {
                this.#setCreateError("Group name is too long.");
                return;
            }

            const password = this.createPasswordInput.value;
            const type = (this.createTypeSelect.value || "ISOLATED").toUpperCase();
            this.#setCreateError("");
            console.debug(
                `groups:create submitted nameLength=${name.length} protected=${password.length > 0} type=${type}`
            );
            this.#requestCreate(name, password.length > 0 ? password : null, type);
        });

        this.joinForm.addEventListener("submit", (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (!this.enabled || !this.pendingJoinGroupId || this.pendingOps.size > 0) return;
            const password = this.joinPasswordInput.value;
            const groupId = this.pendingJoinGroupId;
            this.#setJoinError("");
            this.#requestJoin(groupId, password.length > 0 ? password : null);
        });

        this.createTypeSelect?.addEventListener("change", () => this.#updateTypeHelp());

        this.ws.onGroupMessage((data) => this.#handleServerMessage(data));
        this.ws.onOperationResult((result) => this.#handleOperationResult(result));

        if (this.createTypeSelect) {
            if (!this.createTypeSelect.value) {
                this.createTypeSelect.value = "ISOLATED";
            }
            this.#updateTypeHelp();
        }

        // Ensure dialogs start closed (no static open attribute, no leftover open state).
        this.#forceCloseDialog(this.createModal);
        this.#forceCloseDialog(this.joinModal);

        this.#render();
        this.setEnabled(false);
    }

    /**
     * Enable/disable group controls (READY gate).
     * @param {boolean} enabled
     */
    setEnabled(enabled) {
        this.enabled = !!enabled;
        this.#syncCreateButtonState();
        this.leaveBtn.disabled = !enabled || !this.currentGroupId;
        this.listEl.setAttribute("aria-disabled", enabled ? "false" : "true");
        this.#render();
    }

    /**
     * @param {boolean} allowed
     */
    setAllowWebCreation(allowed) {
        const next = allowed !== false;
        const changed = next !== this.allowWebCreation;
        this.allowWebCreation = next;
        this.#syncCreateButtonState();
        if (!this.allowWebCreation) {
            this.#setGroupsPanelError("Web group creation is disabled on this server.");
        } else if (changed) {
            this.#setGroupsPanelError("");
        }
    }

    #syncCreateButtonState() {
        const canCreate = this.enabled && this.allowWebCreation;
        this.createBtn.disabled = !canCreate;
        this.createBtn.title = this.allowWebCreation
            ? ""
            : "Web group creation is disabled on this server.";
    }

    #setGroupsPanelError(message) {
        if (!this.errorEl) return;
        const text = String(message || "");
        this.errorEl.textContent = text;
        this.errorEl.hidden = !text;
    }

    /**
     * @returns {string|null}
     */
    getCurrentGroupId() {
        return this.currentGroupId;
    }

    /**
     * @returns {boolean}
     */
    isInGroup() {
        return !!this.currentGroupId;
    }

    /**
     * Clear directory on logout / disconnect.
     */
    reset() {
        this.groups.clear();
        this.revision = 0;
        this.pendingOps.clear();
        this.pendingJoinGroupId = null;
        this.currentGroupId = null;
        this.createPending = false;
        this.createPasswordInput.value = "";
        this.joinPasswordInput.value = "";
        this.#setCreateError("");
        this.#setJoinError("");
        this.#setCreateFormBusy(false);
        this.#forceCloseDialog(this.createModal);
        this.#forceCloseDialog(this.joinModal);
        this.setEnabled(false);
        this.#updateCurrentGroup();
        this.#notifyMembership();
        this.#render();
    }

    /**
     * Apply a groups_snapshot payload (used for live updates and buffered pre-ready snapshots).
     * @param {object} data
     * @param {{idempotent?: boolean}} [options]
     */
    applySnapshot(data, options = {}) {
        if (!data || typeof data !== "object") {
            return false;
        }
        if (Object.prototype.hasOwnProperty.call(data, "allowWebCreation")) {
            this.setAllowWebCreation(data.allowWebCreation !== false);
        }
        const revision = Number(data.revision);
        if (Number.isFinite(revision)) {
            if (revision < this.revision) {
                console.debug(`groups: snapshot revision=${revision} ignored (stale; local=${this.revision})`);
                return false;
            }
            if (revision === this.revision && !options.idempotent && this.groups.size > 0) {
                const list = Array.isArray(data.groups) ? data.groups : [];
                if (list.length === this.groups.size) {
                    return false;
                }
            }
            this.revision = revision;
        }

        this.groups.clear();
        const list = Array.isArray(data.groups) ? data.groups : [];
        for (const g of list) {
            if (g?.uuid) {
                this.groups.set(String(g.uuid), this.#normalize(g));
            }
        }

        let current = "none";
        for (const g of this.groups.values()) {
            if (g.joined) {
                current = g.uuid;
                break;
            }
        }
        console.debug(`groups:reconcile revision=${this.revision} groups=${this.groups.size} current=${current}`);
        this.#updateCurrentGroup();
        this.#render();
        console.debug(`groups:render revision=${this.revision} groups=${this.groups.size}`);
        this.onSnapshot?.(this.revision, this.groups.size);
        return true;
    }

    #handleServerMessage(data) {
        const type = String(data?.type || "").toLowerCase();
        const revision = Number(data?.revision);

        if (type === "groups_snapshot") {
            if (this.appState && !this.appState.isDashboardVisible()) {
                this.appState.bufferGroupSnapshot(data);
                return;
            }
            try {
                this.applySnapshot(data);
            } catch (error) {
                console.error(error);
                this.onLoadError?.("Could not render voice groups.");
            }
            return;
        }

        if (Number.isFinite(revision) && revision < this.revision) {
            return;
        }
        if (Number.isFinite(revision) && revision > this.revision) {
            this.revision = revision;
        }

        if (type === "group_created") {
            this.ws.subscribeGroups();
            return;
        }

        if (type === "group_removed") {
            const id = String(data.groupId || "");
            if (id) this.groups.delete(id);
            this.#updateCurrentGroup();
            this.#render();
            return;
        }

        if (type === "membership_changed") {
            this.ws.subscribeGroups();
        }
    }

    #handleOperationResult(result) {
        const opId = result?.operationId;
        const pending = opId ? this.pendingOps.get(opId) : null;
        if (pending) {
            this.pendingOps.delete(opId);
        }

        if (Number.isFinite(result?.revision) && result.revision > this.revision) {
            this.revision = result.revision;
        }

        const kind = pending?.kind;
        if (kind === "create") {
            this.createPending = false;
            this.#setCreateFormBusy(false);
            // Always clear password after a create attempt (success or failure). Never log it.
            this.createPasswordInput.value = "";
        }

        if (result?.success) {
            console.debug(`groups:create success operationId=${opId || "?"}`);
            Logger.log(pending?.successMessage || "Group operation succeeded.");
            if (kind === "create") {
                this.createNameInput.value = "";
                this.#setCreateError("");
                this.#closeModal(this.createModal, { force: true });
            }
            if (kind === "join") {
                this.joinPasswordInput.value = "";
                this.pendingJoinGroupId = null;
                this.#setJoinError("");
                this.#closeModal(this.joinModal, { force: true });
            }
            this.ws.subscribeGroups();
        } else {
            const err = result?.error || "Group operation failed.";
            const category = this.#errorCategory(err);
            if (kind === "create") {
                console.debug(
                    `groups:create validation_failed operationId=${opId || "?"} reason=${category}`
                );
                this.#setCreateError(err);
            } else if (kind === "join") {
                this.#setJoinError(err);
                console.debug(`groups: operation=${opId || "?"} result=error`);
            } else {
                console.debug(`groups: operation=${opId || "?"} result=error`);
            }
            Logger.log(err);
            this.onLoadError?.(err);
            // Leave dialog open on failure.
        }

        if (kind === "join" && !result?.success) {
            this.joinPasswordInput.value = "";
        }

        this.#render();
    }

    #errorCategory(message) {
        const m = String(message || "").toLowerCase();
        if (m.includes("permission")) return "permission";
        if (m.includes("disabled")) return "disabled";
        if (m.includes("cooldown") || m.includes("wait")) return "cooldown";
        if (m.includes("limit") || m.includes("maximum") || m.includes("too many")) return "limit";
        if (m.includes("name")) return "name";
        if (m.includes("type")) return "type";
        if (m.includes("join failed")) return "assign";
        if (m.includes("exist")) return "exists";
        return "server";
    }

    #normalize(g) {
        return {
            uuid: String(g.uuid),
            name: String(g.name || "Unnamed"),
            type: String(g.type || "NORMAL").toUpperCase(),
            hasPassword: !!g.hasPassword,
            persistent: !!g.persistent,
            memberCount: Number(g.memberCount) || 0,
            joined: !!g.joined
        };
    }

    #requestJoin(groupId, password) {
        const operationId = this.#nextOpId("join");
        this.pendingOps.set(operationId, { kind: "join", successMessage: "Joined group." });
        this.#render();
        this.ws.sendGroupJoin(groupId, password, operationId);
    }

    #requestLeave() {
        const operationId = this.#nextOpId("leave");
        this.pendingOps.set(operationId, { kind: "leave", successMessage: "Left group." });
        this.currentGroupId = null;
        this.#notifyMembership();
        this.ws.sendGroupLeave(operationId);
        this.#render();
    }

    #requestCreate(name, password, type) {
        if (!this.allowWebCreation) {
            this.#setCreateError("Web group creation is disabled on this server.");
            return;
        }

        const operationId = this.#nextOpId("create");
        this.createPending = true;
        this.pendingOps.set(operationId, {
            kind: "create",
            successMessage: `Created group "${name}".`
        });
        this.#setCreateFormBusy(true);
        console.debug(
            `groups:create submit operationId=${operationId} nameLength=${name.length} `
            + `protected=${!!password} type=${type}`
        );
        const sent = this.ws.sendGroupCreate(name, password, type, operationId);
        if (!sent) {
            this.pendingOps.delete(operationId);
            this.createPending = false;
            this.#setCreateFormBusy(false);
            this.#setCreateError("Session is not ready. Wait for connection, then try again.");
            console.debug(`groups:create blocked_not_ready operationId=${operationId}`);
            this.#render();
            return;
        }
        console.debug(`groups:create sent operationId=${operationId}`);
        this.#render();
    }

    #nextOpId(prefix) {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    }

    #updateCurrentGroup() {
        let current = null;
        for (const g of this.groups.values()) {
            if (g.joined) {
                current = g;
                break;
            }
        }
        const nextId = current ? current.uuid : null;
        const changed = nextId !== this.currentGroupId;
        this.currentGroupId = nextId;
        this.currentGroupEl.textContent = current ? current.name : "None";
        this.leaveBtn.disabled = !this.enabled || !current;

        console.debug(
            `groups: membership group=${nextId || "none"} revision=${this.revision}`
        );

        if (changed) {
            this.#notifyMembership();
        }
    }

    #notifyMembership() {
        this.onMembershipChange?.(this.currentGroupId);
        this.ws?.setInGroup?.(!!this.currentGroupId);
    }

    #updateTypeHelp() {
        if (!this.createTypeHelp || !this.createTypeSelect) return;
        const key = (this.createTypeSelect.value || "ISOLATED").toUpperCase();
        const help = GROUP_TYPE_HELP[key] || GROUP_TYPE_HELP.ISOLATED;
        this.createTypeHelp.innerHTML = `<strong>${help.label}:</strong> ${help.text}`;
    }

    #setCreateError(message) {
        if (!this.createErrorEl) return;
        const text = String(message || "");
        this.createErrorEl.textContent = text;
        this.createErrorEl.hidden = !text;
    }

    #setJoinError(message) {
        if (!this.joinErrorEl) return;
        const text = String(message || "");
        this.joinErrorEl.textContent = text;
        this.joinErrorEl.hidden = !text;
    }

    #setCreateFormBusy(busy) {
        const disabled = !!busy;
        this.createNameInput.disabled = disabled;
        this.createPasswordInput.disabled = disabled;
        this.createTypeSelect.disabled = disabled;
        // Keep cancel/close usable so users can recover from a hung create attempt
        // once we clear createPending; while pending, close is still guarded.
        this.createCancelBtn.disabled = false;
        if (this.createCloseBtn) this.createCloseBtn.disabled = false;
        const submit = this.createSubmitBtn
            || this.createForm.querySelector('button[type="submit"]');
        if (submit) {
            submit.disabled = disabled;
            submit.textContent = disabled ? "Creating..." : this.createSubmitDefaultLabel;
        }
    }

    #render() {
        this.#updateCurrentGroup();
        this.listEl.innerHTML = "";

        if (!this.enabled) {
            const hint = document.createElement("p");
            hint.className = "groups-empty";
            hint.textContent = "Connect to load voice groups.";
            this.listEl.appendChild(hint);
            return;
        }

        if (this.groups.size === 0) {
            const hint = document.createElement("p");
            hint.className = "groups-empty";
            hint.textContent = "No voice groups are currently available. Create one to get started.";
            this.listEl.appendChild(hint);
            return;
        }

        const sorted = [...this.groups.values()].sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: "base" })
        );

        for (const group of sorted) {
            this.listEl.appendChild(this.#buildRow(group));
        }
    }

    #buildRow(group) {
        const row = document.createElement("article");
        row.className = "group-card group-row" + (group.joined ? " is-joined" : "");
        row.dataset.groupId = group.uuid;
        row.setAttribute("role", "listitem");

        const main = document.createElement("div");
        main.className = "group-row-main";

        const title = document.createElement("h3");
        title.className = "group-name";
        title.textContent = group.name;
        if (group.hasPassword) {
            const lock = document.createElement("span");
            lock.className = "group-lock";
            lock.title = "Password required";
            lock.setAttribute("aria-label", "Password required");
            lock.textContent = "· locked";
            title.appendChild(document.createTextNode(" "));
            title.appendChild(lock);
        }

        const meta = document.createElement("p");
        meta.className = "group-meta";
        meta.textContent = `${group.type} · ${group.memberCount} member${group.memberCount === 1 ? "" : "s"}`;
        if (group.joined) {
            const badge = document.createElement("span");
            badge.className = "group-joined-badge";
            badge.textContent = "Joined";
            meta.appendChild(document.createTextNode(" · "));
            meta.appendChild(badge);
        }

        main.appendChild(title);
        main.appendChild(meta);

        const actions = document.createElement("div");
        actions.className = "group-row-actions";

        const joinBtn = document.createElement("button");
        joinBtn.type = "button";
        joinBtn.className = "btn btn-secondary";
        joinBtn.textContent = group.joined ? "Joined" : "Join";
        joinBtn.disabled = !this.enabled || group.joined || this.pendingOps.size > 0;
        joinBtn.addEventListener("click", () => {
            if (group.hasPassword) {
                this.pendingJoinGroupId = group.uuid;
                this.joinGroupNameEl.textContent = group.name;
                this.joinPasswordInput.value = "";
                this.#openModal(this.joinModal, this.joinPasswordInput);
            } else {
                this.#requestJoin(group.uuid, null);
            }
        });

        actions.appendChild(joinBtn);
        row.appendChild(main);
        row.appendChild(actions);
        return row;
    }

    #isDialog(el) {
        return typeof HTMLDialogElement !== "undefined" && el instanceof HTMLDialogElement;
    }

    #forceCloseDialog(modal) {
        if (!modal) return;
        if (this.#isDialog(modal)) {
            if (modal.open) {
                try {
                    modal.close();
                } catch {
                    modal.removeAttribute("open");
                }
            }
            modal.removeAttribute("open");
        } else {
            modal.hidden = true;
            modal.setAttribute("aria-hidden", "true");
        }
    }

    #openModal(modal, focusEl) {
        // Opening one dialog must close the other. Never alter primary views.
        if (modal === this.createModal) {
            this.#forceCloseDialog(this.joinModal);
            this.#setCreateError("");
            this.#updateTypeHelp();
        } else if (modal === this.joinModal) {
            this.#forceCloseDialog(this.createModal);
            this.#setJoinError("");
        }

        this.lastFocus = document.activeElement;
        this.activeModal = modal;

        if (!this.#isDialog(modal)) {
            console.error("SVG groups: expected HTMLDialogElement for modal");
            return;
        }

        if (!modal.open) {
            try {
                modal.showModal();
            } catch (error) {
                console.error("SVG groups: showModal failed", error);
                return;
            }
        }

        document.addEventListener("keydown", this.boundKeyHandler);
        const focusable = this.#focusable(modal);
        queueMicrotask(() => {
            (focusEl || focusable[0])?.focus();
        });
    }

    #closeModal(modal, options = {}) {
        const force = !!options.force;
        if (!force && (this.createPending || this.pendingOps.size > 0) && modal === this.createModal) {
            return;
        }
        if (!force && this.pendingOps.size > 0 && modal === this.joinModal) {
            return;
        }

        this.#forceCloseDialog(modal);

        if (modal === this.joinModal) {
            this.joinPasswordInput.value = "";
            this.pendingJoinGroupId = null;
            this.#setJoinError("");
        }

        this.#onDialogClosed(modal);
    }

    #onDialogClosed(modal) {
        if (this.activeModal === modal) {
            this.activeModal = null;
            document.removeEventListener("keydown", this.boundKeyHandler);
            const returnTo = this.lastFocus || this.createBtn;
            this.lastFocus = null;
            queueMicrotask(() => {
                try {
                    returnTo?.focus?.();
                } catch {
                    // ignore
                }
            });
        }
    }

    #onKeyDown(e) {
        if (!this.activeModal) return;
        if (e.key === "Escape") {
            if (this.createPending || this.pendingOps.size > 0) {
                e.preventDefault();
                return;
            }
            e.preventDefault();
            this.#closeModal(this.activeModal);
            return;
        }
        if (e.key !== "Tab") return;

        const nodes = this.#focusable(this.activeModal);
        if (nodes.length === 0) return;
        const first = nodes[0];
        const last = nodes[nodes.length - 1];
        if (e.shiftKey && document.activeElement === first) {
            e.preventDefault();
            last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
            e.preventDefault();
            first.focus();
        }
    }

    #focusable(root) {
        return [...root.querySelectorAll(
            'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )];
    }
}

/**
 * Build a group_create websocket payload (shared with tests).
 * @param {string} name
 * @param {string|null} password
 * @param {string} groupType
 * @param {string} operationId
 * @returns {object}
 */
export function buildGroupCreatePayload(name, password, groupType, operationId) {
    const trimmed = String(name ?? "").trim();
    const type = String(groupType || "ISOLATED").toUpperCase();
    return {
        type: "group_create",
        operationId,
        name: trimmed,
        password: password != null && password !== "" ? password : null,
        groupType: type
    };
}
