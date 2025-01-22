package io.github.theodoremeyer.SimpleVoiceGeyser;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.Player;

import java.util.*;

public class VoiceChatHandler {

    private final String serverHost;
    private final int serverPort;
    private final double defaultProximityRadius;
    private final String alwaysActiveProximityChannelName = "proximity"; // Fixed name for the channel
    private final Map<UUID, List<AudioChannel>> playerChannels = new HashMap<>();

    public VoiceChatHandler(String serverHost, int serverPort, double defaultProximityRadius) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.defaultProximityRadius = defaultProximityRadius;
    }

    public void handlePlayerJoin(Player player) {
        // Automatically add the player to the always-active proximity chat channel
        joinProximityChannel(player);
    }

    private void joinProximityChannel(Player player) {
        VoicechatServerApi voicechat = getVoiceChatApi();
        AudioChannel proximityChannel = voicechat.getChannel(alwaysActiveProximityChannelName);

        if (proximityChannel == null) {
            proximityChannel = voicechat.createChannel(alwaysActiveProximityChannelName);
            proximityChannel.setProximityRadius(defaultProximityRadius);
        }

        proximityChannel.addPlayer(player);
        player.sendMessage("§aJoined proximity chat channel!");
    }

    public void toggleProximityChat(Player player, boolean enabled) {
        VoicechatServerApi voicechat = getVoiceChatApi();
        AudioChannel proximityChannel = voicechat.getChannel(alwaysActiveProximityChannelName);

        if (proximityChannel == null) {
            player.sendMessage("§cProximity chat channel is not available.");
            return;
        }

        if (enabled) {
            proximityChannel.addPlayer(player);
            player.sendMessage("§aProximity chat enabled.");
        } else {
            proximityChannel.removePlayer(player);
            player.sendMessage("§cProximity chat disabled.");
        }
    }

    private VoicechatServerApi getVoiceChatApi() {
        // Assume SimpleVoiceChat provides an API instance
        return VoicechatServerApi.getInstance(serverHost, serverPort);
    }
}
