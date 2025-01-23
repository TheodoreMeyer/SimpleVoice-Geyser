package io.github.theodoremeyer.SimpleVoiceGeyser;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.Player;

import java.util.*;

public class VoiceChatHandler {

    private final String serverHost;
    private final int serverPort;
    private final double defaultProximityRadius;
    private final boolean proximityChannelForced;
    private final Map<UUID, List<AudioChannel>> playerChannels = new HashMap<>();
    private final Map<String, String> channelPasswords = new HashMap<>();
    private final Map<String, Set<UUID>> mutedPlayers = new HashMap<>();

    public VoiceChatHandler(String serverHost, int serverPort, double defaultProximityRadius, boolean proximityChannelForced) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.defaultProximityRadius = defaultProximityRadius;
        this.proximityChannelForced = proximityChannelForced;
    }

    // Cross-Platform Joining: Java and Bedrock can join each other's channels
    public void joinChannel(String channelName, Player player, String password) {
        VoicechatServerApi voicechat = getVoiceChatApi();
        AudioChannel channel = voicechat.getChannel(channelName);

        if (channel == null) {
            player.sendMessage("§cChannel not found.");
            return;
        }

        String storedPassword = channelPasswords.get(channelName);
        if (storedPassword != null && !storedPassword.equals(password) && !player.hasPermission("simplevoicegeyser.admin")) {
            player.sendMessage("§cIncorrect password for the channel.");
            return;
        }

        channel.addPlayer(player);
        playerChannels.computeIfAbsent(player.getUuid(), k -> new ArrayList<>()).add(channel);
        player.sendMessage("§aJoined the channel: " + channelName);
    }

    // Create Channel Method (Supports non-proximity and proximity)
    public void createChannel(String channelName, boolean isProximity, double proximityRadius, String password, Player player) {
        if (player.hasPermission("simplevoicegeyser.nonprox") || isProximity) {
            VoicechatServerApi voicechat = getVoiceChatApi();
            AudioChannel newChannel = voicechat.createChannel(channelName);
            if (isProximity) {
                newChannel.setProximityRadius(proximityRadius);
            }
            if (password != null && !password.isEmpty()) {
                channelPasswords.put(channelName, password);
            }

            player.sendMessage("§aChannel '" + channelName + "' created successfully.");
        } else {
            player.sendMessage("§cYou don't have permission to create a non-proximity channel.");
        }
    }

    // Mute a player in the channel
    public void mutePlayer(UUID playerId, String channelName) {
        mutedPlayers.computeIfAbsent(channelName, k -> new HashSet<>()).add(playerId);
    }

    // Unmute a player in the channel
    public void unmutePlayer(UUID playerId, String channelName) {
        Set<UUID> mutedSet = mutedPlayers.get(channelName);
        if (mutedSet != null) {
            mutedSet.remove(playerId);
        }
    }

    // Get all players in a channel
    public List<Player> getPlayersInChannel(String channelName) {
        VoicechatServerApi voicechat = getVoiceChatApi();
        AudioChannel channel = voicechat.getChannel(channelName);
        if (channel != null) {
            return channel.getPlayers();
        }
        return Collections.emptyList();
    }

    private VoicechatServerApi getVoiceChatApi() {
        return VoicechatServerApi.getInstance(serverHost, serverPort);
    }

    public boolean isPlayerMuted(UUID playerId, String channelName) {
        Set<UUID> mutedSet = mutedPlayers.get(channelName);
        return mutedSet != null && mutedSet.contains(playerId);
    }
}
