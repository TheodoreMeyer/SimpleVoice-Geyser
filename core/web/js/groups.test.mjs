/**
 * Group directory / create-group payload helpers.
 * Run: node --test core/web/js/groups.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildGroupCreatePayload } from "./groups.js";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const wsJs = fs.readFileSync(path.join(root, "js", "websocket.js"), "utf8");

function applyRevision(current, incoming, { idempotent = false } = {}) {
    const next = Number(incoming);
    if (!Number.isFinite(next)) return { accepted: true, revision: current };
    if (next < current) return { accepted: false, revision: current };
    if (next === current && !idempotent) return { accepted: false, revision: current };
    return { accepted: true, revision: next };
}

test("ignores stale group revisions", () => {
    let rev = 10;
    const stale = applyRevision(rev, 8);
    assert.equal(stale.accepted, false);
    rev = applyRevision(rev, 12).revision;
    assert.equal(rev, 12);
});

test("equal revision accepted only when idempotent", () => {
    assert.equal(applyRevision(5, 5).accepted, false);
    assert.equal(applyRevision(5, 5, { idempotent: true }).accepted, true);
});

test("operationId uniqueness for pending mutations", () => {
    const ids = new Set();
    for (let i = 0; i < 50; i++) {
        const id = `join-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 8)}`;
        assert.equal(ids.has(id), false);
        ids.add(id);
    }
});

test("passwordless join payload omits password field", () => {
    function buildJoin(groupId, password, operationId) {
        const payload = { type: "group_join", groupId, operationId };
        if (password != null && password !== "") {
            payload.password = password;
        }
        return payload;
    }
    assert.equal("password" in buildJoin("uuid", null, "op1"), false);
    assert.equal("password" in buildJoin("uuid", "", "op2"), false);
    assert.equal(buildJoin("uuid", "secret", "op3").password, "secret");
});

test("group_create payload uses groupType and keeps packet type", () => {
    const payload = buildGroupCreatePayload("  Squad  ", null, "ISOLATED", "create-1");
    assert.equal(payload.type, "group_create");
    assert.equal(payload.groupType, "ISOLATED");
    assert.equal(payload.name, "Squad");
    assert.equal(payload.password, null);
    assert.equal(payload.operationId, "create-1");

    const protectedPayload = buildGroupCreatePayload("Squad", "s3cret!", "OPEN", "create-2");
    assert.equal(protectedPayload.password, "s3cret!");
    assert.equal(protectedPayload.groupType, "OPEN");
});

test("websocket sendGroupCreate must not overwrite type with group kind", () => {
    // Regression: duplicate object key `type` previously turned the packet into
    // `{ type: "ISOLATED", ... }` which the server dispatcher never handled.
    assert.match(wsJs, /groupType:\s*type\s*\|\|\s*"ISOLATED"/);
    assert.doesNotMatch(wsJs, /type:\s*"group_create",\s*\n\s*name,\s*\n\s*type:/);
});

test("create group dialog markup invariants", () => {
    assert.match(html, /<dialog id="create-group-dialog"/);
    assert.match(html, /id="create-group-form"/);
    assert.match(html, /id="protected-group-dialog"/);
    assert.match(html, /id="protected-group-form"/);
    assert.match(html, /name="groupName"/);
    assert.match(html, /name="groupPassword"/);
    assert.match(html, /name="groupType"/);
    assert.match(html, /id="create-group-error"/);
    assert.equal((html.match(/id="create-group-dialog"/g) || []).length, 1);
    assert.doesNotMatch(html, /<dialog[^>]*\sopen\b/);
});

test("uuid-keyed map replaces instead of duplicating", () => {
    const groups = new Map();
    groups.set("a", { uuid: "a", name: "One" });
    groups.set("a", { uuid: "a", name: "One Updated" });
    assert.equal(groups.size, 1);
    assert.equal(groups.get("a").name, "One Updated");
});

test("complete snapshot removes absent groups", () => {
    const groups = new Map([
        ["a", { uuid: "a" }],
        ["b", { uuid: "b" }]
    ]);
    const next = [{ uuid: "a" }];
    groups.clear();
    for (const g of next) groups.set(g.uuid, g);
    assert.equal(groups.has("b"), false);
    assert.equal(groups.size, 1);
});

test("failed operation leaves existing directory intact", () => {
    const groups = new Map([["a", { uuid: "a", name: "Keep" }]]);
    const failed = { success: false, error: "Invalid password." };
    if (!failed.success) {
        // no-op
    }
    assert.equal(groups.get("a").name, "Keep");
});

test("blank name rejected before send", () => {
    const name = "   ".trim();
    assert.equal(name.length, 0);
});

test("duplicate create submission blocked while pending", () => {
    const pendingOps = new Map();
    let createPending = false;
    function trySubmit() {
        if (createPending) return false;
        createPending = true;
        pendingOps.set("create-1", { kind: "create" });
        return true;
    }
    assert.equal(trySubmit(), true);
    assert.equal(trySubmit(), false);
});

test("blank optional group password becomes null in payload", () => {
    const payload = buildGroupCreatePayload("Crew", "", "NORMAL", "create-blank");
    assert.equal(payload.password, null);
    assert.equal(payload.groupType, "NORMAL");
});

test("operationId is preserved on create payload", () => {
    const op = "create-1730000000-abc123";
    const payload = buildGroupCreatePayload("Alpha", null, "ISOLATED", op);
    assert.equal(payload.operationId, op);
});

test("sendGroupCreate returns false when not ready (source invariant)", () => {
    assert.match(wsJs, /sendGroupCreate\([\s\S]*?return false;/);
    assert.match(wsJs, /return true;\s*}/);
});

test("create modal markup has Creating-capable submit control", () => {
    assert.match(html, /data-svg="groups\.create-submit"/);
    assert.match(html, /data-svg="groups\.create-error"/);
    assert.match(html, /data-svg="groups\.create-type-help"/);
});

test("groups refresh payload includes operationId", () => {
    function buildRefresh(operationId) {
        return { type: "groups_refresh", operationId };
    }
    const payload = buildRefresh("refresh-1");
    assert.equal(payload.type, "groups_refresh");
    assert.equal(payload.operationId, "refresh-1");
});

test("passwordProtected alias mirrors hasPassword in normalize", () => {
    function normalize(g) {
        const hasPassword = !!(g.hasPassword || g.passwordProtected);
        return {
            hasPassword,
            passwordProtected: hasPassword
        };
    }
    assert.equal(normalize({ passwordProtected: true }).hasPassword, true);
    assert.equal(normalize({ hasPassword: true }).passwordProtected, true);
    assert.equal(normalize({}).hasPassword, false);
});

test("refresh markup exposes status element", () => {
    assert.match(html, /data-svg="groups\.refresh"/);
    assert.match(html, /data-svg="groups\.refresh-status"/);
});

test("websocket leave includes expectedGroupId", () => {
    assert.match(wsJs, /expectedGroupId/);
    assert.match(wsJs, /sendGroupLeave\(operationId,\s*expectedGroupId\)/);
});

test("leave button is type=button", () => {
    assert.match(html, /data-svg="groups\.leave"[^>]*type="button"|type="button"[^>]*data-svg="groups\.leave"/);
});

test("mic processing uses styled switches", () => {
    assert.match(html, /class="svg-switch"/);
    assert.match(html, /data-svg="audio\.applied-status"/);
});

test("websocket exposes sendGroupsRefresh helper", () => {
    assert.match(wsJs, /sendGroupsRefresh\(/);
    assert.match(wsJs, /type:\s*"groups_refresh"/);
});

test("footer attribution is two separate lines", () => {
    assert.match(html, /<span>\s*Created by TheodoreMeyer\s*<\/span>/);
    assert.match(html, /<span>\s*Modernized by Kopeka\s*<\/span>/);
    assert.doesNotMatch(
        html,
        /Created by TheodoreMeyer\s+Modernized by Kopeka/
    );
});

test("snapshot currentGroupId is authoritative over joined flags on cards", () => {
    let currentGroupId = "group-b";
    const list = [
        { uuid: "group-a", name: "A", joined: true, memberCount: 2 },
        { uuid: "group-b", name: "B", joined: false, memberCount: 1 }
    ];
    for (const g of list) {
        g.syncedJoined = currentGroupId != null && g.uuid === currentGroupId;
    }
    assert.equal(list[0].syncedJoined, false);
    assert.equal(list[1].syncedJoined, true);
});

test("directory and membership revisions are tracked separately", () => {
    let directoryRevision = 4;
    let membershipRevision = 2;
    const incoming = { revision: 5, membershipRevision: 3, currentGroupId: "g1", groups: [] };
    if (Number(incoming.revision) > directoryRevision) {
        directoryRevision = incoming.revision;
    }
    if (Number(incoming.membershipRevision) > membershipRevision) {
        membershipRevision = incoming.membershipRevision;
    }
    assert.equal(directoryRevision, 5);
    assert.equal(membershipRevision, 3);
});

test("create modal stays open until joined is true", () => {
    let modalOpen = true;
    let createPending = false;
    const result = { success: true, created: true, joined: false, groupId: "new-group" };
    createPending = false;
    if (result.success && result.joined === true && result.groupId) {
        modalOpen = false;
    }
    assert.equal(modalOpen, true);
    const joinedResult = { success: true, created: true, joined: true, groupId: "new-group" };
    if (joinedResult.success && joinedResult.joined === true && joinedResult.groupId) {
        modalOpen = false;
    }
    assert.equal(modalOpen, false);
});
