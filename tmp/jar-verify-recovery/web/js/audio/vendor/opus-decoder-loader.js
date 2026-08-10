/**
 * Local ESM loader for the vendored opus-decoder UMD bundle (no CDN).
 * WASM is embedded in the min bundle; CSP must allow wasm-unsafe-eval (not unsafe-eval).
 */
let loadPromise = null;

export async function loadOpusDecoderModule() {
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
            script.src = new URL("./opus-decoder.min.js", import.meta.url).href;
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
            return mod;
        });
    }
    return loadPromise;
}
