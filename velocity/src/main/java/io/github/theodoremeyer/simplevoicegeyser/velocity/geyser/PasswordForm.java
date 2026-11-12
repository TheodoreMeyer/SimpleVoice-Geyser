package io.github.theodoremeyer.simplevoicegeyser.velocity.geyser;

import com.velocitypowered.api.proxy.Player;
import io.github.theodoremeyer.simplevoicegeyser.velocity.impl.data.ProxyPasswordStore;
import net.kyori.adventure.text.Component;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/** Geyser-only implementation. This class is loaded only after Geyser is detected. */
public final class PasswordForm {
    private final ProxyPasswordStore passwordStore;
    private final Logger logger;

    public PasswordForm(ProxyPasswordStore passwordStore, Logger logger) {
        this.passwordStore = passwordStore;
        this.logger = logger;
    }

    public boolean open(Player player) {
        UUID uuid = player.getUniqueId();
        if (!GeyserApi.api().isBedrockPlayer(uuid)) {
            return false;
        }

        // Geyser and Floodgate both bundle Cumulus and Velocity takes the first loader that has a class,
        // so Cumulus types come from Geyser's loader to avoid a LinkageError in GeyserApi.sendForm.
        try {
            ClassLoader geyser = GeyserApi.class.getClassLoader();
            Class<?> formType = geyser.loadClass("org.geysermc.cumulus.form.Form");
            Class<?> builderType = geyser.loadClass("org.geysermc.cumulus.form.CustomForm$Builder");
            Method next = geyser.loadClass("org.geysermc.cumulus.response.CustomFormResponse").getMethod("next");

            Consumer<Object> onSubmit = response -> {
                try {
                    submit(player, next.invoke(response));
                } catch (ReflectiveOperationException e) {
                    logger.error("Failed to read the Bedrock password form response", e);
                }
            };

            Object builder = geyser.loadClass("org.geysermc.cumulus.form.CustomForm").getMethod("builder").invoke(null);
            builderType.getMethod("title", String.class).invoke(builder, "Set SVG Password");
            builderType.getMethod("input", String.class, String.class)
                    .invoke(builder, "Password (between 8-32 characters)", "password");
            builderType.getMethod("validResultHandler", Consumer.class).invoke(builder, onSubmit);
            Object form = builderType.getMethod("build").invoke(builder);

            GeyserApi.class.getMethod("sendForm", UUID.class, formType).invoke(GeyserApi.api(), uuid, form);
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to open the Bedrock password form", e);
            return false;
        }
        return true;
    }

    private void submit(Player player, Object password) {
        if (password instanceof String value && value.length() >= 8 && value.length() <= 32) {
            passwordStore.setPassword(player.getUniqueId(), player.getUsername(), value);
            player.sendMessage(Component.text("Password set successfully."));
        } else {
            player.sendMessage(Component.text("Password must be between 8 and 32 characters."));
        }
    }
}
