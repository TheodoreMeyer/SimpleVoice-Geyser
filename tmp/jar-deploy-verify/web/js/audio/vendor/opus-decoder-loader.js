/**
 * Local ESM loader for the vendored opus-decoder UMD bundle (no CDN).
 * WASM is embedded in the min bundle; CSP must allow wasm-unsafe-eval (not unsafe-eval).
 */
export const DECODER_BUILD_ID = "0.1.3-c471505-dirty-20260810T080751Z";
export const DECODER_PACKAGE_VERSION = "0.7.11-local";

let loadPromise = null;

export async function loadOpusDecoderModule() {
    if (typeof globalThis !== "undefined") {
        globalThis.DECODER_BUILD_ID = DECODER_BUILD_ID;
    }
    if (globalThis.__svgOpusDecoderModule) {
        return globalThis.__svgOpusDecoderModule;
    }
    if (!loadPromise) {
        loadPromise = new Promise((resolve, reject) => {
            const existing = globalThis["opus-decoder"];
            if (existing?.OpusDecoder) {
                resolve(existing);
                return;
            }
            const script = document.createElement("script");
            const url = new URL("./opus-decoder.min.js", import.meta.url);
            if (DECODER_BUILD_ID && !String(DECODER_BUILD_ID).includes("@@")) {
                url.searchParams.set("v", DECODER_BUILD_ID);
            }
            script.src = url.href;
            script.async = true;
            script.onload = () => {
                const mod = globalThis["opus-decoder"];
                if (!mod?.OpusDecoder) {
                    reject(new Error("Vendored opus-decoder failed to expose OpusDecoder"));
                    return;
                }
                resolve(mod);
            };
            script.onerror = () => reject(new Error("Failed loading local opus-decoder.min.js"));
            document.head.appendChild(script);
        }).then((mod) => {
            globalThis.__svgOpusDecoderModule = mod;
            globalThis.DECODER_BUILD_ID = DECODER_BUILD_ID;
            return mod;
        });
    }
    return loadPromise;
}

export function getDecoderBuildIdentity() {
    return {
        buildId: DECODER_BUILD_ID,
        packageVersion: DECODER_PACKAGE_VERSION
    };
}
