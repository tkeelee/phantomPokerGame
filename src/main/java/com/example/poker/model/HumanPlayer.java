package com.example.poker.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 人类玩家类，表示由真实用户控制的玩家
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HumanPlayer extends AbstractPlayer {
    
    /**
     * 使用ID和名称构造人类玩家
     * @param id 玩家ID
     * @param name 玩家名称
     */
    public HumanPlayer(String id, String name) {
        super(id, name);
    }
    
    @Override
    public boolean isRobot() {
        return false;
    }
} 