package com.brad.pms.ai.command;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PmsCommandRegistry {

    private final Map<CommandName, PmsCommand> commands;

    public PmsCommandRegistry(List<PmsCommand> commands) {
        EnumMap<CommandName, PmsCommand> indexed = new EnumMap<>(CommandName.class);
        for (PmsCommand command : commands) {
            if (command == null || command.name() == null) {
                throw new IllegalArgumentException("Command and command name are required");
            }
            PmsCommand previous = indexed.put(command.name(), command);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate command: " + command.name().code());
            }
        }
        this.commands = Map.copyOf(indexed);
    }

    public PmsCommand require(CommandName name) {
        if (name == null) throw new IllegalArgumentException("command is required");
        PmsCommand command = commands.get(name);
        if (command == null) throw new IllegalArgumentException("Unsupported command: " + name.code());
        return command;
    }

    public Set<CommandName> list() {
        return commands.keySet();
    }
}
