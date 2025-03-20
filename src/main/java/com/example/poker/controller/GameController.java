package com.example.poker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.poker.model.GameState;
import com.example.poker.model.PlayerAction;
import com.example.poker.service.GameService;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.example.poker.model.Card;
import com.example.poker.model.GameMessage;
import com.example.poker.model.CardDeck;

@Controller
public class GameController {

    @Autowired
    private GameService gameService;

    @Autowired
    private CardDeck cardDeck;

    @MessageMapping("/game/action")
    @SendTo("/topic/game-state")
    public GameMessage handleAction(GameMessage action) {
        switch(action.getActionType()) {
            case "PLAY":
                return handlePlayCard(action.getPlayerId(), action.getClaim(), action.getCards());
            case "CHALLENGE":
                return handleChallenge(action.getPlayerId(), action.getTargetPlayerId());
            case "FOLLOW":
                return handleFollow(action.getPlayerId(), action.getCards());
            default:
                return new GameMessage("ERROR", "system", "Invalid action");
        }
    }

    private GameMessage handlePlayCard(String playerId, String claim, List<String> cards) {
        // 解析前端卡牌数据结构
        List<Card> parsedCards = cards.stream()
            .map(card -> new Card(
                card.split(",")[0],   // shape (suit)
                card.split(",")[1],  // value (rank)
                "",
                card.split(",")[2]   // color
            ))
            .collect(Collectors.toList());

        // 验证声明牌型格式（数量/点数）
        if(!gameService.validateClaimFormat(claim, parsedCards.size())) {
            return new GameMessage("ERROR", playerId, "声明格式不合法");
        }

        // 验证实际卡牌组合
        if(!gameService.validateCardCombination(parsedCards)) {
            return new GameMessage("ERROR", playerId, "卡牌组合不合法");
        }

        gameService.transferCardDeck(playerId, parsedCards);
        return new GameMessage("PLAY", 
            gameService.getCurrentPlayer(),
            playerId,
            claim,
            parsedCards.stream()
                .map(Card::toString)
                .collect(Collectors.toList()),
            gameService.getNextPlayer() // 添加下个玩家指示
        );
    }

    public GameMessage handleChallenge(String challengerId, String targetPlayerId) {
        // 验证目标玩家最后声明的牌型
        boolean isValid = gameService.validateLastClaim(targetPlayerId);
        
        // 处理挑战结果并更新游戏状态
        gameService.handleChallengeResult(challengerId, targetPlayerId, isValid);
        
        return new GameMessage("CHALLENGE", challengerId, isValid, gameService.getNextPlayer());
    }

    public GameMessage handleFollow(String followerId, List<String> cards) {
        // 验证跟牌卡牌合法性
        List<Card> parsedCards = parseCardList(cards);
        if(!gameService.validateCardCombination(parsedCards)) {
            return new GameMessage("ERROR", followerId, "跟牌卡牌不合法");
        }
        
        // 更新卡牌堆和玩家手牌
        cardDeck.removeCards(followerId, cards);
        gameService.transferCardDeck(followerId, parsedCards);
        
        return new GameMessage("FOLLOW", followerId, cards, gameService.getNextPlayer());
    }

    private List<Card> parseCardList(List<String> cards) {
        return cards.stream()
            .map(card -> new Card(
                card.split(",")[0],
                card.split(",")[1],
                "",
                card.split(",")[2]
            ))
            .collect(Collectors.toList());
    }

    @MessageMapping("/game/init")
    @SendTo("/topic/game-state")
    public GameMessage handleInit(String playerId) {
        String status = gameService.initializeGame(playerId);
        return new GameMessage("INIT", playerId, status);
    }

    private boolean processChallenge(String playerId) {
        // 验证玩家最后声明的牌型是否真实
        return cardDeck.validateLastClaim(playerId);
    }
}