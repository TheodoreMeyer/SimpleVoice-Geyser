package io.github.theodoremeyer.SimpleVoiceGeyser;

import io.github.theodoremeyer.SimpleVoiceGeyser.VoiceChatHandler;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;

public class WebServer extends NanoHTTPD {

    private final VoiceChatHandler voiceChatHandler;

    public WebServer(int port, VoiceChatHandler voiceChatHandler) {
        super(port);
        this.voiceChatHandler = voiceChatHandler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() == Method.POST && "/audio".equals(session.getUri())) {
            try {
                // Parse audio data from the request body
                byte[] audioData = session.getInputStream().readAllBytes();
                String playerId = session.getParms().get("playerId");
                String channel = session.getParms().get("channel");

                if (playerId != null && channel != null) {
                    voiceChatHandler.sendAudio(playerId, channel, audioData);
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Audio sent!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error processing audio.");
            }
        } else if (session.getMethod() == Method.GET && "/".equals(session.getUri())) {
            // Serve the web interface
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>SimpleVoiceChat Config</title>
                    <script>
                        // JavaScript code to send audio to the server
                        async function sendAudio() {
                            const micId = document.getElementById('microphone').value;
                            const channel = document.getElementById('channel').value;

                            const stream = await navigator.mediaDevices.getUserMedia({ audio: { deviceId: micId } });
                            const audioContext = new AudioContext();
                            const source = audioContext.createMediaStreamSource(stream);
                            const processor = audioContext.createScriptProcessor(1024, 1, 1);

                            const ws = new WebSocket('ws://' + location.host + '/audio');
                            ws.onopen = () => {
                                processor.onaudioprocess = (e) => {
                                    const audioData = e.inputBuffer.getChannelData(0);
                                    ws.send(JSON.stringify({
                                        playerId: '<PLAYER_ID>',
                                        channel: channel,
                                        data: Array.from(audioData)
                                    }));
                                };
                                source.connect(processor);
                                processor.connect(audioContext.destination);
                            };
                        }
                    </script>
                </head>
                <body>
                    <h1>SimpleVoiceChat Config</h1>
                    <label for="microphone">Microphone:</label>
                    <select id="microphone"></select>
                    <label for="channel">Channel:</label>
                    <input id="channel" type="text" placeholder="Enter channel name" />
                    <button onclick="sendAudio()">Start</button>
                </body>
                </html>
            """;
            return newFixedLengthResponse(html);
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }
}
