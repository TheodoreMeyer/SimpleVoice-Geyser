import { SvgBuilder } from "./utils/builder.js";
import { SvgLang } from "./utils/lang.js";
import { SvgWebSocket } from "./websocket.js";

SvgLang.detectLanguage();

const client = SvgBuilder.fromDocument().build();

await client.start();

// TODO Make a better alert overlay; this was just implemented haphazardly
const alertOverlay = document.getElementById("alertOverlay");
const alertMessage = document.getElementById("alertMessage");
document.getElementById("closeAlertOverlayBtn").addEventListener("click", (_) => {
    alertOverlay.classList.add("dev-hidden");
});
function showAlertOverlay(message) {
    alertMessage.innerText = message;
    alertOverlay.classList.remove("dev-hidden");
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
// TODO Have properly phrased and nice-to-the-tongue alert messages
client.webSocketController.addEventListener("statusChange", (status) => {
    if (status.connected) {
        document.body.classList.add("connected");
    } else {
        document.body.classList.remove("connected");
        if (status.code === SvgWebSocket.DisconnectPolicy.OUTDATED || status.reason === "update_required") {
            showAlertOverlay("Outdated client. Reloading...");
            return;
        }

        if (SvgWebSocket.DisconnectPolicy.FATAL.has(status.code) || status.reason === "fatal") {
            showAlertOverlay("Fatal disconnect. Reconnect disabled.");
            return;
        }

        if (status.code === SvgWebSocket.DisconnectPolicy.SERVER_SHUTDOWN) {
            showAlertOverlay("Server shutdown: " + status.reason);
            return;
        }

        if (status.code === SvgWebSocket.DisconnectPolicy.TIMEOUT) {
            showAlertOverlay("Timeout disconnect.");
            return;
        }

        if (SvgWebSocket.DisconnectPolicy.NO_RECONNECT.has(status.code)) {
            showAlertOverlay("Not reconnecting.")
        }
    }
}, true);

client.webSocketController.addEventListener("message", (data) => {
    if (data.type === "json" && (data.packetType === "error" || data.fatalAuthError === true)) {
        showAlertOverlay(`${data.packetType || "Info"}: ${data.msg || "Unparseable, check console for more information."}`);
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
                SvgLang.changeLanguage(lang);

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