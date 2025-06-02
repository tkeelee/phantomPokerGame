package com.example.poker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * 玩家类，为了保持与现有代码的兼容性
 * 实际上是HumanPlayer的别名
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Player extends AbstractPlayer {
    
    /**
     * 使用ID和名称构造玩家
     * @param id 玩家ID
     * @param name 玩家名称
     */
    public Player(String id, String name) {
        super(id, name);
    }
    
    /**
     * 创建一个Builder实例
     * @return PlayerBuilder实例
     */
    public static PlayerBuilder builder() {
        return new PlayerBuilder();
    }
    
    /**
     * 玩家构建器类，保持与原有代码的兼容性
     */
    public static class PlayerBuilder {
        private String id;
        private String name;
        private List<Card> hand = new ArrayList<>();
        private int score;
        private boolean active = true;
        private String status;
        private String roomId;
        private Instant lastActiveTime;
        private boolean ready = false;
        private boolean host = false;
        
        public PlayerBuilder id(String id) {
            this.id = id;
            return this;
        }
        
        public PlayerBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        public PlayerBuilder hand(List<Card> hand) {
            this.hand = hand;
            return this;
        }
        
        public PlayerBuilder score(int score) {
            this.score = score;
            return this;
        }
        
        public PlayerBuilder active(boolean active) {
            this.active = active;
            return this;
        }
        
        public PlayerBuilder status(String status) {
            this.status = status;
            return this;
        }
        
        public PlayerBuilder roomId(String roomId) {
            this.roomId = roomId;
            return this;
        }
        
        public PlayerBuilder lastActiveTime(Instant lastActiveTime) {
            this.lastActiveTime = lastActiveTime;
            return this;
        }
        
        public PlayerBuilder ready(boolean ready) {
            this.ready = ready;
            return this;
        }
        
        public PlayerBuilder host(boolean host) {
            this.host = host;
            return this;
        }
        
        public Player build() {
            Player player = new Player();
            player.setId(id);
            player.setName(name);
            player.setHand(hand);
            player.setScore(score);
            player.setActive(active);
            player.setStatus(status);
            player.setRoomId(roomId);
            player.setLastActiveTime(lastActiveTime);
            player.setReady(ready);
            player.setHost(host);
            return player;
        }
    }
}