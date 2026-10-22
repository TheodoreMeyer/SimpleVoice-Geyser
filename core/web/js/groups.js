import {SvgLang} from "./utils/lang.js";

const DEFAULT_AVATAR = "images/v2/mic.png";

/**
 * SVC-style Groups list + in-group menu for the web client.
 */
export class GroupsUI {
    /**
     * @param {{
     *   listEl: HTMLElement,
     *   openCurrentBtn?: HTMLButtonElement,
     *   passwordInput: HTMLInputElement,
     *   joinSubmitBtn?: HTMLButtonElement,
     *   leaveBtn: HTMLButtonElement,
     *   menuTitleEl?: HTMLElement,
     *   menuMembersEl?: HTMLElement,
     *   createNameInput?: HTMLInputElement,
     *   createPasswordInput?: HTMLInputElement,
     *   createTypeSelect?: HTMLSelectElement,
     *   createTypeHintEl?: HTMLElement,
     *   createSubmitBtn?: HTMLButtonElement,
     *   joinGroup: (name: string, password: string) => void,
     *   leaveGroup: () => void,
     *   createGroup: (name: string, password: string, type?: string) => void,
     *   openGroupMenu: () => void,
     *   openGroupsList: () => void,
     *   openPasswordScreen: () => void
     * }} options
     */
    constructor(options) {
        this.listEl = options.listEl;
        this.openCurrentBtn = options.openCurrentBtn || null;
        this.passwordInput = options.passwordInput;
        this.joinSubmitBtn = options.joinSubmitBtn || null;
        this.leaveBtn = options.leaveBtn;
        this.menuTitleEl = options.menuTitleEl || null;
        this.menuMembersEl = options.menuMembersEl || null;
        this.createNameInput = options.createNameInput || null;
        this.createPasswordInput = options.createPasswordInput || null;
        this.createTypeSelect = options.createTypeSelect || null;
        this.createTypeHintEl = options.createTypeHintEl || null;
        this.createSubmitBtn = options.createSubmitBtn || null;
        this.joinGroup = options.joinGroup;
        this.leaveGroup = options.leaveGroup;
        this.createGroup = options.createGroup;
        this.openGroupMenu = options.openGroupMenu;
        this.openGroupsList = options.openGroupsList;
        this.openPasswordScreen = options.openPasswordScreen;
        /** @type {{ name: string, type?: string, hasPassword?: boolean }[]} */
        this.groups = [];
        /** @type {{ uuid: string, name: string, self?: boolean }[]} */
        this.members = [];
        /** @type {string|null} */
        this.current = null;
        /** @type {string|null} */
        this.currentType = null;
        this.currentHasPassword = false;
        /** @type {string|null} pending group to open after join/create */
        this._pendingOpen = null;
        /** @type {string|null} locked group awaiting password */
        this._passwordTarget = null;
    }

    init() {
        this.leaveBtn?.addEventListener("click", () => {
            this._pendingOpen = null;
            this.leaveGroup();
            this.openGroupsList?.();
        });
        this.openCurrentBtn?.addEventListener("click", () => {
            if (this.current) {
                this.openGroupMenu?.();
            }
        });
        this.joinSubmitBtn?.addEventListener("click", () => this.#submitPasswordJoin());
        this.passwordInput?.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.#submitPasswordJoin();
            }
        });
        this.createTypeSelect?.addEventListener("change", () => this.#updateCreateTypeHint());
        this.createSubmitBtn?.addEventListener("click", () => {
            const name = String(this.createNameInput?.value || "").trim();
            if (!name) {
                this.createNameInput?.focus();
                return;
            }
            const password = String(this.createPasswordInput?.value || "");
            const type = String(this.createTypeSelect?.value || "isolated").trim().toLowerCase() || "isolated";
            this._pendingOpen = name;
            this.createGroup(name, password, type);
            if (this.createNameInput) {
                this.createNameInput.value = "";
            }
            if (this.createPasswordInput) {
                this.createPasswordInput.value = "";
            }
            if (this.createTypeSelect) {
                this.createTypeSelect.value = "isolated";
            }
            this.#updateCreateTypeHint();
        });
        this.#updateCreateTypeHint();
        this.render();
    }

    #updateCreateTypeHint() {
        if (!this.createTypeHintEl) {
            return;
        }
        const type = String(this.createTypeSelect?.value || "isolated").trim().toLowerCase();
        const key =
            type === "open"
                ? "createGroupTypeHintOpen"
                : type === "normal"
                    ? "createGroupTypeHintNormal"
                    : "createGroupTypeHintIsolated";
        const fallback =
            type === "open"
                ? "Party line: nearby players outside the group can hear you too."
                : type === "normal"
                    ? "Outsiders cannot hear you. You can still hear nearby players outside the group."
                    : "Only group members hear each other. Nearby outsiders cannot hear you, and you cannot hear them.";
        this.createTypeHintEl.setAttribute("svg-lang", key);
        this.createTypeHintEl.textContent = SvgLang.string(key) || fallback;
    }

    /**
     * @param {{
     *   groups?: any[],
     *   current?: string|null,
     *   members?: any[],
     *   currentType?: string|null,
     *   currentHasPassword?: boolean
     * }} data
     */
    update(data) {
        const prev = this.current;
        this.groups = Array.isArray(data?.groups) ? data.groups : [];
        const cur = data?.current;
        this.current = cur && cur !== "null" ? String(cur) : null;
        this.currentType = data?.currentType && data.currentType !== "null"
            ? String(data.currentType)
            : null;
        this.currentHasPassword = !!data?.currentHasPassword;
        this.members = Array.isArray(data?.members) ? data.members : [];

        if (this.current !== prev && this.passwordInput) {
            this.passwordInput.value = "";
        }

        // Keep _pendingOpen across a transient current=null broadcast that fires
        // when SVC CreateGroupEvent runs before connection.setGroup.
        const shouldOpen =
            this._pendingOpen
            && this.current
            && this.current.toLowerCase() === this._pendingOpen.toLowerCase();
        if (shouldOpen) {
            this._pendingOpen = null;
            this.openGroupMenu?.();
        }

        this.render();
    }

    reset() {
        this.groups = [];
        this.members = [];
        this.current = null;
        this.currentType = null;
        this.currentHasPassword = false;
        this._pendingOpen = null;
        this._passwordTarget = null;
        if (this.passwordInput) {
            this.passwordInput.value = "";
        }
        if (this.createNameInput) {
            this.createNameInput.value = "";
        }
        if (this.createPasswordInput) {
            this.createPasswordInput.value = "";
        }
        if (this.createTypeSelect) {
            this.createTypeSelect.value = "isolated";
        }
        this.#updateCreateTypeHint();
        this.render();
    }

    render() {
        this.#renderOpenCurrent();
        this.#renderList();
        this.#renderMenu();
    }

    #renderOpenCurrent() {
        if (!this.openCurrentBtn) {
            return;
        }
        if (!this.current) {
            this.openCurrentBtn.classList.add("dev-hidden");
            this.openCurrentBtn.textContent = "";
            return;
        }
        this.openCurrentBtn.classList.remove("dev-hidden");
        const openLabel = SvgLang.string("groupOpenMenuBtn") || "Open group";
        this.openCurrentBtn.textContent = `${openLabel}: ${this.current}`;
    }

    #renderList() {
        if (!this.listEl) {
            return;
        }
        this.listEl.replaceChildren();

        if (this.groups.length === 0) {
            const empty = document.createElement("div");
            empty.className = "svcListRow svcListRowStatic";
            SvgLang.setElement(empty, "groupsEmptyLabel");
            this.listEl.appendChild(empty);
            return;
        }

        for (const group of this.groups) {
            if (!group?.name) {
                continue;
            }
            const isCurrent = group.name === this.current;
            const row = document.createElement("button");
            row.type = "button";
            row.className = "svcListRow";
            if (isCurrent) {
                row.classList.add("svcGroupRowJoined");
            }

            if (group.hasPassword) {
                const lock = document.createElement("span");
                lock.className = "svcGroupLock";
                lock.title = SvgLang.string("groupHasPasswordHint") || "Password required";
                lock.setAttribute("aria-hidden", "true");
                row.appendChild(lock);
            }

            const body = document.createElement("span");
            body.className = "svcListLabel mcTextStyle2";
            body.textContent = group.name;
            row.appendChild(body);

            const badge = document.createElement("span");
            badge.className = "svcGroupBadge mcTextStyle1";
            if (isCurrent) {
                badge.classList.add("svcGroupBadgeJoined");
                badge.textContent = SvgLang.string("groupJoinedBadge") || "Joined";
            } else if (group.hasPassword) {
                badge.textContent = SvgLang.string("groupLockedBadge") || "Locked";
            } else {
                badge.textContent = SvgLang.string("groupOpenBadge") || "Open";
            }
            row.appendChild(badge);

            row.addEventListener("click", () => this.#onGroupClick(group));
            this.listEl.appendChild(row);
        }
    }

    /**
     * @param {{ name: string, hasPassword?: boolean }} group
     */
    #onGroupClick(group) {
        if (group.name === this.current) {
            this.openGroupMenu?.();
            return;
        }
        if (group.hasPassword) {
            this._passwordTarget = group.name;
            if (this.passwordInput) {
                this.passwordInput.value = "";
            }
            this.openPasswordScreen?.();
            queueMicrotask(() => this.passwordInput?.focus());
            return;
        }
        this._pendingOpen = group.name;
        this.joinGroup(group.name, "");
    }

    #submitPasswordJoin() {
        const name = this._passwordTarget;
        if (!name) {
            this.openGroupsList?.();
            return;
        }
        const password = String(this.passwordInput?.value || "");
        if (!password) {
            this.passwordInput?.focus();
            return;
        }
        this._pendingOpen = name;
        this.joinGroup(name, password);
    }

    #renderMenu() {
        if (this.menuTitleEl) {
            if (this.current) {
                const type = (this.currentType || "OPEN").toUpperCase();
                const typeLabel =
                    type === "NORMAL"
                        ? (SvgLang.string("groupTypeNormal") || "Normal")
                        : type === "ISOLATED"
                            ? (SvgLang.string("groupTypeIsolated") || "Isolated")
                            : (SvgLang.string("groupTypeOpen") || "Open");
                this.menuTitleEl.textContent = `${this.current} (${typeLabel})`;
            } else {
                this.menuTitleEl.textContent = SvgLang.string("groupsTitle") || "Groups";
            }
        }

        if (!this.menuMembersEl) {
            return;
        }
        this.menuMembersEl.replaceChildren();

        if (!this.current) {
            const empty = document.createElement("div");
            empty.className = "svcListRow svcListRowStatic";
            SvgLang.setElement(empty, "groupNoneLabel");
            this.menuMembersEl.appendChild(empty);
            return;
        }

        if (this.members.length === 0) {
            const empty = document.createElement("div");
            empty.className = "svcListRow svcListRowStatic";
            SvgLang.setElement(empty, "groupMembersEmptyLabel");
            this.menuMembersEl.appendChild(empty);
            return;
        }

        for (const member of this.members) {
            const row = document.createElement("div");
            row.className = "svcListRow svcListRowStatic svcMemberRow";
            if (member.self) {
                row.classList.add("svcMemberSelf");
            }

            const avatar = document.createElement("img");
            avatar.className = "svcMemberAvatar";
            avatar.alt = "";
            avatar.width = 32;
            avatar.height = 32;
            avatar.src = member.uuid
                ? `https://mc-heads.net/avatar/${member.uuid}/32`
                : DEFAULT_AVATAR;
            avatar.onerror = () => {
                avatar.onerror = null;
                avatar.src = DEFAULT_AVATAR;
            };

            const name = document.createElement("span");
            name.className = "svcListLabel mcTextStyle2";
            name.textContent = member.name || "?";

            row.appendChild(avatar);
            row.appendChild(name);

            if (member.self) {
                const you = document.createElement("span");
                you.className = "svcGroupBadge svcGroupBadgeJoined mcTextStyle1";
                you.textContent = SvgLang.string("groupYouBadge") || "You";
                row.appendChild(you);
            }

            this.menuMembersEl.appendChild(row);
        }
    }
}
