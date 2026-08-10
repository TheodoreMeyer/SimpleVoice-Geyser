import fs from "node:fs";
import { execSync } from "node:child_process";

// Re-extract inline
const cssOld = fs.readFileSync(
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

function hexToRgb(hex) {
    const h = hex.replace("#", "");
    const full = h.length === 3 ? h.split("").map((c) => c + c).join("") : h;
    const n = parseInt(full, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function rgba(hex, a) {
    const [r, g, b] = hexToRgb(hex);
    return `rgba(${r}, ${g}, ${b}, ${a})`;
}

const lines = [
    "/*",
    " * Color presets — CSS custom properties only.",
    " * Do not style form/input/button/dialog layout here.",
    " */",
    ""
];

for (const name of names) {
    const block = (cssOld.match(new RegExp(`\\.preset-${name}\\s*\\{([\\s\\S]*?)\\n\\}`)) || [])[1];
    const h1 = pick((cssOld.match(new RegExp(`\\.preset-${name} h1\\s*\\{([\\s\\S]*?)\\}`)) || [])[1], "color");
    const btn = pick((cssOld.match(new RegExp(`\\.preset-${name} button\\s*\\{([\\s\\S]*?)\\}`)) || [])[1], "background-color");
    const btnHover = pick((cssOld.match(new RegExp(`\\.preset-${name} button:hover\\s*\\{([\\s\\S]*?)\\}`)) || [])[1], "background-color");
    const form = pick((cssOld.match(new RegExp(`\\.preset-${name} form\\s*\\{([\\s\\S]*?)\\}`)) || [])[1], "background");
    const input = (cssOld.match(new RegExp(
        `\\.preset-${name} input,\\s*\\.preset-${name} select\\s*\\{([\\s\\S]*?)\\}`
    )) || [])[1];
    const bg = pick(block, "background");
    const color = pick(block, "color");
    const inputBg = pick(input, "background") || pick(block, "--theme-input-bg");
    const inputBorder = pick(block, "--theme-input-border");
    const inputText = pick(block, "--theme-input-text") || color;
    const accent = h1 || btn;
    const accentHover = btnHover || accent;
    const surface = form || bg;
    const isLight = name === "light";

    lines.push(`.preset-${name} {`);
    lines.push(`  color: ${color};`);
    lines.push(`  --bg-deep: ${bg};`);
    lines.push(`  --bg: ${bg};`);
    lines.push(`  --bg-elevated: ${surface};`);
    lines.push(`  --bg-panel: ${surface};`);
    lines.push(`  --bg-row: ${inputBg};`);
    lines.push(`  --border: ${inputBorder};`);
    lines.push(`  --border-strong: ${inputBorder};`);
    lines.push(`  --border-subtle: ${inputBorder};`);
    lines.push(`  --surface-primary: ${surface};`);
    lines.push(`  --surface-secondary: ${bg};`);
    lines.push(`  --surface-tertiary: ${isLight ? "rgba(0, 0, 0, 0.04)" : "rgba(0, 0, 0, 0.22)"};`);
    lines.push(`  --input-border: ${inputBorder};`);
    lines.push(`  --input-background: ${isLight ? "#ffffff" : bg};`);
    lines.push(`  --text: ${color};`);
    lines.push(`  --text-primary: ${color};`);
    lines.push(`  --text-muted: ${isLight ? "#4a5560" : "#9aa7b5"};`);
    lines.push(`  --text-dim: ${isLight ? "#6b7785" : "#6f7d8c"};`);
    lines.push(`  --accent: ${accent};`);
    lines.push(`  --accent-hover: ${accentHover};`);
    lines.push(`  --accent-soft: ${rgba(accent, 0.14)};`);
    lines.push(`  --focus: ${accent};`);
    lines.push(`  background:`);
    lines.push(`    radial-gradient(1200px 600px at 10% -10%, ${rgba(accent, 0.12)}, transparent 55%),`);
    lines.push(`    radial-gradient(900px 500px at 100% 0%, ${rgba(accent, 0.08)}, transparent 50%),`);
    lines.push(`    linear-gradient(180deg, ${bg} 0%, ${bg} 100%);`);
    lines.push(`}`);
    lines.push("");
}

fs.writeFileSync(
    "c:/Users/lowry/Coding/SimpleVoice-Geyser/core/web/css/presets.css",
    lines.join("\n") + "\n",
    "utf8"
);
console.log("wrote presets.css", lines.length, "lines");
