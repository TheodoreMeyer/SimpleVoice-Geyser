package io.github.theodoremeyer.simplevoicegeyser.core.commands;

public interface SubCommand {

    String name();

    boolean execute(CommandArgs args);

}