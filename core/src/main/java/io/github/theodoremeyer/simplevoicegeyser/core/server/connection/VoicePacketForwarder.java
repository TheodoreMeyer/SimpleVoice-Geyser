package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import java.util.UUID;

public final class VoicePacketForwarder {

    private VoicePacketForwarder() {}

    public static <P> Result forward(UUID senderUuid, P packet, Iterable<? extends Receiver<P>> receivers) {
        if (senderUuid == null) {
            return new Result(0, 0, 1, 0, 0, 0);
        }

        int forwarded = 0;
        int skippedSelf = 0;
        int skippedInactive = 0;
        int skippedMissingListener = 0;
        int failed = 0;

        for (Receiver<P> receiver : receivers) {
            if (receiver == null) {
                continue;
            }

            UUID receiverUuid = receiver.uuid();
            if (senderUuid.equals(receiverUuid)) {
                skippedSelf++;
                continue;
            }

            if (!receiver.authenticated() || receiver.closed()) {
                skippedInactive++;
                continue;
            }

            if (!receiver.canReceiveVoicePacket()) {
                skippedMissingListener++;
                continue;
            }

            try {
                receiver.receive(packet);
                forwarded++;
            } catch (Exception ignored) {
                failed++;
            }
        }

        return new Result(forwarded, skippedSelf, 0, skippedInactive, skippedMissingListener, failed);
    }

    public interface Receiver<P> {
        UUID uuid();

        boolean authenticated();

        boolean closed();

        boolean canReceiveVoicePacket();

        void receive(P packet);
    }

    public record Result(
            int forwarded,
            int skippedSelf,
            int skippedNullSender,
            int skippedInactive,
            int skippedMissingListener,
            int failed
    ) {
        public Result plus(Result other) {
            if (other == null) {
                return this;
            }
            return new Result(
                    forwarded + other.forwarded,
                    skippedSelf + other.skippedSelf,
                    skippedNullSender + other.skippedNullSender,
                    skippedInactive + other.skippedInactive,
                    skippedMissingListener + other.skippedMissingListener,
                    failed + other.failed
            );
        }
    }
}
