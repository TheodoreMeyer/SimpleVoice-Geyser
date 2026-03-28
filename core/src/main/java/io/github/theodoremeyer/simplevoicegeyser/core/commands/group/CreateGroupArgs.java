package io.github.theodoremeyer.simplevoicegeyser.core.commands.group;

public record CreateGroupArgs(
        String name,
        String type,
        String password,
        boolean persistent
) {}