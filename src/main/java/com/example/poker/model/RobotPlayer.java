package com.example.poker.model;

import java.util.*;

public class RobotPlayer extends Player {
    private static final Random random = new Random();
    private String difficulty; // EASY, MEDIUM, HARD
    private boolean isRobot = true;
    private int handCount; // 记录手牌数量
    private Map<String, Integer> cardCountMap; // 记录每种牌的数量

    public RobotPlayer(String id, String name, String difficulty) {
        super(id, name);
        this.difficulty = difficulty;
        this.cardCountMap = new HashMap<>();
        updateCardCount();
    }

    public boolean isRobot() {
        return isRobot;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * 更新手牌统计信息
     */
    private void updateCardCount() {
        cardCountMap.clear();
        List<Card> hand = getHand();
        handCount = hand.size();
        
        for (Card card : hand) {
            String rank = card.getRank();
            cardCountMap.merge(rank, 1, Integer::sum);
        }
    }

    /**
     * 机器人决定是否质疑上一个玩家
     * @param lastClaim 上一个玩家的声明
     * @param currentPile 当前牌堆
     * @return 是否质疑
     */
    public boolean decideToChallenge(String lastClaim, List<Card> currentPile) {
        if (lastClaim == null || currentPile.isEmpty()) {
            return false;
        }

        // 解析上一个声明
        String[] parts = lastClaim.split(" ");
        int claimedCount = Integer.parseInt(parts[0]);
        String claimedValue = parts[1];

        // 根据难度级别调整基础质疑概率
        double challengeProbability = switch (difficulty) {
            case "EASY" -> 0.2;
            case "MEDIUM" -> 0.4;
            case "HARD" -> 0.6;
            default -> 0.3;
        };

        // 计算手牌中相同点数的牌的数量
        int sameValueCount = cardCountMap.getOrDefault(claimedValue, 0);

        // 根据手牌情况调整质疑概率
        if (handCount <= 3) {
            // 手牌很少时，更倾向于质疑
            challengeProbability += 0.2;
        }

        // 如果声明的数量看起来不合理，增加质疑概率
        if (claimedCount > 4 || (claimedCount + sameValueCount > 4)) {
            challengeProbability += 0.3;
        }

        // 在困难模式下，根据历史记录调整概率
        if (difficulty.equals("HARD")) {
            // 如果手牌中完全没有这种牌，增加质疑概率
            if (sameValueCount == 0) {
                challengeProbability += 0.2;
            }
            // 如果手牌中这种牌的数量接近4张，增加质疑概率
            if (sameValueCount >= 3) {
                challengeProbability += 0.15;
            }
        }

        return random.nextDouble() < challengeProbability;
    }

    /**
     * 机器人选择要打出的牌
     * @param lastClaim 上一个玩家的声明
     * @return 选择的牌列表
     */
    public List<Card> selectCardsToPlay(String lastClaim) {
        List<Card> selectedCards = new ArrayList<>();
        List<Card> hand = getHand();
        
        if (lastClaim == null || hand.isEmpty()) {
            // 如果是新的一轮，选择最优的牌
            String bestValue = findBestValueToPlay();
            List<Card> sameValueCards = hand.stream()
                    .filter(card -> card.getRank().equals(bestValue))
                    .toList();
            
            // 根据难度决定出牌数量
            int cardsToSelect = switch (difficulty) {
                case "EASY" -> Math.min(1 + random.nextInt(2), sameValueCards.size());
                case "MEDIUM" -> Math.min(1 + random.nextInt(3), sameValueCards.size());
                case "HARD" -> Math.min(2 + random.nextInt(2), sameValueCards.size());
                default -> Math.min(1 + random.nextInt(2), sameValueCards.size());
            };
            
            for (int i = 0; i < cardsToSelect; i++) {
                selectedCards.add(sameValueCards.get(i));
            }
        } else {
            // 解析上一个声明
            String[] parts = lastClaim.split(" ");
            String requiredValue = parts[1];
            
            // 找出所有匹配的牌
            List<Card> matchingCards = hand.stream()
                    .filter(card -> card.getRank().equals(requiredValue))
                    .toList();
            
            if (!matchingCards.isEmpty()) {
                // 如果有匹配的牌，根据难度决定出牌数量
                int cardsToSelect = switch (difficulty) {
                    case "EASY" -> Math.min(1 + random.nextInt(2), matchingCards.size());
                    case "MEDIUM" -> Math.min(1 + random.nextInt(3), matchingCards.size());
                    case "HARD" -> Math.min(2 + random.nextInt(2), matchingCards.size());
                    default -> Math.min(1 + random.nextInt(2), matchingCards.size());
                };
                
                for (int i = 0; i < cardsToSelect; i++) {
                    selectedCards.add(matchingCards.get(i));
                }
            } else {
                // 如果没有匹配的牌，选择最优的牌来说谎
                String bestValue = findBestValueToLie(requiredValue);
                List<Card> cardsToLie = hand.stream()
                        .filter(card -> card.getRank().equals(bestValue))
                        .toList();
                
                int cardsToSelect = Math.min(1 + random.nextInt(2), cardsToLie.size());
                for (int i = 0; i < cardsToSelect; i++) {
                    selectedCards.add(cardsToLie.get(i));
                }
            }
        }
        
        return selectedCards;
    }

    /**
     * 找到最优的牌值来出牌
     * @return 最优的牌值
     */
    private String findBestValueToPlay() {
        String bestValue = null;
        int maxCount = 0;
        
        for (Map.Entry<String, Integer> entry : cardCountMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestValue = entry.getKey();
            }
        }
        
        return bestValue != null ? bestValue : getHand().get(0).getRank();
    }

    /**
     * 找到最优的牌值来说谎
     * @param requiredValue 需要声明的牌值
     * @return 最优的牌值
     */
    private String findBestValueToLie(String requiredValue) {
        String bestValue = null;
        int maxCount = 0;
        
        for (Map.Entry<String, Integer> entry : cardCountMap.entrySet()) {
            // 选择数量最多的牌来说谎
            if (entry.getValue() > maxCount && !entry.getKey().equals(requiredValue)) {
                maxCount = entry.getValue();
                bestValue = entry.getKey();
            }
        }
        
        return bestValue != null ? bestValue : getHand().get(0).getRank();
    }

    /**
     * 机器人生成声明
     * @param selectedCards 选择的牌
     * @param lastClaim 上一个玩家的声明
     * @return 声明内容
     */
    public String generateClaim(List<Card> selectedCards, String lastClaim) {
        if (selectedCards.isEmpty()) {
            return null;
        }

        String valueToClaimStr;
        if (lastClaim != null) {
            // 如果不是新的一轮，必须声明相同的点数
            String[] parts = lastClaim.split(" ");
            valueToClaimStr = parts[1];
        } else {
            // 如果是新的一轮，根据难度选择声明策略
            if (difficulty.equals("HARD")) {
                // 困难模式下，70%的概率说实话
                if (random.nextDouble() < 0.7) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    // 30%的概率说谎，选择手牌中数量较多的牌
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            } else if (difficulty.equals("MEDIUM")) {
                // 中等模式下，50%的概率说实话
                if (random.nextDouble() < 0.5) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            } else {
                // 简单模式下，30%的概率说实话
                if (random.nextDouble() < 0.3) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            }
        }

        return selectedCards.size() + " " + valueToClaimStr;
    }

    @Override
    public void setHand(List<Card> hand) {
        super.setHand(hand);
        updateCardCount();
    }
} 