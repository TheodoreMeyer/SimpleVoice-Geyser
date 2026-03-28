package io.github.theodoremeyer.simplevoicegeyser.core.commands;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.Sender;

public interface SubCommand<T> {

    String name();

    void execute(Sender sender, T args);

}