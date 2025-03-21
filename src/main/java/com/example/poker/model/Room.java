package com.example.poker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏房间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    private String id;
    private String name;
    private String hostId;
    private int maxPlayers;
    private String status;

    @Builder.Default
    private List<String> players = new ArrayList<>();
    
    @Builder.Default
    private List<String> readyPlayers = new ArrayList<>();
    
    @Builder.Default
    private Map<String, List<Card>> playerHands = new HashMap<>();
    
    public int getPlayerCount() {
        return players.size();
    }
    
    public boolean addPlayer(String playerId) {
        if (players.size() >= maxPlayers) {
            return false;
        }
        
        if (!players.contains(playerId)) {
            players.add(playerId);
            return true;
        }
        return false;
    }
    
    public boolean removePlayer(String playerId) {
        boolean removed = players.remove(playerId);
        readyPlayers.remove(playerId);
        playerHands.remove(playerId);
        return removed;
    }
    
    public boolean isReady(String playerId) {
        return readyPlayers.contains(playerId);
    }
    
    public void setReady(String playerId, boolean ready) {
        if (ready) {
            if (!readyPlayers.contains(playerId)) {
                readyPlayers.add(playerId);
            }
        } else {
            readyPlayers.remove(playerId);
        }
    }
    
    public boolean isAllReady() {
        return players.size() >= 2 && readyPlayers.size() == players.size();
    }
} 