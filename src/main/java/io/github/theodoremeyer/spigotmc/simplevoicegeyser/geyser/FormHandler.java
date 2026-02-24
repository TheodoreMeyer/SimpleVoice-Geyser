package io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.GroupManager;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.PlayerVcPswd;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.SVGPlugin;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.SvgCommand;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.cumulus.component.DropdownComponent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.geysermc.geyser.api.GeyserApi;

import java.util.ArrayList;
import java.util.List;

public final class FormHandler {

    /**
     * Interface to SVC Groups
     */
    private final GroupManager groupManager;

    /**
     * Entry to set up Cumulus forms
     * @param manager the Group Manager
     */
    public FormHandler(GroupManager manager) {
        this.groupManager = manager;
    }

    /**
     * Group System
     * @param p the player
     * @return whether it successfully opened
     */
    public boolean openCommand(Player p){
        List<String> commands = new ArrayList<>();
        commands.add("password");
        commands.add("cgroup");
        commands.add("jgroup");
        commands.add("lgroup");

        Form form = CustomForm.builder()
                .title("Command System")
                .dropdown("Commands", commands, 0)
                .validResultHandler((response) -> {
                    if (response.hasNext()) {
                        String command = response.next();
                        switch (command) {
                            case "password": {
                                setPassword(p);
                            }
                            case "cgroup": {
                                createGroup(p);
                                return;
                            }
                            case "jgroup": {
                                JoinGroup(p);
                                return;
                            }
                            case "lgroup": {
                                SVGPlugin.getGroupManager().leaveGroup(p);
                            }
                            case null, default:
                               p.sendMessage(ChatColor.RED + "Invalid Command");
                        }
                    }
                })
                .build();

        GeyserHook.sendForm(p.getUniqueId(), form);
        return true;
    }

    /**
     * Allow a bedrock player to create Groups using forms
     * @param p the player
     */
    private void createGroup(Player p) {

        List<String> groupTypes = List.of("Open", "Normal", "Isolated");
        List<String> isPersistent = List.of("false", "true");

        Form form = CustomForm.builder()
                .title("Create a Group")
                .input("Group Name", "name")
                .input("password", "pswd")
                .dropdown("Group Type", groupTypes, 0)
                .dropdown("Persistent", isPersistent, 0)
                .validResultHandler((s, e) -> {
                    String gName = e.next();
                    String pswd = e.next();
                    Group.Type type = e.next();
                    boolean persistent = "true".equalsIgnoreCase(e.next());

                    groupManager.createGroup(p, gName, pswd, type, persistent, false);
                })
                .build();

        GeyserHook.sendForm(p.getUniqueId(), form);
    }

    /**
     * Allow a bedrock player to join Groups using forms
     * @param p the player
     */
    private void JoinGroup(Player p) {

        DropdownComponent.@NonNull Builder dropdown = DropdownComponent.builder("Group Name");
        for (String string : SVGPlugin.getGroupManager().getGroupNames()) {
            dropdown.option(string);
        }

        Form form = CustomForm.builder()
                .title("Join Group")
                .dropdown(dropdown)
                .input("password", "pswd")
                .validResultHandler((s, e) -> {
                    String gName = e.next();
                    String pswd = e.next();
                    SVGPlugin.getGroupManager().joinGroup(p, gName, pswd);
                })
                .build();

        GeyserHook.sendForm(p.getUniqueId(), form);
    }

    /**
     * Allow a bedrock player to set their password using forms
     * @param p the player
     */
    private void setPassword(Player p) {
        Form form = CustomForm.builder()
                .title("Set SVG Password")
                .input("Password (between 8-32 characters)", "pswd")
                .validResultHandler((e) -> {
                    String password = e.next();

                    PlayerVcPswd.setPassword(p, password);
                })
                .build();
        GeyserHook.sendForm(p.getUniqueId(), form);
    }
}
