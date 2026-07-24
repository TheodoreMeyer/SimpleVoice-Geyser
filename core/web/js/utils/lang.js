// When adding a new translation, head to https://flagicons.lipis.dev/,
// Press "Download Flags", open the downloaded folder, go to flags/1x1,
// then copy the <xx>.svg country flag representing the language
// of the translation to core/src/web/css/images/flags-1x1
// and rename the file to <language code>.svg instead of <country code>.svg
// E.g. en.svg -> en.svg
//
// Also edit core/src/main/.../server/servlets/FileManager.java
//
// extractResource(
//     "/web/images/flags-1x1/<xx>.svg",
//     "images/flags-1x1/<xx>.svg"
// );
//
// Add the above piece of code to the function "void ExtractAssets()",
// beneath all the other extractResource(...) calls.
//
// Thank You!

import {Logger} from "./logger.js";

export class SvgLang {
    static #translationData = {
        "en": {
            "joinLabel": "Join Simple Voice Chat",
            "usernameInput": "Username",
            "passwordInput": "Password",
            "micSelect": "Select Microphone",
            "speakSelect": "Select Speaker",
            "micLoad": "Loading Microphones...",
            "speakLoad": "Loading Speakers...",
            "joinBtnText": "Join",
            "joinWait": "Waiting to join...",
            "msgText": "Message",
            "sendBtnText": "Send",
            "transmitModeLabel": "Transmit Mode",
            "micSLabel": "Microphone",
            "muteBtnText": "Mute",
            "voiceActivityText": "Voice Activation",
            "pushToTalkText": "Push-To-Talk",
            "pttBindingLabel": "PTT Binding",
            "bindPttBtnText": "Bind Push-to-Talk",
            "clearPttBtnText": "Clear Binding",
            "pttBindingDescription": "No binding set. Use the hold button or add a binding.",
            "allowBackgroundPttText": "Allow background controller PTT",
            "pttLabel": "Push-to-Talk",
            "pushToTalkBtnText": "Hold to Talk",
            "fullscreenPttBtnText": "Fullscreen PTT",
            "debugAudioLabel": "Debug Audio",
            "testSoundBtnText": "Test Sound",
            "colorEditorLabel": "Color Customization",
            "pushToTalkFullscreenBtnText": "Hold to Talk",
            "exitFullscreenPttBtnText": "Exit Fullscreen",
            "devToolsLabel": "Developer Tools"
        },
        "pl": {
            "joinLabel": "Dołącz do Simple Voice Chat",
            "usernameInput": "Nazwa użytkownika",
            "passwordInput": "Hasło",
            "micSelect": "Wybierz mikrofon",
            "speakSelect": "Wybierz głośnik",
            "micLoad": "Ładowanie mikrofonów...",
            "speakLoad": "Ładowanie głośników...",
            "joinBtnText": "Dołącz",
            "joinWait": "Oczekiwanie na połączenie...",
            "msgText": "Wiadomość",
            "sendBtnText": "Wyślij",
            "transmitModeLabel": "Tryb nadawania",
            "micSLabel": "Mikrofon",
            "muteBtnText": "Wycisz",
            "voiceActivityText": "Aktywacja głosem",
            "pushToTalkText": "Naciśnij i mów (PTT)",
            "pttBindingLabel": "Przypisanie klawisza PTT",
            "bindPttBtnText": "Przypisz klawisz Push-to-Talk",
            "clearPttBtnText": "Usuń przypisanie klawisza",
            "pttBindingDescription": "Brak przypisanego klawisza. Użyj przycisku ekranowego lub przypisz klawisz.",
            "allowBackgroundPttText": "Zezwól na PTT na kontrolerze w tle",
            "pttLabel": "Naciśnij i mów",
            "pushToTalkBtnText": "Przytrzymaj, aby mówić",
            "fullscreenPttBtnText": "Tryb pełnoekranowy PTT",
            "debugAudioLabel": "Diagnostyka dźwięku",
            "testSoundBtnText": "Test dźwięku",
            "colorEditorLabel": "Personalizacja kolorów",
            "pushToTalkFullscreenBtnText": "Przytrzymaj, aby mówić",
            "exitFullscreenPttBtnText": "Zamknij pełny ekran",
            "devToolsLabel": "Narzędzia deweloperskie"
        },
        "it": {
            "joinLabel": "Unisciti a Simple Voice Chat",
            "usernameInput": "Nome utente",
            "passwordInput": "Password",
            "micSelect": "Seleziona microfono",
            "speakSelect": "Seleziona altoparlante",
            "micLoad": "Caricamento microfoni...",
            "speakLoad": "Caricamento altoparlanti...",
            "joinBtnText": "Connettiti",
            "joinWait": "In attesa di connessione...",
            "msgText": "Messaggio",
            "sendBtnText": "Invia",
            "transmitModeLabel": "Modalità di trasmissione",
            "micSLabel": "Microfono",
            "muteBtnText": "Silenzia",
            "voiceActivityText": "Attivazione vocale",
            "pushToTalkText": "Premere per parlare (PTT)",
            "pttBindingLabel": "Assegnazione tasto PTT",
            "bindPttBtnText": "Assegna tasto Push-to-Talk",
            "clearPttBtnText": "Rimuovi assegnazione tasto",
            "pttBindingDescription": "Nessun tasto assegnato. Usa il pulsante a schermo o assegna un tasto.",
            "allowBackgroundPttText": "Consenti PTT tramite controller in background",
            "pttLabel": "Premere per parlare",
            "pushToTalkBtnText": "Tieni premuto per parlare",
            "fullscreenPttBtnText": "PTT a schermo intero",
            "debugAudioLabel": "Diagnostica audio",
            "testSoundBtnText": "Test audio",
            "colorEditorLabel": "Personalizzazione colori",
            "pushToTalkFullscreenBtnText": "Tieni premuto per parlare",
            "exitFullscreenPttBtnText": "Esci da schermo intero",
            "devToolsLabel": "Strumenti per sviluppatori"
        }
    };
    static availableLanguages = Object.keys(SvgLang.#translationData);

    static #currentLanguage;
    static get currentLanguage() {
        return SvgLang.#currentLanguage;
    }

    static detectLanguage() {
        let langCode = navigator.language || navigator.userLanguage || "en";
        langCode = langCode.slice(0, 2);

        SvgLang.changeLanguage(langCode);
    }

    static changeLanguage(langCode) {
        if (!(langCode in SvgLang.#translationData)) {
            Logger.log(`Translation for country code ${langCode} unavailable... defaulting to English.`)
            langCode = "en";
        }
        SvgLang.#currentLanguage = langCode;

        const translation = SvgLang.#translationData[langCode];

        document.querySelectorAll("[svg-lang]").forEach(element => {
            const key = element.getAttribute("svg-lang");

            if (key in translation) {
                element.textContent = translation[key];
            }
        });
    }
}