let handler = console.log;

export function setLogger(fn) {
    handler = fn;
}

export function log(msg) {
    handler(msg);
}