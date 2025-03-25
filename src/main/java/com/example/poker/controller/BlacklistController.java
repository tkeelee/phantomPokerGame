package com.example.poker.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BlacklistController {
    private List<Map<String, Object>> blacklist = new ArrayList<>();

    @PostMapping("/addPlayerToBlacklist")
    public void addToBlacklist(@RequestParam String playerId) {
        Map<String, Object> player = new HashMap<>();
        player.put("id", playerId);
        player.put("nickname", "玩家" + playerId);
        player.put("addedTime", new Date());
        blacklist.add(player);
    }

    @PostMapping("/removePlayerFromBlacklist")
    public void removeFromBlacklist(@RequestParam String playerId) {
        blacklist.removeIf(p -> p.get("id").equals(playerId));
    }

    @GetMapping("/getBlacklistPlayers")
    public List<Map<String, Object>> getBlacklist() {
        return blacklist;
    }
}