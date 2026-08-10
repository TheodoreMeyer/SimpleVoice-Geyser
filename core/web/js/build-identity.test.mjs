import test from "node:test";
import assert from "node:assert/strict";
import {
    compareBuildIdentity,
    FRONTEND_BUILD_ID,
    PROTOCOL_VERSION
} from "./build-identity.js";

test("compareBuildIdentity rejects empty server", () => {
    const result = compareBuildIdentity("", PROTOCOL_VERSION);
    assert.equal(result.match, false);
    assert.ok(result.reason === "server_missing" || result.reason === "frontend_unstamped");
});

test("compareBuildIdentity rejects mismatched builds when frontend stamped", () => {
    // Source trees keep placeholders; when stamped, mismatch is detected.
    if (String(FRONTEND_BUILD_ID).includes("@@")) {
        const result = compareBuildIdentity("0.1.3-abc-20260101T000000Z", 3);
        assert.equal(result.match, false);
        assert.equal(result.reason, "frontend_unstamped");
        return;
    }
    const result = compareBuildIdentity("other-build", 3);
    assert.equal(result.match, false);
    assert.equal(result.reason, "build_mismatch");
});

test("compareBuildIdentity rejects protocol mismatch when builds equal and stamped", () => {
    if (String(FRONTEND_BUILD_ID).includes("@@")) {
        return;
    }
    const result = compareBuildIdentity(FRONTEND_BUILD_ID, 1);
    assert.equal(result.match, false);
    assert.equal(result.reason, "protocol_mismatch");
});
