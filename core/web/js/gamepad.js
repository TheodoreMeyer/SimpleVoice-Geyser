export const GAMEPAD_BUTTON_NAMES = {
    generic: [
        "Face Bottom (A / Cross / B)",
        "Face Right (B / Circle / A)",
        "Face Left (X / Square / Y)",
        "Face Top (Y / Triangle / X)",
        "Left Bumper",
        "Right Bumper",
        "Left Trigger",
        "Right Trigger",
        "View / Share",
        "Menu / Options",
        "Left Stick",
        "Right Stick",
        "D-Pad Up",
        "D-Pad Down",
        "D-Pad Left",
        "D-Pad Right",
        "Home",
        "Touchpad"
    ],
    xbox: [
        "A",
        "B",
        "X",
        "Y",
        "Left Bumper",
        "Right Bumper",
        "Left Trigger",
        "Right Trigger",
        "View",
        "Menu",
        "Left Stick",
        "Right Stick",
        "D-Pad Up",
        "D-Pad Down",
        "D-Pad Left",
        "D-Pad Right",
        "Xbox",
        "Share"
    ],
    playstation: [
        "Cross",
        "Circle",
        "Square",
        "Triangle",
        "L1",
        "R1",
        "L2",
        "R2",
        "Create",
        "Options",
        "L3",
        "R3",
        "D-Pad Up",
        "D-Pad Down",
        "D-Pad Left",
        "D-Pad Right",
        "PS",
        "Touchpad"
    ],
    nintendo: [
        "B",
        "A",
        "Y",
        "X",
        "L",
        "R",
        "ZL",
        "ZR",
        "Minus",
        "Plus",
        "Left Stick",
        "Right Stick",
        "D-Pad Up",
        "D-Pad Down",
        "D-Pad Left",
        "D-Pad Right",
        "Home",
        "Capture"
    ]
};

export function detectGamepadFamily(gamepadId = "") {
    const id = gamepadId.toLowerCase();
    if (id.includes("xbox") || id.includes("xinput")) return "xbox";
    if (id.includes("playstation") || id.includes("dualshock") || id.includes("dualsense") || id.includes("wireless controller")) return "playstation";
    if (id.includes("nintendo") || id.includes("switch") || id.includes("joy-con")) return "nintendo";
    return "generic";
}

export function getGamepadButtonName(buttonIndex, gamepadId = "") {
    const family = detectGamepadFamily(gamepadId);
    return GAMEPAD_BUTTON_NAMES[family][buttonIndex] || GAMEPAD_BUTTON_NAMES.generic[buttonIndex] || `Button ${buttonIndex}`;
}

export function getConnectedGamepad() {
    const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
    return Array.from(gamepads).find(Boolean) || null;
}
