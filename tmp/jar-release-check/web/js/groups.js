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
        this.refreshBtn = options.refreshBtn || null;
        this.refreshStatusEl = options.refreshStatusEl || null;
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
        this.leavePending = false;
        /** @type {"NO_GROUP"|"CREATING"|"GROUP_CREATED"|"JOINING"|"JOINING_CREATED_GROUP"|"IN_GROUP"|"LEAVING"} */
        this.membershipState = "NO_GROUP";
        this.lastMembershipIdentity = "";
        this.allowWebCreation = true;
        this.createSubmitDefaultLabel = this.createSubmitBtn?.textContent || "Create";
        /** @type {Set<string>} */
        this.expandedGroupIds = new Set();
        this.refreshPending = false;
        this.refreshRetryTimer = null;
        this.rateLimitTimers = new Map();
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
            if (!this.enabled || this.leavePending || this.pendingOps.size > 0) return;
            this.#requestLeave();
        });

        this.refreshBtn?.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            if (!this.enabled || this.refreshPending) return;
            this.#requestRefresh();
        });
        // Delegated fallback so list rerenders / drag handlers cannot orphan the control.
        this.listEl?.parentElement?.addEventListener("click", (event) => {
            const target = event.target;
            if (!(target instanceof Element)) return;
            const btn = target.closest('[data-svg="groups.refresh"]');
            if (!btn || btn !== this.refreshBtn) return;
            event.preventDefault();
            event.stopPropagation();
            if (!this.enabled || this.refreshPending) return;
            this.#requestRefresh();
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
        if (this.refreshBtn) {
            this.refreshBtn.disabled = !enabled || this.refreshPending;
        }
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
        this.leavePending = false;
        this.membershipState = "NO_GROUP";
        this.refreshPending = false;
        this.expandedGroupIds.clear();
        this.#clearRateLimitTimers();
        this.#setRefreshStatus("");
        this.createPasswordInput.value = "";
        this.joinPasswordInput.value = "";
        this.#setCreateError("");
        this.#setJoinError("");
        this.#setCreateFormBusy(false);
        if (this.leaveBtn?.dataset?.defaultLabel) {
            this.leaveBtn.textContent = this.leaveBtn.dataset.defaultLabel;
        }
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
        const list = Array.isArray(data.groups) ? data.groups : [];
        let snapshotCurrent = null;
        for (const g of list) {
            if (g?.joined) {
                snapshotCurrent = String(g.uuid || "");
                break;
            }
        }
        const membershipIdentity = `${snapshotCurrent || "none"}|${list.length}|${list.map((g) =>
            `${g?.uuid}:${g?.memberCount || 0}:${g?.joined ? 1 : 0}`
        ).join(",")}`;

        if (Number.isFinite(revision)) {
            if (revision < this.revision) {
                console.debug(`groups: snapshot revision=${revision} ignored (stale; local=${this.revision})`);
                return false;
            }
            // Same revision + same directory size is NOT enough: membership can change
            // without list-length changes (creator join, leave, member swap).
            if (
                revision === this.revision
                && !options.idempotent
                && this.groups.size > 0
                && membershipIdentity === this.lastMembershipIdentity
            ) {
                return false;
            }
            this.revision = revision;
        }
        this.lastMembershipIdentity = membershipIdentity;

        // Preserve a confirmed local join when a racing incomplete snapshot arrives
        // without the joined flag (create→refresh race).
        const preserveJoinedId =
            (this.membershipState === "IN_GROUP" || this.membershipState === "JOINING_CREATED_GROUP")
            && this.currentGroupId
            && !snapshotCurrent
                ? this.currentGroupId
                : null;

        this.groups.clear();
        for (const g of list) {
            if (g?.uuid) {
                const normalized = this.#normalize(g);
                if (preserveJoinedId && normalized.uuid === preserveJoinedId) {
                    normalized.joined = true;
                }
                this.groups.set(String(g.uuid), normalized);
            }
        }
        if (preserveJoinedId && !this.groups.has(preserveJoinedId)) {
            this.groups.set(preserveJoinedId, this.#normalize({
                uuid: preserveJoinedId,
                name: "Group",
                type: "ISOLATED",
                joined: true,
                memberCount: 1,
                members: []
            }));
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
            // Directory fact only — does not set membership by itself.
            const id = String(data.groupId || "");
            const name = String(data.name || "Unnamed");
            if (id) {
                const existing = this.groups.get(id);
                this.groups.set(id, this.#normalize({
                    uuid: id,
                    name,
                    type: existing?.type || data.groupType || data.type || "NORMAL",
                    hasPassword: existing?.hasPassword || !!data.hasPassword || false,
                    passwordProtected: existing?.passwordProtected || !!data.passwordProtected || false,
                    memberCount: existing?.memberCount || Number(data.memberCount) || 0,
                    joined: existing?.joined || false,
                    members: existing?.members || []
                }));
                if (this.membershipState === "CREATING") {
                    this.membershipState = "GROUP_CREATED";
                }
                this.#updateCurrentGroup();
                this.#render();
            }
            this.#requestRefresh({ silent: true });
            return;
        }

        if (type === "group_removed") {
            const id = String(data.groupId || "");
            if (id) this.groups.delete(id);
            this.#updateCurrentGroup();
            this.#render();
            return;
        }

        if (type === "membership_changed" || type === "group_joined") {
            const playerId = data.playerId != null ? String(data.playerId) : "";
            const groupId = data.groupId != null ? String(data.groupId) : "";
            const joined = data.joined === true || type === "group_joined";
            // Apply local membership when the event is about this session or lacks player scope.
            if (!playerId || playerId === "self" || data.you === true || data.self === true) {
                if (joined && groupId) {
                    this.#applyJoinedFromResult(groupId, revision);
                    this.#render();
                } else if (!joined) {
                    this.#applyLeftFromResult({ previousGroupId: groupId || this.currentGroupId });
                    this.#render();
                }
            }
            this.#requestRefresh({ silent: true });
        }
    }

    #handleOperationResult(result) {
        const opId = result?.operationId;
        const pending = opId ? this.pendingOps.get(opId) : null;
        if (pending) {
            this.pendingOps.delete(opId);
        }

        if (result?.errorCode === "RATE_LIMITED") {
            this.#handleRateLimited(result, pending?.kind);
            if (pending?.kind === "refresh") {
                this.refreshPending = false;
                this.#syncRefreshButton();
            }
            this.#render();
            return;
        }

        if (Number.isFinite(result?.revision) && result.revision > this.revision) {
            this.revision = result.revision;
        }

        const kind = pending?.kind;
        if (kind === "refresh") {
            this.refreshPending = false;
            if (this.refreshRetryTimer) {
                clearTimeout(this.refreshRetryTimer);
                this.refreshRetryTimer = null;
            }
            this.#syncRefreshButton();
            if (result?.success) {
                this.#setRefreshStatus("Updated");
            }
        }
        if (kind === "create") {
            this.createPending = false;
            this.#setCreateFormBusy(false);
            // Always clear password after a create attempt (success or failure). Never log it.
            this.createPasswordInput.value = "";
        }
        if (kind === "leave") {
            this.leavePending = false;
            this.leaveBtn.textContent = this.leaveBtn.dataset.defaultLabel || "Leave Group";
        }

        if (result?.success) {
            console.debug(`groups: operation success operationId=${opId || "?"} kind=${kind || "?"}`);
            Logger.log(pending?.successMessage || "Group operation succeeded.");
            if (kind === "create") {
                this.createNameInput.value = "";
                this.#setCreateError("");
                this.#closeModal(this.createModal, { force: true });
                if (result.joined === true && result.groupId) {
                    this.#applyJoinedFromResult(result.groupId, result.revision);
                    this.#render();
                } else if (result.created === true && result.groupId) {
                    this.membershipState = "GROUP_CREATED";
                }
            }
            if (kind === "join") {
                this.joinPasswordInput.value = "";
                this.pendingJoinGroupId = null;
                this.#setJoinError("");
                this.#closeModal(this.joinModal, { force: true });
                if (result.joined === true && (result.groupId || result.currentGroupId)) {
                    this.#applyJoinedFromResult(result.groupId || result.currentGroupId, result.revision);
                    this.#render();
                }
            }
            if (kind === "leave") {
                if (result.left === true || result.currentGroupId == null) {
                    this.#applyLeftFromResult(result);
                    this.#render();
                }
            }
            if (kind === "create" || kind === "join" || kind === "leave") {
                this.#requestRefresh({ silent: true });
            }
        } else {
            const err = result?.error || result?.message || "Group operation failed.";
            const category = this.#errorCategory(err);
            if (kind === "create") {
                console.debug(
                    `groups:create validation_failed operationId=${opId || "?"} reason=${category}`
                );
                this.#setCreateError(err);
                // Partial create: group exists but creator not joined — keep visible via refresh.
                if (result?.partial && result.groupId) {
                    this.membershipState = "GROUP_CREATED";
                    const id = String(result.groupId);
                    const existing = this.groups.get(id);
                    this.groups.set(id, this.#normalize({
                        ...(existing || {}),
                        uuid: id,
                        name: existing?.name || this.createNameInput.value || "Group",
                        joined: false
                    }));
                    this.#requestRefresh({ silent: true });
                    this.#render();
                } else if (this.membershipState === "CREATING" || this.membershipState === "JOINING_CREATED_GROUP") {
                    this.membershipState = this.currentGroupId ? "IN_GROUP" : "NO_GROUP";
                }
            } else if (kind === "join") {
                this.#setJoinError(err);
                console.debug(`groups: operation=${opId || "?"} result=error`);
                this.membershipState = this.currentGroupId ? "IN_GROUP" : "NO_GROUP";
            } else if (kind === "leave") {
                // Leave failed — restore from authoritative snapshot.
                this.membershipState = this.currentGroupId ? "IN_GROUP" : "NO_GROUP";
                this.#requestRefresh({ silent: true });
                this.onLoadError?.(err);
            } else if (kind === "refresh") {
                this.#setRefreshStatus("Error");
            } else {
                console.debug(`groups: operation=${opId || "?"} result=error`);
            }
            Logger.log(err);
            if (kind !== "refresh" && kind !== "leave") {
                this.onLoadError?.(err);
            }
        }

        if (kind === "join" && !result?.success) {
            this.joinPasswordInput.value = "";
        }

        this.#render();
    }

    #applyJoinedFromResult(groupId, revision) {
        const id = String(groupId);
        const existing = this.groups.get(id) || {
            uuid: id,
            name: "Group",
            type: "ISOLATED",
            hasPassword: false,
            passwordProtected: false,
            memberCount: 1,
            members: []
        };
        this.groups.set(id, this.#normalize({
            ...existing,
            uuid: id,
            joined: true,
            memberCount: Math.max(1, Number(existing.memberCount) || 1)
        }));
        if (Number.isFinite(revision) && revision > this.revision) {
            this.revision = revision;
        }
        this.currentGroupId = id;
        this.membershipState = "IN_GROUP";
        this.#notifyMembership();
    }

    #applyLeftFromResult(result) {
        const previous = result?.previousGroupId ? String(result.previousGroupId) : this.currentGroupId;
        if (previous && this.groups.has(previous)) {
            const g = this.groups.get(previous);
            this.groups.set(previous, this.#normalize({
                ...g,
                joined: false,
                memberCount: Math.max(0, (Number(g.memberCount) || 1) - 1)
            }));
        }
        for (const [id, g] of this.groups) {
            if (g.joined) {
                this.groups.set(id, this.#normalize({ ...g, joined: false }));
            }
        }
        this.currentGroupId = null;
        this.membershipState = "NO_GROUP";
        this.#notifyMembership();
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
        const hasPassword = !!(g.hasPassword || g.passwordProtected);
        const members = Array.isArray(g.members)
            ? g.members.map((m) => ({
                name: String(m?.name || ""),
                you: !!m?.you
            })).filter((m) => m.name)
            : [];
        return {
            uuid: String(g.uuid),
            name: String(g.name || "Unnamed"),
            type: String(g.type || "NORMAL").toUpperCase(),
            hasPassword,
            passwordProtected: hasPassword,
            persistent: !!g.persistent,
            memberCount: Number(g.memberCount) || members.length || 0,
            joined: !!g.joined,
            members
        };
    }

    #requestRefresh(options = {}) {
        const silent = !!options.silent;
        if (!this.enabled || this.refreshPending) {
            return;
        }
        if (!this.ws?.isReady?.() && !this.ws?.isReady) {
            // Fall through to sendGroupsRefresh which also guards readiness.
        }
        const operationId = this.#nextOpId("refresh");
        this.refreshPending = true;
        this.pendingOps.set(operationId, {
            kind: "refresh",
            successMessage: "Groups refreshed.",
            startedAt: Date.now()
        });
        if (!silent) {
            this.#setRefreshStatus("Refreshing...");
        }
        this.#syncRefreshButton();
        const sent = this.ws.sendGroupsRefresh(operationId);
        if (sent === false) {
            this.pendingOps.delete(operationId);
            this.refreshPending = false;
            this.#syncRefreshButton();
            if (!silent) {
                this.#setRefreshStatus("Refresh failed: not ready.");
            }
            return;
        }
        // Never leave the button permanently pending if the server never answers.
        if (this.refreshRetryTimer) {
            clearTimeout(this.refreshRetryTimer);
        }
        this.refreshRetryTimer = setTimeout(() => {
            this.refreshRetryTimer = null;
            if (!this.refreshPending) return;
            if (!this.pendingOps.has(operationId)) return;
            this.pendingOps.delete(operationId);
            this.refreshPending = false;
            this.#syncRefreshButton();
            this.#setRefreshStatus("Refresh timed out.");
        }, 12000);
    }

    #syncRefreshButton() {
        if (!this.refreshBtn) return;
        this.refreshBtn.disabled = !this.enabled || this.refreshPending;
    }

    #setRefreshStatus(message) {
        if (!this.refreshStatusEl) return;
        const text = String(message || "");
        this.refreshStatusEl.textContent = text;
        this.refreshStatusEl.hidden = !text;
    }

    #handleRateLimited(result, kind) {
        const retryMs = Math.max(1, Number(result?.retryAfterMs) || 1000);
        const message = result?.message || "Please wait before trying again.";
        if (kind === "refresh") {
            this.#setRefreshStatus(`Wait ${Math.ceil(retryMs / 1000)}s`);
        } else if (kind === "create") {
            this.createPending = false;
            this.#setCreateFormBusy(false);
            this.#setCreateError(message);
        } else if (kind === "join") {
            this.#setJoinError(message);
        }         else if (kind === "leave") {
            this.leavePending = false;
            this.leaveBtn.textContent = this.leaveBtn.dataset.defaultLabel || "Leave Group";
            this.membershipState = this.currentGroupId ? "IN_GROUP" : "NO_GROUP";
            this.onLoadError?.(message);
        }
        this.#scheduleRateLimitRetry(kind, retryMs);
    }

    #scheduleRateLimitRetry(kind, retryMs) {
        const existing = this.rateLimitTimers.get(kind);
        if (existing) {
            clearTimeout(existing);
        }
        const timer = setTimeout(() => {
            this.rateLimitTimers.delete(kind);
            if (kind === "refresh") {
                this.#setRefreshStatus("");
            }
            this.#syncRefreshButton();
        }, retryMs);
        this.rateLimitTimers.set(kind, timer);
    }

    #clearRateLimitTimers() {
        for (const timer of this.rateLimitTimers.values()) {
            clearTimeout(timer);
        }
        this.rateLimitTimers.clear();
    }

    #requestJoin(groupId, password) {
        const operationId = this.#nextOpId("join");
        this.membershipState = "JOINING";
        this.pendingOps.set(operationId, { kind: "join", successMessage: "Joined group." });
        this.#render();
        this.ws.sendGroupJoin(groupId, password, operationId);
    }

    #requestLeave() {
        if (this.leavePending) {
            return;
        }
        const expectedGroupId = this.currentGroupId;
        if (!expectedGroupId) {
            return;
        }
        if (!this.leaveBtn.dataset.defaultLabel) {
            this.leaveBtn.dataset.defaultLabel = this.leaveBtn.textContent || "Leave Group";
        }
        const operationId = this.#nextOpId("leave");
        this.leavePending = true;
        this.membershipState = "LEAVING";
        this.pendingOps.set(operationId, { kind: "leave", successMessage: "Left group." });
        // Fail-closed client audio immediately; do not wait for snapshot.
        this.ws.setInGroup?.(false);
        this.onMembershipChange?.(null);
        this.leaveBtn.disabled = true;
        this.leaveBtn.textContent = "Leaving…";
        this.ws.sendGroupLeave(operationId, expectedGroupId);
        this.#render();
    }

    #requestCreate(name, password, type) {
        if (!this.allowWebCreation) {
            this.#setCreateError("Web group creation is disabled on this server.");
            return;
        }

        const operationId = this.#nextOpId("create");
        this.createPending = true;
        this.membershipState = "CREATING";
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
            this.membershipState = this.currentGroupId ? "IN_GROUP" : "NO_GROUP";
            this.#setCreateFormBusy(false);
            this.#setCreateError("Session is not ready. Wait for connection, then try again.");
            console.debug(`groups:create blocked_not_ready operationId=${operationId}`);
            this.#render();
            return;
        }
        this.membershipState = "JOINING_CREATED_GROUP";
        console.debug(`groups:create sent operationId=${operationId}`);
        this.#render();
    }

    #nextOpId(prefix) {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    }

    #updateCurrentGroup() {
        // While leaving, keep UI/audio gated closed even if the last snapshot still
        // shows joined=true (optimistic clear must not be overwritten by #render).
        if (this.leavePending || this.membershipState === "LEAVING") {
            this.currentGroupEl.textContent = "Leaving…";
            this.leaveBtn.disabled = true;
            if (!this.leaveBtn.dataset.defaultLabel) {
                this.leaveBtn.dataset.defaultLabel = "Leave Group";
            }
            this.leaveBtn.textContent = "Leaving…";
            this.ws?.setInGroup?.(false);
            return;
        }

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
        if (this.leaveBtn.dataset.defaultLabel) {
            this.leaveBtn.textContent = this.leaveBtn.dataset.defaultLabel;
        }
        if (nextId) {
            this.membershipState = "IN_GROUP";
        } else if (
            this.membershipState !== "CREATING"
            && this.membershipState !== "JOINING"
            && this.membershipState !== "JOINING_CREATED_GROUP"
        ) {
            this.membershipState = "NO_GROUP";
        }

        console.debug(
            `groups: membership group=${nextId || "none"} revision=${this.revision} state=${this.membershipState}`
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
        const isProtected = !!(group.passwordProtected || group.hasPassword);
        if (isProtected) {
            const lock = document.createElement("span");
            lock.className = "group-lock";
            lock.title = "Password required";
            lock.setAttribute("aria-label", "Password required");
            lock.textContent = "🔒";
            title.appendChild(document.createTextNode(" "));
            title.appendChild(lock);
        }

        const header = document.createElement("div");
        header.className = "group-row-header";

        const chevron = document.createElement("button");
        chevron.type = "button";
        chevron.className = "group-expand-btn";
        chevron.setAttribute("aria-expanded", this.expandedGroupIds.has(group.uuid) ? "true" : "false");
        chevron.setAttribute("aria-label", `Toggle members for ${group.name}`);
        chevron.textContent = this.expandedGroupIds.has(group.uuid) ? "▾" : "▸";
        chevron.addEventListener("click", () => {
            if (this.expandedGroupIds.has(group.uuid)) {
                this.expandedGroupIds.delete(group.uuid);
            } else {
                this.expandedGroupIds.add(group.uuid);
            }
            this.#render();
        });

        header.appendChild(chevron);
        header.appendChild(title);

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

        main.appendChild(header);
        main.appendChild(meta);

        if (this.expandedGroupIds.has(group.uuid) && group.members?.length) {
            const memberList = document.createElement("ul");
            memberList.className = "group-member-list";
            for (const member of group.members) {
                const item = document.createElement("li");
                item.textContent = member.you ? `${member.name} (you)` : member.name;
                memberList.appendChild(item);
            }
            main.appendChild(memberList);
        }

        const actions = document.createElement("div");
        actions.className = "group-row-actions";

        const joinBtn = document.createElement("button");
        joinBtn.type = "button";
        joinBtn.className = "btn btn-secondary";
        joinBtn.textContent = group.joined ? "Joined" : "Join";
        joinBtn.disabled = !this.enabled || group.joined || this.pendingOps.size > 0;
        joinBtn.addEventListener("click", () => {
            if (group.hasPassword || group.passwordProtected) {
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
