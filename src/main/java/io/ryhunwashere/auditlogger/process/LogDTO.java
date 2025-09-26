package io.ryhunwashere.auditlogger.process;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


public class LogDTO {
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
	
	private Instant instantTimestamp;
	private UUID playerUUID;
	private String playerName;
	private ActionType actionType;
	private Map<String, Object> actionDetail;
	private String world;
	private double x;
	private double y;
	private double z;
	private Source source;
	
	public LogDTO(Instant instantTimestamp, UUID playerUUID, String playerName, ActionType actionType,
			Map<String, Object> actionDetail, String world, double x, double y, double z, Source source) {
		this.instantTimestamp = instantTimestamp;
		this.playerUUID = playerUUID;
		this.playerName = playerName;
		this.actionType = actionType;
		this.actionDetail = actionDetail;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.source = source;
	}

	public Instant getInstantTimestamp() {
		return instantTimestamp;
	}

	public UUID getPlayerUUID() {
		return playerUUID;
	}

	public String getPlayerName() {
		return playerName;
	}

	public ActionType getActionType() {
		return actionType;
	}

	public Map<String, Object> getActionDetail() {
		return actionDetail;
	}

	public String getWorld() {
		return world;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public Source getSource() {
		return source;
	}
	
	
}
