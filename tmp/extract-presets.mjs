import fs from "node:fs";

const css = fs.readFileSync(
    "c:/Users/lowry/Coding/SimpleVoice-Geyser/core/web/css/presets.css",
    "utf8"
);
const names = [
    "default", "dark", "light", "green", "blue", "purple", "red",
    "yellow", "cyan", "monochrome", "monokai", "orange", "mint", "rose"
];

function pick(block, key) {
    const m = (block || "").match(new RegExp(`${key}:\\s*([^;]+)`));
    return m ? m[1].trim() : null;
}

for (const name of names) {
    const block = (css.match(new RegExp(`\\.preset-${name}\\s*\\{([\\s\\S]*?)\\n\\}`)) || [])[1];
    const h1 = (css.match(new RegExp(`\\.preset-${name} h1\\s*\\{([\\s\\S]*?)\\}`)) || [])[1];
    const btn = (css.match(new RegExp(`\\.preset-${name} button\\s*\\{([\\s\\S]*?)\\}`)) || [])[1];
    const form = (css.match(new RegExp(`\\.preset-${name} form\\s*\\{([\\s\\S]*?)\\}`)) || [])[1];
    const input = (css.match(new RegExp(
        `\\.preset-${name} input,\\s*\\.preset-${name} select\\s*\\{([\\s\\S]*?)\\}`
    )) || [])[1];
    console.log(JSON.stringify({
        name,
        bg: pick(block, "background"),
        color: pick(block, "color"),
        h1: pick(h1, "color"),
        btn: pick(btn, "background-color"),
        btnHover: pick(
            (css.match(new RegExp(`\\.preset-${name} button:hover\\s*\\{([\\s\\S]*?)\\}`)) || [])[1],
            "background-color"
        ),
        form: pick(form, "background"),
        inputBg: pick(input, "background") || pick(block, "--theme-input-bg"),
        inputBorder: pick(block, "--theme-input-border"),
        inputText: pick(block, "--theme-input-text")
    }));
}
