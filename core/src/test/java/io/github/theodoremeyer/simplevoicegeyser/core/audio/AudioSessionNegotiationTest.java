package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioSessionNegotiationTest {

    @Test
    void micOpusSelectionIsIndependentOfDownlinkAndDefaultsToPcm() {
        AudioSessionNegotiation negotiation = new AudioSessionNegotiation(AudioTransportMode.SVG_V2, true);
        assertEquals(MicAudioFormat.PCM16LE, negotiation.getSelectedMicFormat());

        negotiation.updateClientCapabilities(false, false, false, false, true);
        assertEquals(MicAudioFormat.OPUS, negotiation.getSelectedMicFormat());
        assertEquals(AudioTransportMode.LEGACY, negotiation.getSelectedMode());

        negotiation.updateClientCapabilities(true, false, false, false);
        assertEquals(MicAudioFormat.PCM16LE, negotiation.getSelectedMicFormat());

        negotiation.updateClientCapabilities(true, true, true, true, false);
        assertEquals(MicAudioFormat.PCM16LE, negotiation.getSelectedMicFormat());
        assertEquals(AudioTransportMode.SVG_V2, negotiation.getSelectedMode());
    }
}
