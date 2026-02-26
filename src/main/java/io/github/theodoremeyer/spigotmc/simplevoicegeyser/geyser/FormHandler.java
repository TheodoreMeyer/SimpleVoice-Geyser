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
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
     * @param player the player
     * @return whether it successfully opened
     */
    public boolean openCommand(Player player) {
        Form form = SimpleForm.builder()
                .title("Simple Voice Chat (SVG)")
                .button("Groups")
                .button("Set Password")
                .button("Options")
                .validResultHandler(response -> {
                    switch (response.clickedButtonId()) {
                        case 0 -> groups(player);
                        case 1 -> setPassword(player);
                        case 2 -> options(player);
                    }
                })
                .build();
        GeyserHook.sendForm(player.getUniqueId(), form);
        return true;
    }

    /**
     * Creates a window for the player to easily join and create groups with desired parms
     * @param player
     */
    private void groups(Player player) {
        String title = "Groups";
        Form form;
        if (groupManager.isInGroup(player)) {
            form = SimpleForm.builder()
                    .title(title)
                    .content("Group Name: " + Objects.requireNonNull(groupManager.getJoinedGroupName(player)))
                    .button("Invite")
                    .button("Leave")
                    .validResultHandler(response -> {
                        switch (response.clickedButtonId()) {
                            case 0 -> {
                                // TODO needs to be in a new function
                                // Temporary
                                Form tempform = SimpleForm.builder()
                                        .title("Invite")
                                        .content("Work in progress")
                                        .build();
                                GeyserHook.sendForm(player.getUniqueId(), tempform);
                            }
                            case 1 -> groupManager.leaveGroup(player);
                        }
                    })
                    .build();
        } else {
            form = SimpleForm.builder()
                    .title(title)
                    .button("Join")
                    .button("Create")
                    .validResultHandler(response -> {
                        switch (response.clickedButtonId()) {
                            case 0 -> joinGroup(player);
                            case 1 -> createGroup(player);
                        }
                    })
                    .build();
        }
        GeyserHook.sendForm(player.getUniqueId(), form);
    }

    /**
     * Will allow user to change setting in game instead of the webserver
     * @param player
     */
    private static void options(Player player) {
        // TODO
        // Do stuff like mute and turn up and down volume
        // Player information
        Form form = SimpleForm.builder()
                .title("Options")
                .content("Work in progress")
                .build();
        GeyserHook.sendForm(player.getUniqueId(), form);
    };

    /**
     * Allow a bedrock player to create Groups using forms
     * @param player the player
     */
    private void createGroup(Player player) {

        List<String> groupTypes = List.of("Normal", "Open", "Isolated");

        Form form = CustomForm.builder()
                .title("Create a Group")
                .input("Group Name", "name")
                .input("password", "password")
                .dropdown("Group Type", groupTypes, 0)
                .toggle("Persistent")
                .validResultHandler((s, e) -> {
                    String gName = e.next();
                    String pswd = e.next();
                    int typeIndex = e.next();
                    Group.Type[] groupArray = {
                            Group.Type.NORMAL,
                            Group.Type.OPEN,
                            Group.Type.ISOLATED
                    };
                    boolean persistent = e.next();

                    groupManager.createGroup(player, gName, pswd, groupArray[typeIndex], persistent, false);
                })
                .build();

        GeyserHook.sendForm(player.getUniqueId(), form);
    }

    /**
     * Allow a bedrock player to join Groups using forms
     * @param player the player
     */
    private void joinGroup(Player player) {

        DropdownComponent.@NonNull Builder dropdown = DropdownComponent.builder("Group Name");
        for (String string : SVGPlugin.getGroupManager().getGroupNames()) {
            dropdown.option(string);
        }

        Form form = CustomForm.builder()
                .title("Join Group")
                .dropdown(dropdown)
                .input("password", "password")
                .validResultHandler(response -> {
                    int dropdownIndex = response.next();
                    String gName = groupManager.getGroupNames().get(dropdownIndex);
                    String pswd = response.asInput(1);
                    SVGPlugin.getGroupManager().joinGroup(player, gName, pswd);
                })
                .build();

        GeyserHook.sendForm(player.getUniqueId(), form);
    }

    /**
     * Allow a bedrock player to set their password using forms
     * @param p the player
     */
    private void setPassword(Player p) {
        Form form = CustomForm.builder()
                .title("Set SVG Password")
                .input("Password (between 8-32 characters)", "password")
                .validResultHandler((e) -> {
                    String password = e.next();

                    PlayerVcPswd.setPassword(p, password);
                })
                .build();
        GeyserHook.sendForm(p.getUniqueId(), form);
    }
}
