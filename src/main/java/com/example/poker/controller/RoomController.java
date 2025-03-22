package com.example.poker.controller;

import com.example.poker.model.GameRoom;
import com.example.poker.model.GameState;
import com.example.poker.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 添加机器人
     * @param roomId 房间ID
     * @param count 机器人数量
     * @param difficulty 难度级别
     * @return 操作结果
     */
    @PostMapping("/{roomId}/robots")
    public ResponseEntity<?> addRobots(@PathVariable String roomId, 
                                       @RequestParam(defaultValue = "1") int count,
                                       @RequestParam(defaultValue = "MEDIUM") String difficulty) {
        try {
            GameRoom room = gameService.getRoom(roomId);
            
            // 检查用户是否是房主
            String currentPlayerId = getUserId();
            if (!room.getHostId().equals(currentPlayerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "只有房主可以添加机器人"));
            }
            
            // 添加机器人
            room.addRobots(count, difficulty);
            
            // 更新房间和状态中的机器人信息
            gameService.updateRobotInfo(roomId);
            
            // 获取更新后的游戏状态
            GameState state = gameService.getGameState(roomId);
            
            // 广播更新
            messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
            messagingTemplate.convertAndSend("/topic/game-state/" + roomId, state);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功添加" + count + "个机器人",
                "robotCount", room.getRobotCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 移除机器人
     * @param roomId 房间ID
     * @return 操作结果
     */
    @DeleteMapping("/{roomId}/robots")
    public ResponseEntity<?> removeRobots(@PathVariable String roomId) {
        try {
            GameRoom room = gameService.getRoom(roomId);
            
            // 检查用户是否是房主
            String currentPlayerId = getUserId();
            if (!room.getHostId().equals(currentPlayerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "只有房主可以移除机器人"));
            }
            
            // 移除机器人
            int removedCount = room.getRobotCount();
            room.removeAllRobots();
            
            // 更新房间和状态中的机器人信息
            gameService.updateRobotInfo(roomId);
            
            // 获取更新后的游戏状态
            GameState state = gameService.getGameState(roomId);
            
            // 广播更新
            messagingTemplate.convertAndSend("/topic/room/" + roomId, room);
            messagingTemplate.convertAndSend("/topic/game-state/" + roomId, state);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功移除" + removedCount + "个机器人",
                "robotCount", 0
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 辅助方法，获取当前用户ID
    private String getUserId() {
        // 通过会话或认证获取当前用户ID
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
} 