package com.aigamemaster.model;

import java.util.List;

public class JoinGameResponse {

    private String gameCode;
    private String playerId;
    private String playerName;
    private String hostPlayerId;
    private List<Player> players;

    public JoinGameResponse(
            String gameCode,
            String playerId,
            String playerName,
            String hostPlayerId,
            List<Player> players
    ) {
        this.gameCode = gameCode;
        this.playerId = playerId;
        this.playerName = playerName;
        this.hostPlayerId = hostPlayerId;
        this.players = players;
    }

    public String getGameCode() {
        return gameCode;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
