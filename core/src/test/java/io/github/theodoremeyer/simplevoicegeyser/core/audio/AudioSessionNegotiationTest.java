package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioSessionNegotiationTest {

    @Test
    void defaultPolicyAllowsPcmWithoutCapabilities() {
        AudioSessionNegotiation negotiation = new AudioSessionNegotiation(AudioTransportMode.LEGACY, true);
        assertTrue(negotiation.isMicFormatAllowed());
        negotiation.updateClientCapabilities(true, false, false, false);
        assertTrue(negotiation.isMicFormatAllowed());
        assertEquals(MicAudioFormat.PCM16LE, negotiation.getSelectedMicFormat());
    }

    @Test
    void opusOnlyPolicyRequiresCapabilitiesAndRejectsPcmRenegotiation() {
        AudioSessionNegotiation negotiation = new AudioSessionNegotiation(AudioTransportMode.LEGACY, true, false);
        assertFalse(negotiation.isMicFormatAllowed());
        negotiation.updateClientCapabilities(true, false, false, true, true);
        assertTrue(negotiation.isMicFormatAllowed());
        assertEquals(MicAudioFormat.OPUS, negotiation.getSelectedMicFormat());
        assertEquals(AudioTransportMode.LEGACY, negotiation.getSelectedMode());
        assertTrue(negotiation.summary().contains("micLegacyAllowed=false"));
        negotiation.updateClientCapabilities(true, false, false, true, false);
        assertFalse(negotiation.isMicFormatAllowed());
    }

    @Test
    void receiveFallbackDoesNotControlPcmMicrophoneCompatibility() {
        AudioSessionNegotiation negotiation = new AudioSessionNegotiation(AudioTransportMode.SVG_V2, false, true);
        negotiation.updateClientCapabilities(false, true, true, true, false);
        assertTrue(negotiation.isMicFormatAllowed());
        assertEquals(MicAudioFormat.PCM16LE, negotiation.getSelectedMicFormat());
        assertEquals(AudioTransportMode.SVG_V2, negotiation.getSelectedMode());
    }

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
