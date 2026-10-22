import { SvgBuilder } from "./utils/builder.js";
import { SvgLang } from "./utils/lang.js";
import { SvgWebSocket } from "./websocket.js";

// Safety net if the inline cleaner in index.html did not run
try {
    const url = new URL(window.location.href);
    if (url.searchParams.has("_svg")) {
        url.searchParams.delete("_svg");
        const clean = `${url.pathname}${url.search}${url.hash}`;
        history.replaceState(null, "", clean || url.pathname);
    }
} catch {
    // ignore
}

await SvgLang.loadWebConfig();
SvgLang.detectLanguage();
SvgLang.applyBranding();

const client = SvgBuilder.fromDocument().build();

await client.start();

const ALERT_AUTO_DISMISS_MS = 60_000;
const alertOverlay = document.getElementById("alertOverlay");
const alertMessage = document.getElementById("alertMessage");
let alertDismissTimer = null;

const pendingJoinSecondsEl = document.getElementById("pendingJoinSeconds");
let pendingJoinCountdownTimer = null;

function stopPendingJoinCountdown() {
    if (pendingJoinCountdownTimer !== null) {
        clearInterval(pendingJoinCountdownTimer);
        pendingJoinCountdownTimer = null;
    }
    document.body.classList.remove("pending-minecraft-join");
}

function startPendingJoinCountdown() {
    stopPendingJoinCountdown();
    document.body.classList.add("pending-minecraft-join");
    let remaining = SvgLang.webBranding.joinTimeoutSeconds || 60;
    if (pendingJoinSecondsEl) {
        pendingJoinSecondsEl.textContent = String(remaining);
    }
    pendingJoinCountdownTimer = setInterval(() => {
        remaining -= 1;
        if (pendingJoinSecondsEl) {
            pendingJoinSecondsEl.textContent = String(Math.max(0, remaining));
        }
        if (remaining <= 0) {
            stopPendingJoinCountdown();
        }
    }, 1000);
}

function hideAlertOverlay() {
    if (alertDismissTimer !== null) {
        clearTimeout(alertDismissTimer);
        alertDismissTimer = null;
    }
    alertOverlay.classList.add("dev-hidden");
}

document.getElementById("closeAlertOverlayBtn").addEventListener("click", (_) => {
    hideAlertOverlay();
});

function showAlertOverlay(message) {
    alertMessage.innerText = message;
    alertOverlay.classList.remove("dev-hidden");
    if (alertDismissTimer !== null) {
        clearTimeout(alertDismissTimer);
    }
    alertDismissTimer = setTimeout(() => {
        alertOverlay.classList.add("dev-hidden");
        alertDismissTimer = null;
    }, ALERT_AUTO_DISMISS_MS);
}

// Auto send when enter is pressed
const chatBtn = document.getElementById("chatBtn")
document.getElementById("chatInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        event.preventDefault();
        chatBtn.click();
    }
});

// Detect connection state to show appropriate section
client.webSocketController.addEventListener("statusChange", (status) => {
    if (status.waitingForMinecraft) {
        startPendingJoinCountdown();
        return;
    }

    if (status.connected) {
        stopPendingJoinCountdown();
        document.body.classList.add("connected");
        return;
    }

    document.body.classList.remove("connected");
    stopPendingJoinCountdown();

    if (status.code === SvgWebSocket.DisconnectPolicy.OUTDATED || status.reason === "update_required") {
        showAlertOverlay(SvgLang.string("alertOutdatedClient"));
        return;
    }

    if (SvgWebSocket.DisconnectPolicy.FATAL.has(status.code) || status.reason === "fatal") {
        showAlertOverlay(SvgLang.string("alertFatalDisconnect"));
        return;
    }

    if (status.code === SvgWebSocket.DisconnectPolicy.SERVER_SHUTDOWN) {
        showAlertOverlay(`${SvgLang.string("alertServerShutdownPrefix")} ${status.reason}`);
        return;
    }

    if (status.code === SvgWebSocket.DisconnectPolicy.TIMEOUT
        || status.reason === "join_wait_expired") {
        showAlertOverlay(SvgLang.string("alertJoinWaitExpired"));
        return;
    }

    if (SvgWebSocket.DisconnectPolicy.NO_RECONNECT.has(status.code)) {
        showAlertOverlay(SvgLang.string("alertNotReconnecting"));
    }
}, true);

client.webSocketController.addEventListener("message", (data) => {
    if (data.type === "json" && (data.packetType === "error" || data.fatalAuthError === true)) {
        const prefix = data.packetType || "Info";
        const detail = data.msg || SvgLang.string("alertUnparseable");
        showAlertOverlay(`${prefix}: ${detail}`);
    }
}, true);

// Language Changer
const langBtn = document.getElementById("langBtn");
const langBtnImg = document.getElementById("langBtnImg");
const langOptionsContainer = document.getElementById("langOptionsContainer");

langBtnImg.src = `images/flags-1x1/${SvgLang.currentLanguage}.svg`;

langBtn.addEventListener("click", (_) => {
    if (langOptionsContainer.childElementCount === 0) {
        // Populate #langOptionsContainer with available languages
        for (const lang of SvgLang.availableLanguages) {
            if (lang === SvgLang.currentLanguage) {
                continue;
            }

            const html =
                `<div class="mcIconButtonStyle">
                        <button class="mcIconButtonStyleInner mcTextStyle2" id="${lang}LangBtn">
                            <img src="images/flags-1x1/${lang}.svg" alt="Change to ${lang}">
                        </button>
                    </div>`;
            langOptionsContainer.insertAdjacentHTML("beforeend", html);

            document.getElementById(`${lang}LangBtn`).addEventListener("click", (_) => {
                SvgLang.changeLanguage(lang, true);

                langBtnImg.src = `images/flags-1x1/${lang}.svg`;
                langOptionsContainer.innerHTML = "";
                langBtn.style.backgroundColor = "var(--color4)";
                langBtnImg.style.filter = "brightness(1.0)";
            });
        }

        langBtn.style.backgroundColor = "var(--color3)";
        langBtnImg.style.filter = "brightness(0.2)";
    } else {
        // Hide available languages
        langOptionsContainer.innerHTML = "";
        langBtn.style.backgroundColor = "var(--color4)";
        langBtnImg.style.filter = "brightness(1.0)";
    }
});
