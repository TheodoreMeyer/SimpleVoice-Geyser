package io.github.theodoremeyer.simplevoicegeyser.core.commands;

/**
 * represents a sub command
 */
public interface SubCommand {

    /**
     * Sub Command name
     * @return name
     */
    String name();

    /**
     * Execute the command
     * @param args args to execute with
     * @return success
     */
    boolean execute(CommandArgs args);

}