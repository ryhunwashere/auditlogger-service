package io.ryhunwashere.auditlogger.process;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

enum ActionType {
    // Player Actions
    BLOCK_BREAK, BLOCK_PLACE, INTERACT, CHAT, COMMAND,

    // Player Status
    JOIN, QUIT, DEATH, RESPAWN,

    // System / plugin actions
    PLUGIN_ACTION, CONSOLE_COMMAND, WORLD_EVENT
}

enum Source {
    PLAYER, CONSOLE, PLUGIN, SYSTEM, WORLD_EVENT
}

public record LogDTO(
        Timestamp timestamp,
        UUID playerUUID,
        String playerName,
        ActionType actionType,
        Map<String, Object> actionDetail,
        String world,
        double x,
        double y,
        double z,
        Source source
) {
}
