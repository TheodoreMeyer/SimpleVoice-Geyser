/**
 * Frontend/server build identity helpers.
 * Packaged builds replace 0.1.3-c471505-dirty-20260810T080751Z etc. via Gradle; source trees keep placeholders.
 */

export const FRONTEND_BUILD_ID = (typeof window !== "undefined" && window.BUILD_ID)
    ? String(window.BUILD_ID)
    : "0.1.3-c471505-dirty-20260810T080751Z";

export const PROTOCOL_VERSION = (typeof window !== "undefined" && Number.isFinite(window.PROTOCOL_VERSION))
    ? Number(window.PROTOCOL_VERSION)
    : Number("3") || 3;

export const FRONTEND_SCHEMA = (typeof window !== "undefined" && Number.isFinite(window.FRONTEND_SCHEMA))
    ? Number(window.FRONTEND_SCHEMA)
    : Number("4") || 4;

/**
 * @param {string|null|undefined} serverBuildId
 * @param {number|null|undefined} protocolVersion
 * @returns {{ match: boolean, reason: string }}
 */
export function compareBuildIdentity(serverBuildId, protocolVersion) {
    const frontend = FRONTEND_BUILD_ID;
    const server = serverBuildId == null ? "" : String(serverBuildId);
    if (!frontend || frontend.includes("@@")) {
        return { match: false, reason: "frontend_unstamped" };
    }
    if (!server) {
        return { match: false, reason: "server_missing" };
    }
    if (frontend !== server) {
        return { match: false, reason: "build_mismatch" };
    }
    if (protocolVersion != null && Number(protocolVersion) !== PROTOCOL_VERSION) {
        return { match: false, reason: "protocol_mismatch" };
    }
    return { match: true, reason: "ok" };
}

/**
 * One-shot reload for mismatched clients. Uses sessionStorage to avoid loops.
 * @param {string} buildId
 */
export function reloadUpdatedClientOnce(buildId) {
    const key = "svg.build.reload.once";
    try {
        if (sessionStorage.getItem(key) === buildId) {
            return false;
        }
        sessionStorage.setItem(key, buildId);
    } catch {
        // private mode — still attempt a single reload via URL marker
    }
    const url = new URL(window.location.href);
    url.searchParams.set("svgBuildReload", "1");
    url.searchParams.set("v", buildId || String(Date.now()));
    window.location.replace(url.toString());
    return true;
}

export function clearReloadGuard() {
    try {
        sessionStorage.removeItem("svg.build.reload.once");
    } catch {
        // ignore
    }
}
