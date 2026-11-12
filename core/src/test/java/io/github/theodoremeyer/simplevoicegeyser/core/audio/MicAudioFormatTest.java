package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicAudioFormatTest {

    @Test
    void validatesPcmAndRawOpusPacketSizes() {
        assertTrue(MicAudioFormat.PCM16LE.acceptsPacketLength(1920));
        assertFalse(MicAudioFormat.PCM16LE.acceptsPacketLength(1918));
        assertTrue(MicAudioFormat.OPUS.acceptsPacketLength(1));
        assertTrue(MicAudioFormat.OPUS.acceptsPacketLength(1275));
        assertFalse(MicAudioFormat.OPUS.acceptsPacketLength(0));
        assertFalse(MicAudioFormat.OPUS.acceptsPacketLength(1276));
    }
}
