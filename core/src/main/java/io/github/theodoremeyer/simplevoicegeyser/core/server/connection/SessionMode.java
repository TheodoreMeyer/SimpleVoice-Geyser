package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

/**
 * How an authenticated SVG websocket session participates in voice chat.
 */
public enum SessionMode {

    /**
     * Browser / web voice client: SVG registers audio listener and sender.
     */
    WEB_VOICE,

    /**
     * Player already has the native Simple Voice Chat mod; SVG is a group controller only.
     */
    NATIVE_VOICE_CONTROLLER
}
