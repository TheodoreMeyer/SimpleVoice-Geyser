package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicFrameCodecTest {

    @Test
    void decodesLegacyRawPcmAndAssignsSequence() {
        byte[] pcm = new byte[AudioFormatConstants.FRAME_BYTES];
        pcm[0] = 1;
        pcm[1] = 2;
        AtomicLong seq = new AtomicLong(7);

        Optional<MicFrame> decoded = MicFrameCodec.decode(pcm, 123L, seq);

        assertTrue(decoded.isPresent());
        MicFrame frame = decoded.get();
        assertTrue(frame.isLegacy());
        assertEquals(7L, frame.getSequence());
        assertEquals(8L, seq.get());
        assertEquals(AudioFormatConstants.SAMPLE_RATE, frame.getSampleRate());
        assertEquals(AudioFormatConstants.FRAME_SAMPLES, frame.getSampleCount());
        assertEquals(123L, frame.getArrivalNanos());
        assertArrayEquals(pcm, frame.getPayload());
    }

    @Test
    void roundTripsVersionedSvtxFrame() {
        byte[] pcm = new byte[AudioFormatConstants.FRAME_BYTES];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }
        MicFrame original = new MicFrame(
                AudioFormatConstants.PROTOCOL_VERSION,
                3L,
                42L,
                AudioFormatConstants.SAMPLE_RATE,
                AudioFormatConstants.CHANNELS,
                AudioFormatConstants.PCM_FORMAT_PCM16LE,
                AudioFormatConstants.FRAME_SAMPLES,
                pcm,
                999L,
                false
        );

        byte[] encoded = MicFrameCodec.encodeVersioned(original);
        assertTrue(encoded.length > AudioFormatConstants.FRAME_BYTES);
        assertEquals('S', encoded[0]);
        assertEquals('V', encoded[1]);
        assertEquals('T', encoded[2]);
        assertEquals('X', encoded[3]);

        Optional<MicFrame> decoded = MicFrameCodec.decode(encoded, 111L, new AtomicLong());
        assertTrue(decoded.isPresent());
        MicFrame frame = decoded.get();
        assertFalse(frame.isLegacy());
        assertEquals(3L, frame.getStreamGeneration());
        assertEquals(42L, frame.getSequence());
        assertEquals(111L, frame.getArrivalNanos());
        assertArrayEquals(pcm, frame.getPayload());
    }

    @Test
    void rejectsTruncatedOrUnknownPayloads() {
        assertTrue(MicFrameCodec.decode(new byte[0], 0L, new AtomicLong()).isEmpty());
        assertTrue(MicFrameCodec.decode(new byte[100], 0L, new AtomicLong()).isEmpty());

        byte[] badHeader = new byte[MicFrameCodec.HEADER_SIZE];
        badHeader[0] = 'S';
        badHeader[1] = 'V';
        badHeader[2] = 'T';
        badHeader[3] = 'X';
        assertTrue(MicFrameCodec.decode(badHeader, 0L, new AtomicLong()).isEmpty());
    }

    @Test
    void validateRejectsWrongSampleCount() {
        assertThrows(IllegalArgumentException.class, () -> new MicFrame(
                AudioFormatConstants.PROTOCOL_VERSION,
                0L,
                0L,
                AudioFormatConstants.SAMPLE_RATE,
                AudioFormatConstants.CHANNELS,
                AudioFormatConstants.PCM_FORMAT_PCM16LE,
                100,
                new byte[200],
                0L,
                false
        ));
    }
}
