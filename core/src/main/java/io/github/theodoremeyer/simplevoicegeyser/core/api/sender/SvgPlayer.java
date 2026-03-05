package io.github.theodoremeyer.simplevoicegeyser.core.api.sender;

import java.util.UUID;

public abstract class SvgPlayer extends Sender {

    public abstract UUID getUniqueId();

    public abstract String getName();

    public abstract boolean hasPermission(String permission);

    public abstract void chat(String message);
}
