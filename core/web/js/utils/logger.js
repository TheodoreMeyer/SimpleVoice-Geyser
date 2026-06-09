let handler = console.log;

// Initialize debug flag from localStorage
window.debug = localStorage.getItem("debug") === "true";

// Keep localStorage synced whenever debug changes
Object.defineProperty(window, "debug", {
    get() {
        return this._debug ?? false;
    },
    set(value) {
        this._debug = Boolean(value);
        localStorage.setItem("debug", this._debug);
    }
});

// Trigger setter once with stored value
window.debug = localStorage.getItem("debug") === "true";

export function setLogger(fn) {
    handler = fn;
}

export function log(msg) {
    handler(msg);
}

export function debug(msg) {
    if (window.debug) {
        log(msg);
    } else {
        console.debug(msg);
    }
}