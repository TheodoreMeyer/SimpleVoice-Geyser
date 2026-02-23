package io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.GroupManager;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.PlayerVcPswd;
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

public class FormHandler {

    public static boolean openCommand(Player p){
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
                                GroupManager.leaveGroup(p);
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

    private static void createGroup(Player p) {

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
                    String type = e.next();
                    boolean persistent = "true".equalsIgnoreCase(e.next());

                    SvgCommand.createGroup(p, gName, pswd, type, persistent);
                })
                .build();

        GeyserHook.sendForm(p.getUniqueId(), form);
    }

    private static void JoinGroup(Player p) {

        DropdownComponent.@NonNull Builder dropdown = DropdownComponent.builder("Group Name");
        for (String string : GroupManager.getGroupNames()) {
            dropdown.option(string);
        }

        Form form = CustomForm.builder()
                .title("Join Group")
                .dropdown(dropdown)
                .input("password", "pswd")
                .validResultHandler((s, e) -> {
                    String gName = e.next();
                    String pswd = e.next();
                    GroupManager.joinGroup(p, gName, pswd);
                })
                .build();

        GeyserHook.sendForm(p.getUniqueId(), form);
    }

    private static void setPassword(Player p) {
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
