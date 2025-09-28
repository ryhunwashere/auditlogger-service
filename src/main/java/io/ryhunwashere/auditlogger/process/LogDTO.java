package io.ryhunwashere.auditlogger.process;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


public class LogDTO {
    public enum ActionType {
        // Player Actions
        BLOCK_BREAK, BLOCK_PLACE, INTERACT, CHAT, COMMAND,

        // Player Status
        JOIN, QUIT, DEATH, RESPAWN,

        // System / plugin actions
        PLUGIN_ACTION, CONSOLE_COMMAND, WORLD_EVENT
    }

    public enum Source {
        PLAYER, CONSOLE, PLUGIN, SYSTEM, WORLD_EVENT
    }

    // Instant timestamp with multiple accepted key names
    @JsonAlias({"time", "datetime", "date_time", "dateTime", "instant", "instantTimestamp", "instant_timestamp"})
    private Instant timestamp;

    // Player UUID with multiple accepted key names
    @JsonAlias({"player_uuid", "uuid", "playerUuid"})
    private UUID playerUUID;

    // Player name with multiple accepted key names
    @JsonAlias({"player_name", "name"})
    private String playerName;

    @JsonAlias({"action_type"})
    private ActionType actionType;

    @JsonAlias({"action_detail"})
    private Map<String, Object> actionDetail;

    @JsonAlias({"worldName", "world_name"})
    private String world;

    @JsonAlias({"xPos", "x_pos", "xPosition", "x_position"})
    private double x;

    @JsonAlias({"yPos", "y_pos", "yPosition", "y_position"})
    private double y;

    @JsonAlias({"zPos", "z_pos", "zPosition", "z_position"})
    private double z;

    @JsonAlias({"source"})
    private Source source;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID logUUID;

    public LogDTO() {}

    public void generateLogUUID() {
        this.logUUID = UUID.randomUUID();
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public UUID getPlayerUUID() { return playerUUID; }
    public void setPlayerUUID(UUID playerUUID) { this.playerUUID = playerUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public Map<String, Object> getActionDetail() { return actionDetail; }
    public void setActionDetail(Map<String, Object> actionDetail) { this.actionDetail = actionDetail; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public UUID getLogUUID() { return logUUID; }
    public void setLogUUID(UUID logUUID) { this.logUUID = logUUID; }
}
