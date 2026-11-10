package io.github.theodoremeyer.simplevoicegeyser.velocity.geyser;

import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.api.GeyserApi;

/** Geyser-only implementation. This class is loaded only after Geyser is detected. */
public final class PasswordForm {
    private final ProxyPasswordStore passwordStore;

    public PasswordForm(ProxyPasswordStore passwordStore) {
        this.passwordStore = passwordStore;
    }

    public boolean open(Player player) {
        if (!GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            return false;
        }
        GeyserApi.api().sendForm(player.getUniqueId(), CustomForm.builder()
                .title("Set SVG Password")
                .input("Password (between 8-32 characters)", "password")
                .validResultHandler(response -> {
                    String password = response.next();
                    if (password != null && password.length() >= 8 && password.length() <= 32) {
                        passwordStore.setPassword(player.getUniqueId(), player.getUsername(), password);
                        player.sendMessage(net.kyori.adventure.text.Component.text("Password set successfully."));
                    } else {
                        player.sendMessage(net.kyori.adventure.text.Component.text("Password must be between 8 and 32 characters."));
                    }
                }).build());
        return true;
    }
}
